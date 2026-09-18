package com.github.andreyasadchy.xtra.ui.team

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Team
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.collections.collectionName
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.MarkdownText
import com.github.andreyasadchy.xtra.ui.common.PagedListFragment
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.XtraTopBar
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.team.TeamViewModel.Companion.TeamViewModelFactory
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Team page as a full-Compose screen: app bar with a share action, a team header
 * (banner, logo, name, member count, owner and Markdown description) and the
 * shared team-member list from `:core:ui`.
 *
 * The header is static, matching the pinned AppBarLayout this replaced.
 */
class TeamFragment : PagedListFragment(), Scrollable, IntegrityDialog.Listener {

    private companion object {
        /** `android.graphics.Color.LTGRAY`, the colour the XML used over a banner. */
        val BannerText = Color(0xFFCCCCCC)
    }

    private val args: TeamFragmentArgs by navArgs()
    private val viewModel: TeamViewModel by viewModels { TeamViewModelFactory }
    private val composeRefreshSignal = MutableStateFlow(0)
    private val composeScrollTopSignal = MutableStateFlow(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                XtraTheme(themeId = theme) {
                    TeamScreen()
                }
            }
        }
    }

    @Composable
    private fun TeamScreen() {
        val activity = requireActivity() as MainActivity
        val isLoggedIn = !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
        val liftOptOut = !requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)
        val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
        val refreshTick by composeRefreshSignal.collectAsState()
        val scrollTick by composeScrollTopSignal.collectAsState()
        val team by viewModel.team.collectAsState()
        Scaffold(
            topBar = {
                Column {
                    XtraTopBar(
                        // The team name is shown in the header, as the XML did.
                        title = "",
                        isLoggedIn = isLoggedIn,
                        liftOptOut = liftOptOut,
                        onSearch = { findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment()) },
                        onSettings = { activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java)) },
                        onLogin = { onLoginClick(isLoggedIn, activity) },
                        up = { findNavController().navigateUp() },
                        extraOverflow = listOf(stringResource(R.string.share) to { shareTeam() }),
                    )
                    TeamHeader(team)
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            TeamMembersTab(
                flow = viewModel.flow,
                bottomInset = xtraBottomInset(activity),
                portrait = portrait,
                refreshTick = refreshTick,
                scrollTick = scrollTick,
                modifier = Modifier.padding(padding).fillMaxSize().nestedScroll(rememberNestedScrollInteropConnection()),
                onClick = { stream, live ->
                    if (live) {
                        activity.startStream(stream)
                    } else {
                        openChannel(stream)
                    }
                },
                onChannelClick = ::openChannel,
                onGameClick = ::openGame,
                onTagClick = ::openTag,
                onIntegrityFailed = { activity.getNewIntegrityToken("refresh", childFragmentManager) },
            )
        }
    }

    /**
     * Team banner block. With a banner behind it the texts switch to light grey
     * with a shadow, mirroring the XML's `#8C000000` banner tint.
     */
    @Composable
    private fun TeamHeader(team: Team?) {
        val context = LocalContext.current
        val banner = team?.bannerUrl
        val onBanner = banner != null
        val textColor = if (onBanner) BannerText else MaterialTheme.colorScheme.onSurface
        val detailColor = if (onBanner) BannerText else MaterialTheme.colorScheme.onSurfaceVariant
        val shadow = if (onBanner) Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 4f) else null
        val memberCount = team?.memberCount
        val owner = team?.let {
            if (!it.ownerName.isNullOrBlank() || !it.ownerLogin.isNullOrBlank()) {
                context.getString(
                    R.string.owner,
                    if (it.ownerLogin != null && !it.ownerLogin.equals(it.ownerName, true)) {
                        context.collectionName(it.ownerName, it.ownerLogin)
                    } else {
                        it.ownerName
                    }
                )
            } else null
        }
        Column(Modifier.fillMaxWidth()) {
            Box {
                if (banner != null) {
                    XtraAsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(Modifier.matchParentSize().background(Color(0x8C000000)))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val logo = team?.logoUrl
                    if (logo != null) {
                        XtraAsyncImage(
                            model = logo,
                            contentDescription = team.displayName,
                            circleCrop = context.prefs().getBoolean(C.UI_ROUND_USER_IMAGE, true),
                            modifier = Modifier.size(100.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        team?.displayName?.takeIf { it.isNotBlank() }?.let {
                            Text(text = it, style = MaterialTheme.typography.titleMedium.copy(color = textColor, shadow = shadow))
                        }
                        if (memberCount != null) {
                            Text(
                                text = context.resources.getQuantityString(
                                    R.plurals.members,
                                    memberCount,
                                    TwitchApiHelper.formatCount(memberCount, context.prefs().getBoolean(C.UI_TRUNCATE_VIEW_COUNT, true))
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(color = detailColor, shadow = shadow),
                            )
                        }
                        if (owner != null) {
                            Text(text = owner, style = MaterialTheme.typography.bodyMedium.copy(color = detailColor, shadow = shadow))
                        }
                    }
                }
            }
            team?.description?.takeIf { it.isNotBlank() }?.let {
                MarkdownText(
                    markdown = it,
                    collapsedMaxLines = 3,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(7.dp),
                )
            }
        }
    }

    private fun shareTeam() {
        startActivity(
            Intent.createChooser(
                Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "https://twitch.tv/team/${args.teamName}")
                    args.teamName?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    type = "text/plain"
                },
                null,
            )
        )
    }

    private fun onLoginClick(isLoggedIn: Boolean, activity: MainActivity) {
        if (isLoggedIn) {
            activity.getAlertDialogBuilder().apply {
                setTitle(getString(R.string.logout_title))
                requireContext().tokenPrefs().getString(C.USERNAME, null)?.let { setMessage(getString(R.string.logout_msg, it)) }
                setNegativeButton(getString(R.string.no), null)
                setPositiveButton(getString(R.string.yes)) { _, _ -> activity.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java)) }
            }.show()
        } else {
            activity.loginResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
        }
    }

    override fun initialize() {
        loadTeam()
    }

    private fun loadTeam() {
        viewModel.loadTeamInfo(
            teamName = args.teamName,
            gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
            enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
    }

    private fun openChannel(stream: Stream) {
        findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = stream.channelId,
                channelLogin = stream.channelLogin,
                channelName = stream.channelName,
                channelImage = stream.channelImage,
                streamId = stream.id,
            )
        )
    }

    private fun openGame(stream: Stream) {
        findNavController().navigate(
            if (requireContext().prefs().getBoolean(C.UI_GAME_PAGER, true)) {
                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                    gameId = stream.gameId,
                    gameSlug = stream.gameSlug,
                    gameName = stream.gameName,
                )
            } else {
                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                    gameId = stream.gameId,
                    gameSlug = stream.gameSlug,
                    gameName = stream.gameName,
                )
            }
        )
    }

    private fun openTag(tag: String) {
        findNavController().navigate(
            TopStreamsFragmentDirections.actionGlobalTopFragment(
                tags = arrayOf(tag)
            )
        )
    }

    override fun scrollToTop() {
        composeScrollTopSignal.value++
    }

    override fun onNetworkRestored() {
        loadTeam()
        composeRefreshSignal.value++
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            "refresh" -> {
                loadTeam()
                composeRefreshSignal.value++
            }
        }
    }
}

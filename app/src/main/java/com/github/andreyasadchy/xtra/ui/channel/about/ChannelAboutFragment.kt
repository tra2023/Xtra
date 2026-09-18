package com.github.andreyasadchy.xtra.ui.channel.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.ChannelPanel
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.channel.about.ChannelAboutViewModel.Companion.ChannelAboutViewModelFactory
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.MarkdownText
import com.github.andreyasadchy.xtra.ui.common.xtraBottomInset
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.team.TeamFragmentDirections
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.rememberThemeId
import kotlinx.coroutines.launch

/**
 * Channel "About" tab as Compose: one scrolling column holding the description,
 * social links, team line, old username and the channel panels (whose
 * descriptions are Markdown). The panels used to be a RecyclerView nested in a
 * `NestedScrollView`; a single [LazyColumn] replaces both.
 */
class ChannelAboutFragment : BaseNetworkFragment(), IntegrityDialog.Listener {

    private val args: ChannelPagerFragmentArgs by navArgs()
    private val viewModel: ChannelAboutViewModel by viewModels { ChannelAboutViewModelFactory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val theme = rememberThemeId()
                XtraTheme(themeId = theme) {
                    AboutScreen()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.integrity.collect {
                    (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager)
                }
            }
        }
    }

    @Composable
    private fun AboutScreen() {
        val activity = requireActivity() as MainActivity
        val description by viewModel.description.collectAsState()
        val socialMedias by viewModel.socialMedias.collectAsState()
        val team by viewModel.team.collectAsState()
        val originalName by viewModel.originalName.collectAsState()
        val panels by viewModel.panels.collectAsState()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = xtraBottomInset(activity)),
        ) {
            description?.takeIf { it.isNotBlank() }?.let { text ->
                item { AboutText(text) }
            }
            socialMedias?.takeIf { it.isNotEmpty() }?.let { list ->
                item { SocialMediaList(list) }
            }
            team?.let { (name, displayName) ->
                displayName?.takeIf { it.isNotBlank() }?.let {
                    item { TeamLine(name, it) }
                }
            }
            originalName?.takeIf { it.isNotBlank() }?.let { name ->
                item { AboutText(stringResource(R.string.old_username, name), bottom = 5.dp) }
            }
            items(panels.orEmpty()) { panel ->
                ChannelPanelItem(panel)
            }
        }
    }

    @Composable
    private fun AboutText(text: String, bottom: Dp = 12.dp) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = bottom),
        )
    }

    /** Each entry is `title (host)`, whole line clickable when it has a URL. */
    @Composable
    private fun SocialMediaList(links: List<Pair<String?, String?>>) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp)) {
            links.forEach { (title, url) ->
                if (!title.isNullOrBlank()) {
                    val host = url?.toUri()?.host?.removePrefix("www.")
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }
                            if (host != null) {
                                append(" ($host)")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 5.dp)
                            .then(if (url != null) Modifier.clickable { openUrl(url) } else Modifier),
                    )
                }
            }
        }
    }

    /** `Team: Name` with only the name clickable, as the old ClickableSpan did. */
    @Composable
    private fun TeamLine(name: String?, displayName: String) {
        val full = stringResource(R.string.team, displayName)
        val index = full.indexOf(displayName)
        val style = MaterialTheme.typography.bodyMedium
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        if (index < 0) {
            AboutText(full, bottom = 5.dp)
        } else {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 5.dp)) {
                Text(
                    text = buildAnnotatedString {
                        append(full.substring(0, index))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(displayName) }
                        append(full.substring(index + displayName.length))
                    },
                    style = style,
                    color = color,
                    modifier = Modifier.then(
                        if (name != null) {
                            Modifier.clickable {
                                findNavController().navigate(TeamFragmentDirections.actionGlobalTeamFragment(teamName = name))
                            }
                        } else Modifier
                    ),
                )
            }
        }
    }

    @Composable
    private fun ChannelPanelItem(panel: ChannelPanel) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            panel.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            panel.imageUrl?.let { image ->
                XtraAsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(0.75f)
                        .padding(bottom = 12.dp)
                        .then(if (panel.linkUrl != null) Modifier.clickable { openUrl(panel.linkUrl) } else Modifier),
                )
            }
            panel.description?.let {
                MarkdownText(
                    markdown = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    private fun openUrl(url: String?) {
        if (url == null) return
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_browser_found, Toast.LENGTH_LONG).show()
        }
    }

    override fun initialize() {
        loadAbout()
    }

    private fun loadAbout() {
        viewModel.loadAbout(
            channelId = args.channelId,
            channelLogin = args.channelLogin,
            gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
            enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
    }

    override fun onNetworkRestored() {
        loadAbout()
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        (parentFragment as? IntegrityDialog.Listener)?.onIntegrityTokenLoaded("refresh")
        when (callback) {
            "refresh" -> {
                loadAbout()
            }
        }
    }
}

package com.github.andreyasadchy.xtra.ui.chat

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.MultiAutoCompleteTextView
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.use
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentChatBinding
import com.github.andreyasadchy.xtra.model.chat.ChatImage
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import com.github.andreyasadchy.xtra.ui.theme.XtraTheme
import com.github.andreyasadchy.xtra.ui.view.AutoCompleteAdapter
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.chat.ChatRenderCache
import com.github.andreyasadchy.xtra.util.chat.ChatRenderOptions
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.reduceDragSensitivity
import com.github.andreyasadchy.xtra.util.rememberThemeId
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.roundToInt

class ChatFragment : BaseNetworkFragment(), MessageClickedDialog.OnButtonClickListener, ReplyClickedDialog.OnButtonClickListener {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels { ChatViewModelFactory }
    private var chatState: ChatState? = null
    private val chatListState = LazyListState()

    private var isChatTouched = false
    private var showChatStatus = false
    private var hasRecentEmotes = false
    private var messagingEnabled = false
    // Set when a message arrives while the list is at the bottom; the Compose side performs the
    // actual scroll once the new row is part of the list, so it does not race the update.
    // The index/size snapshot below is taken when the flag is armed: at scroll time the effect
    // only snaps if the list has not moved up beyond what the arrived messages explain, so a
    // scroll that runs late (after the user flung upward) can never yank them back down.
    private var shouldAutoScroll = false
    private var autoScrollIndex = 0
    private var autoScrollSize = 0
    private val composerState = ChatComposerState()

    private var autoCompleteAdapter: AutoCompleteAdapter<Any>? = null

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            toggleEmoteMenu(false)
        }
    }

    private val messageDialog: MessageClickedDialog?
        get() = childFragmentManager.findFragmentByTag("messageDialog") as? MessageClickedDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
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
        with(binding) {
            if (!requireContext().prefs().getBoolean(C.CHAT_DISABLE, false)) {
                val args = requireArguments()
                val channelId = args.getString(KEY_CHANNEL_ID)
                val channelLogin = args.getString(KEY_CHANNEL_LOGIN)
                val isLive = args.getBoolean(KEY_IS_LIVE)
                val accountLogin = requireContext().tokenPrefs().getString(C.USERNAME, null)
                val isLoggedIn = !accountLogin.isNullOrBlank() &&
                        (!TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                                !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank())
                val chatUrl = args.getString(KEY_CHAT_URL)
                if (isLive || (args.getString(KEY_VIDEO_ID) != null && args.getInt(KEY_START_TIME) != -1) || chatUrl != null) {
                    val enableMessaging = isLive && isLoggedIn
                    val sizeModifier = (requireContext().prefs().getInt(C.CHAT_SIZE_MODIFIER, 100).toFloat() / 100f)
                    val state = ChatState().also { chatState = it }
                    state.messageStyle = buildChatMessageStyle(sizeModifier)
                    state.options = buildChatRenderOptions(chatUrl, enableMessaging, accountLogin)
                    state.replaceMessages(snapshotChatMessages())
                    recyclerView.apply {
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent {
                            val theme = rememberThemeId()
                            XtraTheme(themeId = theme) {
                                val dragged by chatListState.interactionSource.collectIsDraggedAsState()
                                LaunchedEffect(dragged) { isChatTouched = dragged }
                                // The list is reversed (newest at the bottom, index 0), so the user
                                // can scroll down to newer messages while firstVisibleItemIndex > 1.
                                // Use the item index instead of canScrollBackward: the latter also
                                // flips on a few px of scroll offset (image loading, layout jitter),
                                // which would spuriously disable auto-scroll and flash the button.
                                // The > 1 threshold mirrors isAtBottom (index <= 1 counts as at the
                                // bottom): every auto-scrolled message transiently pushes the index
                                // to 1, and showing/hiding the button on each of those would churn
                                // two extra layout passes per message in a busy chat.
                                val canScrollDown by remember { derivedStateOf { chatListState.firstVisibleItemIndex > 1 } }
                                LaunchedEffect(canScrollDown) {
                                    btnDown.isVisible = canScrollDown
                                    if (canScrollDown && showChatStatus && chatStatus.isGone) {
                                        chatStatus.visibility = View.VISIBLE
                                        chatStatus.postDelayed({ chatStatus.visibility = View.GONE }, 5000)
                                    }
                                }
                                // Scroll to the newest row only after it is part of the list.
                                // The interaction is re-checked here, not just when the message
                                // arrived: the user may have grabbed the list in between, and the
                                // pending scroll must never yank them back down mid-gesture.
                                // Skipping while the list is moving also matters: scrollToItem
                                // takes the scroll mutex with uninterruptible priority, so a busy
                                // chat snapping on every message would starve the user's own drag
                                // and pin them at the bottom. Finally, the position is verified:
                                // if the first visible row moved up by more than the messages that
                                // arrived since arming, the user scrolled away and the snap is
                                // dropped. A skipped request simply dies; the next arriving
                                // message re-arms it if still at the bottom.
                                LaunchedEffect(state.messages) {
                                    if (shouldAutoScroll) {
                                        shouldAutoScroll = false
                                        val grown = chatListState.firstVisibleItemIndex - autoScrollIndex
                                        val arrived = (chatState?.messages?.size ?: 0) - autoScrollSize
                                        if (!isChatTouched && !chatListState.isScrollInProgress && grown <= arrived + 1) {
                                            chatListState.scrollToItem(0)
                                        }
                                    }
                                }
                                // Stable row callbacks: recreating them on every message would
                                // invalidate every row's cached formatted text and force the whole
                                // visible list to rebuild its AnnotatedString and emote images.
                                val onMessageClick = remember(state, enableMessaging, channelId) {
                                    { message: ChatMessage ->
                                        state.select(message)
                                        hideKeyboardAndFocus()
                                        showMessageDialog(enableMessaging, channelId)
                                    }
                                }
                                val onReplyClick = remember(state, enableMessaging) {
                                    { message: ChatMessage ->
                                        state.select(message)
                                        hideKeyboardAndFocus()
                                        showReplyDialog(enableMessaging)
                                    }
                                }
                                val onImageClick = remember {
                                    { image: ChatImage ->
                                        hideKeyboardAndFocus()
                                        showImageDialog(image)
                                    }
                                }
                                ChatList(
                                    messages = state.messages,
                                    options = state.options,
                                    generation = state.generation,
                                    style = state.messageStyle,
                                    listState = chatListState,
                                    selectedMessage = state.selectedMessage,
                                    onMessageClick = onMessageClick,
                                    onReplyClick = onReplyClick,
                                    onImageClick = onImageClick,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    btnDown.setOnClickListener {
                        view.post { scrollToBottom() }
                    }
                    if (enableMessaging) {
                        viewModel.loadRecentEmotes()
                        viewLifecycleOwner.lifecycleScope.launch {
                            repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.hasRecentEmotes.collectLatest {
                                    if (it) {
                                        hasRecentEmotes = true
                                    }
                                }
                            }
                        }
                        autoCompleteAdapter = AutoCompleteAdapter(
                            requireContext(),
                            R.layout.auto_complete_emotes_list_item,
                            R.id.name,
                            viewModel.autoCompleteList,
                        ).apply {
                            setNotifyOnChange(false)
                            editText.setAdapter(this)

                            var previousSize = 0
                            editText.setOnFocusChangeListener { _, hasFocus ->
                                if (hasFocus && count != previousSize) {
                                    previousSize = count
                                    notifyDataSetChanged()
                                }
                                setNotifyOnChange(hasFocus)
                            }
                        }
                        editText.addTextChangedListener(onTextChanged = { text, _, _, _ ->
                            val visible = if (ChatInput.shouldShowSend(text?.toString())) View.VISIBLE else View.GONE
                            send.visibility = visible
                            clear.visibility = visible
                        })
                        editText.setTokenizer(SpaceTokenizer())
                        editText.setOnKeyListener(sendOnEnterListener { sendMessage() })
                        clear.setOnClickListener {
                            editText.setText(ChatInput.textAfterDeletingLastWord(editText.text.toString()))
                            editText.setSelection(editText.length())
                        }
                        clear.setOnLongClickListener {
                            editText.text.clear()
                            true
                        }
                        composerState.onSent()
                        showReplyIndicator(false)
                        send.setOnClickListener { sendMessage() }
                        if ((view.parent?.parent?.parent?.parent as? View)?.id == R.id.slidingLayout && !requireContext().prefs().getBoolean(C.KEY_CHAT_BAR_VISIBLE, true)) {
                            messageView.visibility = View.GONE
                        } else {
                            messageView.visibility = View.VISIBLE
                        }
                        viewPager.adapter = object : FragmentStateAdapter(this@ChatFragment) {
                            override fun getItemCount(): Int = 3

                            override fun createFragment(position: Int): Fragment {
                                return EmotesFragment.newInstance(position)
                            }
                        }
                        viewPager.offscreenPageLimit = 2
                        viewPager.reduceDragSensitivity()
                        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                            tab.text = when (position) {
                                0 -> getString(R.string.recent_emotes)
                                1 -> "Twitch"
                                else -> "7TV/BTTV/FFZ"
                            }
                        }.attach()
                        emotes.setOnClickListener {
                            //TODO add animation
                            if (emoteMenu.isGone) {
                                if (!hasRecentEmotes && viewPager.currentItem == 0) {
                                    viewPager.setCurrentItem(1, false)
                                }
                                toggleEmoteMenu(true)
                            } else {
                                toggleEmoteMenu(false)
                            }
                        }
                        messagingEnabled = true
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.roomState.collectLatest { roomState ->
                                if (roomState != null) {
                                    when (roomState.emote) {
                                        "0" -> textEmote.visibility = View.GONE
                                        "1" -> textEmote.visibility = View.VISIBLE
                                    }
                                    val followers = roomState.followers
                                    if (followers != null) {
                                        when (followers) {
                                            "-1" -> textFollowers.visibility = View.GONE
                                            "0" -> {
                                                textFollowers.text = getString(R.string.room_followers)
                                                textFollowers.visibility = View.VISIBLE
                                            }
                                            else -> {
                                                textFollowers.text = getString(
                                                    R.string.room_followers_min,
                                                    TwitchApiHelper.getDurationFromSeconds(requireContext(), (followers.toInt() * 60).toString())
                                                )
                                                textFollowers.visibility = View.VISIBLE
                                            }
                                        }
                                    }
                                    when (roomState.unique) {
                                        "0" -> textUnique.visibility = View.GONE
                                        "1" -> textUnique.visibility = View.VISIBLE
                                    }
                                    if (roomState.slow != null) {
                                        when (roomState.slow) {
                                            "0" -> textSlow.visibility = View.GONE
                                            else -> {
                                                textSlow.text = getString(
                                                    R.string.room_slow,
                                                    TwitchApiHelper.getDurationFromSeconds(requireContext(), roomState.slow)
                                                )
                                                textSlow.visibility = View.VISIBLE
                                            }
                                        }
                                    }
                                    when (roomState.subs) {
                                        "0" -> textSubs.visibility = View.GONE
                                        "1" -> textSubs.visibility = View.VISIBLE
                                    }
                                    if (textEmote.isGone && textFollowers.isGone && textUnique.isGone && textSlow.isGone && textSubs.isGone) {
                                        showChatStatus = false
                                        chatStatus.visibility = View.GONE
                                    } else {
                                        showChatStatus = true
                                        chatStatus.visibility = View.VISIBLE
                                        chatStatus.postDelayed({ chatStatus.visibility = View.GONE }, 5000)
                                    }
                                    viewModel.roomState.value = null
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.reloadMessages.collectLatest {
                                if (it) {
                                    chatState?.apply {
                                        options = buildChatRenderOptions(chatUrl, enableMessaging, accountLogin)
                                        refresh()
                                    }
                                    viewModel.reloadMessages.value = false
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.hideRaid.collectLatest {
                                if (it) {
                                    raidLayout.visibility = View.GONE
                                    viewModel.raidClosed = true
                                    viewModel.hideRaid.value = false
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.raid.collectLatest { raid ->
                                if (raid != null) {
                                    if (!viewModel.raidClosed) {
                                        if (raid.openStream) {
                                            if (requireContext().prefs().getBoolean(C.CHAT_RAIDS_AUTO_SWITCH, true) && parentFragment is PlayerFragment) {
                                                (requireActivity() as? MainActivity)?.startStream(
                                                    Stream(
                                                        channelId = raid.targetId,
                                                        channelLogin = raid.targetLogin,
                                                        channelName = raid.targetName,
                                                        channelImageURL = raid.targetImageURL,
                                                    )
                                                )
                                            }
                                            raidLayout.visibility = View.GONE
                                            viewModel.raidClosed = true
                                        } else {
                                            raidLayout.visibility = View.VISIBLE
                                            raidLayout.setOnClickListener { viewModel.raidClicked.value = raid }
                                            requireContext().imageLoader.enqueue(
                                                ImageRequest.Builder(requireContext()).apply {
                                                    data(raid.targetImage)
                                                    if (requireContext().prefs().getBoolean(C.UI_ROUND_USER_IMAGE, true)) {
                                                        transformations(CircleCropTransformation())
                                                    }
                                                    crossfade(true)
                                                    target(raidImage)
                                                }.build()
                                            )
                                            raidClose.setOnClickListener {
                                                raidLayout.visibility = View.GONE
                                                viewModel.raidClosed = true
                                            }
                                            raidText.text = getString(
                                                R.string.raid_text,
                                                if (raid.targetLogin != null && !raid.targetLogin.equals(raid.targetName, true)) {
                                                    when (requireContext().prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                                                        "0" -> "${raid.targetName}(${raid.targetLogin})"
                                                        "1" -> raid.targetName
                                                        else -> raid.targetLogin
                                                    }
                                                } else {
                                                    raid.targetName
                                                },
                                                raid.viewerCount
                                            )
                                        }
                                    }
                                    viewModel.raid.value = null
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.raidClicked.collectLatest {
                                if (it != null) {
                                    (requireActivity() as? MainActivity)?.startStream(
                                        Stream(
                                            channelId = it.targetId,
                                            channelLogin = it.targetLogin,
                                            channelName = it.targetName,
                                            channelImageURL = it.targetImageURL,
                                        )
                                    )
                                    viewModel.raidClicked.value = null
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.hidePoll.collectLatest {
                                if (it) {
                                    pollLayout.visibility = View.GONE
                                    viewModel.pollSecondsLeft.value = null
                                    viewModel.pollTimer?.cancel()
                                    viewModel.pollClosed = true
                                    viewModel.hidePoll.value = false
                                }
                            }
                        }
                    }
                    pollClose.setOnClickListener {
                        pollLayout.visibility = View.GONE
                        viewModel.pollSecondsLeft.value = null
                        viewModel.pollTimer?.cancel()
                        viewModel.pollClosed = true
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.poll.collectLatest { poll ->
                                if (poll != null) {
                                    if (!viewModel.pollClosed) {
                                        when (poll.status) {
                                            "ACTIVE" -> {
                                                pollLayout.visibility = View.VISIBLE
                                                pollTitle.text = getString(R.string.poll_title, poll.title)
                                                pollChoices.text = poll.choices?.joinToString("\n") { it ->
                                                    getString(
                                                        R.string.poll_choice,
                                                        (((it.totalVotes ?: 0).toLong() * 100.0) / max((poll.totalVotes ?: 0), 1)).roundToInt(),
                                                        it.totalVotes?.let { NumberFormat.getInstance().format(it) },
                                                        it.title
                                                    )
                                                }
                                                pollStatus.visibility = View.VISIBLE
                                            }
                                            "COMPLETED", "TERMINATED" -> {
                                                pollLayout.visibility = View.VISIBLE
                                                pollTitle.text = getString(R.string.poll_title, poll.title)
                                                val winningTotal = poll.choices?.maxOfOrNull { it.totalVotes ?: 0 } ?: 0
                                                pollChoices.text = poll.choices?.joinToString("\n") { it ->
                                                    getString(
                                                        if (winningTotal == it.totalVotes) {
                                                            R.string.poll_choice_winner
                                                        } else {
                                                            R.string.poll_choice
                                                        },
                                                        (((it.totalVotes ?: 0).toLong() * 100.0) / max((poll.totalVotes ?: 0), 1)).roundToInt(),
                                                        it.totalVotes?.let { NumberFormat.getInstance().format(it) },
                                                        it.title
                                                    )
                                                }
                                                pollStatus.visibility = View.GONE
                                                viewModel.pollSecondsLeft.value = null
                                                viewModel.pollTimer?.cancel()
                                                viewModel.startPollTimeout { pollLayout.visibility = View.GONE }
                                            }
                                            else -> {
                                                pollLayout.visibility = View.GONE
                                                viewModel.pollSecondsLeft.value = null
                                                viewModel.pollTimer?.cancel()
                                                viewModel.pollClosed = true
                                            }
                                        }
                                    }
                                    viewModel.poll.value = null
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.pollSecondsLeft.collectLatest {
                                if (it != null) {
                                    pollStatus.text = getString(R.string.remaining_time, DateUtils.formatElapsedTime(it.toLong()))
                                    if (it <= 0) {
                                        viewModel.pollSecondsLeft.value = null
                                    }
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.hidePrediction.collectLatest {
                                if (it) {
                                    predictionLayout.visibility = View.GONE
                                    viewModel.predictionSecondsLeft.value = null
                                    viewModel.predictionTimer?.cancel()
                                    viewModel.predictionClosed = true
                                    viewModel.hidePrediction.value = false
                                }
                            }
                        }
                    }
                    predictionClose.setOnClickListener {
                        predictionLayout.visibility = View.GONE
                        viewModel.predictionSecondsLeft.value = null
                        viewModel.predictionTimer?.cancel()
                        viewModel.predictionClosed = true
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.prediction.collectLatest { prediction ->
                                if (prediction != null) {
                                    if (!viewModel.predictionClosed) {
                                        when (prediction.status) {
                                            "ACTIVE" -> {
                                                predictionLayout.visibility = View.VISIBLE
                                                predictionTitle.text = getString(R.string.prediction_title, prediction.title)
                                                val totalPoints = prediction.outcomes?.sumOf { it.totalPoints?.toLong() ?: 0 } ?: 0
                                                predictionOutcomes.text = prediction.outcomes?.joinToString("\n") { it ->
                                                    getString(
                                                        R.string.prediction_outcome,
                                                        (((it.totalPoints ?: 0).toLong() * 100.0) / max(totalPoints, 1)).roundToInt(),
                                                        it.totalPoints?.let { NumberFormat.getInstance().format(it) },
                                                        it.totalUsers?.let { NumberFormat.getInstance().format(it) },
                                                        it.title
                                                    )
                                                }
                                                predictionStatus.visibility = View.VISIBLE
                                            }
                                            "LOCKED" -> {
                                                predictionLayout.visibility = View.VISIBLE
                                                predictionTitle.text = getString(R.string.prediction_title, prediction.title)
                                                val totalPoints = prediction.outcomes?.sumOf { it.totalPoints?.toLong() ?: 0 } ?: 0
                                                predictionOutcomes.text = prediction.outcomes?.joinToString("\n") { it ->
                                                    getString(
                                                        R.string.prediction_outcome,
                                                        (((it.totalPoints ?: 0).toLong() * 100.0) / max(totalPoints, 1)).roundToInt(),
                                                        it.totalPoints?.let { NumberFormat.getInstance().format(it) },
                                                        it.totalUsers?.let { NumberFormat.getInstance().format(it) },
                                                        it.title
                                                    )
                                                }
                                                viewModel.predictionSecondsLeft.value = null
                                                viewModel.predictionTimer?.cancel()
                                                viewModel.startPredictionTimeout { predictionLayout.visibility = View.GONE }
                                                predictionStatus.visibility = View.VISIBLE
                                                predictionStatus.text = getString(R.string.prediction_locked)
                                            }
                                            "CANCELED", "CANCEL_PENDING", "RESOLVED", "RESOLVE_PENDING" -> {
                                                predictionLayout.visibility = View.VISIBLE
                                                predictionTitle.text = getString(R.string.prediction_title, prediction.title)
                                                val resolved = prediction.status == "RESOLVED" || prediction.status == "RESOLVE_PENDING"
                                                val totalPoints = prediction.outcomes?.sumOf { it.totalPoints?.toLong() ?: 0 } ?: 0
                                                predictionOutcomes.text = prediction.outcomes?.joinToString("\n") { it ->
                                                    getString(
                                                        if (resolved && prediction.winningOutcomeId != null && prediction.winningOutcomeId == it.id) {
                                                            R.string.prediction_outcome_winner
                                                        } else {
                                                            R.string.prediction_outcome
                                                        },
                                                        (((it.totalPoints ?: 0).toLong() * 100.0) / max(totalPoints, 1)).roundToInt(),
                                                        it.totalPoints?.let { NumberFormat.getInstance().format(it) },
                                                        it.totalUsers?.let { NumberFormat.getInstance().format(it) },
                                                        it.title
                                                    )
                                                }
                                                viewModel.predictionSecondsLeft.value = null
                                                viewModel.predictionTimer?.cancel()
                                                viewModel.startPredictionTimeout { predictionLayout.visibility = View.GONE }
                                                if (resolved) {
                                                    predictionStatus.visibility = View.GONE
                                                } else {
                                                    predictionStatus.visibility = View.VISIBLE
                                                    predictionStatus.text = getString(R.string.prediction_refunded)
                                                }
                                            }
                                            else -> {
                                                predictionLayout.visibility = View.GONE
                                                viewModel.predictionSecondsLeft.value = null
                                                viewModel.predictionTimer?.cancel()
                                                viewModel.predictionClosed = true
                                            }
                                        }
                                    }
                                    viewModel.prediction.value = null
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.predictionSecondsLeft.collectLatest {
                                if (it != null) {
                                    predictionStatus.text = getString(R.string.remaining_time, DateUtils.formatElapsedTime(it.toLong()))
                                    if (it <= 0) {
                                        viewModel.predictionSecondsLeft.value = null
                                    }
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.playbackMessage.collectLatest {
                                if (it != null) {
                                    val live = it.live
                                    if (live != null) {
                                        (parentFragment as? PlayerFragment)?.updateLiveStatus(live, it.serverTime, channelLogin)
                                    }
                                    (parentFragment as? PlayerFragment)?.updateViewerCount(it.viewers)
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.streamInfo.collectLatest {
                                if (it != null) {
                                    (parentFragment as? PlayerFragment)?.updateStreamInfo(it.title, it.gameId, null, it.gameName)
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            chatState?.replaceMessages(snapshotChatMessages())
                            viewModel.newMessage.collect { result ->
                                val message = result.first
                                val removeCount = result.third
                                updateAutoScroll()
                                chatState?.appendMessage(message)
                                if (removeCount > 0) {
                                    chatState?.removeMessages(removeCount)
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.addMessages.collect { result ->
                                val messages = result.first
                                updateAutoScroll()
                                chatState?.prependMessages(messages, messageLimit())
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.removeMessages.collect { size ->
                                chatState?.removeMessages(size)
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.updateUserMessages.collectLatest {
                                chatState?.refresh()
                            }
                        }
                    }
                    if (chatUrl != null) {
                        viewModel.startReplay(
                            channelId = channelId,
                            channelLogin = channelLogin,
                            chatUrl = chatUrl,
                            createdAt = args.getString(KEY_CREATED_AT),
                            getCurrentPosition = (parentFragment as PlayerFragment)::getCurrentPosition,
                            getCurrentSpeed = (parentFragment as PlayerFragment)::getCurrentSpeed
                        )
                    }
                } else {
                    chatReplayUnavailable.visibility = View.VISIBLE
                }
            }
            if ((view.parent?.parent?.parent?.parent as? View)?.id != R.id.slidingLayout) {
                ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                    if (activity?.findViewById<LinearLayout>(R.id.navBarContainer)?.isVisible == false) {
                        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                            bottomMargin = insets.bottom
                        }
                    }
                    WindowInsetsCompat.CONSUMED
                }
            }
        }
    }

    override fun initialize() {
        if (!requireContext().prefs().getBoolean(C.CHAT_DISABLE, false)) {
            val args = requireArguments()
            val channelId = args.getString(KEY_CHANNEL_ID)
            val channelLogin = args.getString(KEY_CHANNEL_LOGIN)
            if (args.getBoolean(KEY_IS_LIVE)) {
                viewModel.startLive(
                    requireContext().prefs().getString(C.CHAT_RECENT_MESSAGES_URL,
                        $$"https://recent-messages.robotty.de/api/v2/recent-messages/$channel"
                    ),
                    channelId,
                    channelLogin,
                    args.getString(KEY_CHANNEL_NAME),
                    args.getString(KEY_STREAM_ID)
                )
            } else {
                val videoId = args.getString(KEY_VIDEO_ID)
                val startTime = args.getInt(KEY_START_TIME)
                if (videoId != null && startTime != -1) {
                    viewModel.startReplay(
                        channelId = channelId,
                        channelLogin = channelLogin,
                        videoId = videoId,
                        createdAt = args.getString(KEY_CREATED_AT),
                        startTime = startTime,
                        getCurrentPosition = (parentFragment as PlayerFragment)::getCurrentPosition,
                        getCurrentSpeed = (parentFragment as PlayerFragment)::getCurrentSpeed
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val args = requireArguments()
        val channelId = args.getString(KEY_CHANNEL_ID)
        val channelLogin = args.getString(KEY_CHANNEL_LOGIN)
        if (args.getBoolean(KEY_IS_LIVE)) {
            viewModel.resumeLive(channelId, channelLogin)
        } else {
            viewModel.resumeReplay(
                channelId = channelId,
                channelLogin = channelLogin,
                chatUrl = args.getString(KEY_CHAT_URL),
                videoId = args.getString(KEY_VIDEO_ID),
                createdAt = args.getString(KEY_CREATED_AT),
                startTime = args.getInt(KEY_START_TIME),
                getCurrentPosition = (parentFragment as PlayerFragment)::getCurrentPosition,
                getCurrentSpeed = (parentFragment as PlayerFragment)::getCurrentSpeed
            )
        }
    }

    fun isActive(): Boolean? {
        return viewModel.isActive()
    }

    fun disconnect() {
        viewModel.disconnect()
    }

    fun reconnect() {
        val channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN)
        if (channelLogin != null) {
            viewModel.startLiveChat(requireArguments().getString(KEY_CHANNEL_ID), channelLogin)
            if (requireContext().prefs().getBoolean(C.CHAT_RECENT, true)) {
                viewModel.loadRecentMessages(
                    requireContext().prefs().getString(C.CHAT_RECENT_MESSAGES_URL,
                        $$"https://recent-messages.robotty.de/api/v2/recent-messages/$channel"
                    ),
                    channelLogin,
                )
            }
        }
        viewModel.autoReconnect = true
    }

    fun reloadEmotes() {
        viewModel.reloadEmotes(
            requireArguments().getString(KEY_CHANNEL_ID),
            requireArguments().getString(KEY_CHANNEL_LOGIN)
        )
    }

    fun startReplayChatLoad() {
        viewModel.startReplayChatLoad()
    }

    fun updatePosition(position: Long) {
        viewModel.updatePosition(position)
    }

    fun updateSpeed(speed: Float) {
        viewModel.updateSpeed(speed)
    }

    fun updateStreamId(id: String?) {
        viewModel.streamId = id
    }

    fun emoteMenuIsVisible() = binding.emoteMenu.isVisible

    fun toggleEmoteMenu(enable: Boolean) {
        if (enable) {
            binding.emoteMenu.visibility = View.VISIBLE
        } else {
            binding.emoteMenu.visibility = View.GONE
        }
        toggleBackPressedCallback(enable)
    }

    fun toggleBackPressedCallback(enable: Boolean) {
        if (enable) {
            requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
        } else {
            backPressedCallback.remove()
        }
    }

    fun appendEmote(emote: Emote) {
        // Shared rule: the name plus one separating space, appended at the end.
        val name = emote.name ?: return
        binding.editText.text.append(ChatDraft.afterAppendEmote("", name))
    }

    /**
     * The Enter-key listener the composer installs, sharing the predicate with the other three
     * call sites. `setOnKeyListener` returns the value of its last statement, so the lambda must
     * yield a Boolean (true when the event was handled).
     */
    private fun sendOnEnterListener(onSend: () -> Boolean): View.OnKeyListener =
        View.OnKeyListener { _, keyCode, event ->
            ChatInput.shouldSendOnKey(event.action, keyCode, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER) && onSend()
        }

    /**
     * Applies the reply indicator's visibility, and the state with it. Every write goes through
     * here, so there is exactly one place that can change [composerState]'s `replying`.
     */
    private fun showReplyIndicator(visible: Boolean) {
        composerState.setReplying(visible)
        binding.replyView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun sendMessage(replyId: String? = null): Boolean {
        with(binding) {
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editText.windowToken, 0)
            editText.clearFocus()
            toggleEmoteMenu(false)
            // A send always ends the reply, whether the draft had anything in it.

            composerState.onSent()

            showReplyIndicator(false)
            send.setOnClickListener { sendMessage() }
            editText.setOnKeyListener(sendOnEnterListener { sendMessage() })
            val text = ChatDraft.consume(editText.text.toString())
            editText.text.clear()
            return if (text != null) {
                viewModel.send(
                    message = text,
                    replyId = replyId,
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), true),
                    helixHeaders = TwitchApiHelper.getHelixHeaders(requireContext()),
                    accountId = requireContext().tokenPrefs().getString(C.USER_ID, null),
                    channelId = requireArguments().getString(KEY_CHANNEL_ID),
                    channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN),
                    useApiCommands = requireContext().prefs().getBoolean(C.DEBUG_API_COMMANDS, true),
                    useApiChatMessages = requireContext().prefs().getBoolean(C.DEBUG_API_CHAT_MESSAGES, true),
                    enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
                scrollToBottom()
                true
            } else {
                false
            }
        }
    }

    private fun messageLimit(): Int = requireContext().prefs().getInt(C.CHAT_LIMIT, 600)

    private fun snapshotChatMessages(): List<ChatMessage> = synchronized(viewModel.chatMessages) {
        viewModel.chatMessages.toList()
    }

    private fun buildChatMessageStyle(sizeModifier: Float): ChatMessageStyle {
        val prefs = requireContext().prefs()
        return ChatMessageStyle(
            textSize = ((prefs.getString(C.CHAT_TEXT_SIZE, "14")?.toFloatOrNull() ?: 14f) * sizeModifier).sp,
            emoteSize = ((prefs.getString(C.CHAT_EMOTE_SIZE, "29.5")?.toFloatOrNull() ?: 29.5f) * sizeModifier).dp,
            badgeSize = ((prefs.getString(C.CHAT_BADGE_SIZE, "18.5")?.toFloatOrNull() ?: 18.5f) * sizeModifier).dp,
            animateGifs = prefs.getBoolean(C.ANIMATED_EMOTES, true),
            thirdPartyUserAgent = "Xtra/" + BuildConfig.VERSION_NAME,
        )
    }

    private fun buildChatRenderOptions(chatUrl: String?, messagingEnabled: Boolean, accountLogin: String?): ChatRenderOptions {
        val prefs = requireContext().prefs()
        return ChatRenderOptions(
            strings = requireContext().chatMessageStrings(),
            localTwitchEmotes = synchronized(viewModel.localTwitchEmotes) { viewModel.localTwitchEmotes.toList() },
            thirdPartyEmotes = synchronized(viewModel.thirdPartyEmotes) { viewModel.thirdPartyEmotes.toList() },
            globalBadges = synchronized(viewModel.globalBadges) { viewModel.globalBadges.toList() },
            channelBadges = synchronized(viewModel.channelBadges) { viewModel.channelBadges.toList() },
            cheerEmotes = synchronized(viewModel.cheerEmotes) { viewModel.cheerEmotes.toList() },
            namePaints = synchronized(viewModel.namePaints) { viewModel.namePaints.toList() },
            stvBadges = synchronized(viewModel.stvBadges) { viewModel.stvBadges.toList() },
            personalEmoteSets = synchronized(viewModel.personalEmoteSets) { viewModel.personalEmoteSets.toMap() },
            stvUsers = synchronized(viewModel.stvUsers) { viewModel.stvUsers.toList() },
            enableTimestamps = prefs.getBoolean(C.CHAT_TIMESTAMPS, false),
            timestampFormat = prefs.getString(C.CHAT_TIMESTAMP_FORMAT, "0"),
            firstMsgVisibility = prefs.getString(C.CHAT_FIRST_MSG_VISIBILITY, "0")?.toIntOrNull() ?: 0,
            nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0"),
            useRandomColors = prefs.getBoolean(C.CHAT_RANDOM_COLOR, true),
            useReadableColors = prefs.getBoolean(C.CHAT_THEME_ADAPTED_USERNAME_COLOR, true),
            isLightTheme = requireContext().obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.isLightTheme)).use { it.getBoolean(0, false) },
            useBoldNames = prefs.getBoolean(C.CHAT_BOLD_NAMES, false),
            showNamePaints = prefs.getBoolean(C.CHAT_SHOW_PAINTS, true),
            showSTVBadges = prefs.getBoolean(C.CHAT_SHOW_STV_BADGES, true),
            showPersonalEmotes = prefs.getBoolean(C.CHAT_SHOW_PERSONAL_EMOTES, true),
            showSystemMessageEmotes = prefs.getBoolean(C.CHAT_SYSTEM_MESSAGE_EMOTES, true),
            enableOverlayEmotes = prefs.getBoolean(C.CHAT_ZERO_WIDTH, true),
            loggedInUser = if (messagingEnabled) accountLogin else null,
            chatUrl = chatUrl,
            getEmoteBytes = viewModel::getEmoteBytes,
            emoteQuality = prefs.getString(C.CHAT_IMAGE_QUALITY, "4") ?: "4",
            cache = chatState?.renderCache ?: ChatRenderCache(),
        )
    }

    private fun showMessageDialog(messagingEnabled: Boolean, channelId: String?) {
        MessageClickedDialog.newInstance(messagingEnabled, channelId).show(childFragmentManager, "messageDialog")
    }

    private fun showReplyDialog(messagingEnabled: Boolean) {
        ReplyClickedDialog.newInstance(messagingEnabled).show(childFragmentManager, "replyDialog")
    }

    private fun showImageDialog(image: ChatImage) {
        val click = image.click
        ImageClickedDialog.newInstance(
            image.url4x ?: image.url3x ?: image.url2x ?: image.url1x,
            click?.name,
            click?.format,
            click?.isAnimated ?: image.isAnimated,
            click?.source,
            click?.thirdParty ?: image.thirdParty,
            click?.emoteId,
        ).show(childFragmentManager, "imageDialog")
    }

    private fun hideKeyboardAndFocus() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.editText.windowToken, 0)
        binding.editText.clearFocus()
    }

    /**
     * The list is reversed (newest at index 0 at the bottom). The user counts as "at the bottom"
     * while the bottom row is visible, tolerating one not-yet-scrolled row and ignoring a few px
     * of scroll offset. A strict `!canScrollBackward` check flips on any pixel jitter (emote/image
     * loading, layout changes) and during an in-progress auto-scroll, which spuriously disables
     * auto-scroll and leaves the scroll-down button visible. A pending auto-scroll also counts as
     * at the bottom, so a burst of messages cannot cancel the in-flight scroll and then get lost.
     */
    private fun isAtBottom(): Boolean {
        if (isChatTouched) return false
        if (shouldAutoScroll) return true
        return chatListState.firstVisibleItemIndex <= 1
    }

    /**
     * Evaluates [isAtBottom] for an arriving batch and maintains [shouldAutoScroll]. Assigned,
     * not just set: the moment the user grabs the list this disarms the pending snap, so a busy
     * chat stops fighting the drag instead of pinning them at the bottom with back-to-back
     * snaps. When arming, the current position is snapshotted so the scroll effect can tell a
     * burst backlog (position explained by new rows) apart from the user scrolling away.
     */
    private fun updateAutoScroll() {
        val atBottom = isAtBottom()
        if (atBottom && !shouldAutoScroll) {
            autoScrollIndex = chatListState.firstVisibleItemIndex
            autoScrollSize = chatState?.messages?.size ?: 0
        }
        shouldAutoScroll = atBottom
    }

    fun scrollToBottom() {
        val state = chatState ?: return
        if (!isAdded) return
        shouldAutoScroll = false
        viewLifecycleOwner.lifecycleScope.launch {
            if (state.messages.isNotEmpty()) {
                // The list is reversed, so index 0 is the newest message at the bottom.
                chatListState.scrollToItem(0)
            }
        }
        binding.btnDown.isVisible = false
    }

    override fun onCreateMessageClickedChatState(): ChatState? {
        return chatState
    }

    override fun onCreateReplyClickedChatState(): ChatState? {
        return chatState
    }

    override fun onReplyThreadClicked(message: ChatMessage) {
        chatState?.select(message)
        showReplyDialog(messagingEnabled)
    }

    override fun onReplyClicked(replyId: String?, userLogin: String?, userName: String?, message: String?) {
        with(binding) {
            // The state holder owns the reply (and its transition rules); the Views follow it.
            if (composerState.startReply(
                    replyId = replyId,
                    userName = userName,
                    userLogin = userLogin,
                    message = message,
                    nameDisplay = requireContext().prefs().getString(C.UI_NAME_DISPLAY, "0"),
                    format = { name, text -> getString(R.string.replying_to_message, name, text) },
                )
            ) {
                messageDialog?.dismiss()
                showReplyIndicator(true)
                replyText.text = composerState.replyLabel.orEmpty()
                replyClose.setOnClickListener {
                    // Back to a plain message: the reply is canceled, so send forgets its id.
                    showReplyIndicator(false)
                    send.setOnClickListener { sendMessage() }
                    editText.setOnKeyListener(sendOnEnterListener { sendMessage() })
                }
                send.setOnClickListener { sendMessage(replyId) }
                editText.setOnKeyListener(sendOnEnterListener { sendMessage(replyId) })
            }
            editText.apply {
                requestFocus()
                WindowCompat.getInsetsController(this@ChatFragment.requireActivity().window, this).show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    override fun onCopyMessageClicked(message: String) {
        binding.editText.setText(message)
    }

    override fun onViewProfileClicked(id: String?, login: String?, name: String?, channelImage: String?) {
        findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = id,
                channelLogin = login,
                channelName = name,
                channelImage = channelImage
            )
        )
        (parentFragment as? PlayerFragment)?.minimize()
    }

    override fun onNetworkRestored() {
        if (isResumed) {
            val args = requireArguments()
            val channelId = args.getString(KEY_CHANNEL_ID)
            val channelLogin = args.getString(KEY_CHANNEL_LOGIN)
            if (args.getBoolean(KEY_IS_LIVE)) {
                viewModel.resumeLive(channelId, channelLogin)
            } else {
                viewModel.resumeReplay(
                    channelId = channelId,
                    channelLogin = channelLogin,
                    chatUrl = args.getString(KEY_CHAT_URL),
                    videoId = args.getString(KEY_VIDEO_ID),
                    createdAt = args.getString(KEY_CREATED_AT),
                    startTime = args.getInt(KEY_START_TIME),
                    getCurrentPosition = (parentFragment as PlayerFragment)::getCurrentPosition,
                    getCurrentSpeed = (parentFragment as PlayerFragment)::getCurrentSpeed
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!requireArguments().getBoolean(KEY_IS_LIVE) || !requireContext().prefs().getBoolean(C.PLAYER_KEEP_CHAT_OPEN, false)) {
            viewModel.stopLiveChat()
            viewModel.stopReplayChat()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class SpaceTokenizer : MultiAutoCompleteTextView.Tokenizer {

        override fun findTokenStart(text: CharSequence, cursor: Int): Int {
            var i = cursor

            while (i > 0 && text[i - 1] != ' ') {
                i--
            }
            while (i < cursor && text[i] == ' ') {
                i++
            }

            return i
        }

        override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
            var i = cursor
            val len = text.length

            while (i < len) {
                if (text[i] == ' ') {
                    return i
                } else {
                    i++
                }
            }

            return len
        }

        override fun terminateToken(text: CharSequence): CharSequence {
            return "${if (text.startsWith(':')) text.substring(1) else text} "
        }
    }

    companion object {
        private const val KEY_IS_LIVE = "isLive"
        private const val KEY_CHANNEL_ID = "channel_id"
        private const val KEY_CHANNEL_LOGIN = "channel_login"
        private const val KEY_CHANNEL_NAME = "channel_name"
        private const val KEY_STREAM_ID = "streamId"
        private const val KEY_VIDEO_ID = "videoId"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_CHAT_URL = "chatUrl"
        private const val KEY_START_TIME = "startTime"

        fun newInstance(channelId: String?, channelLogin: String?, channelName: String?, streamId: String?): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(KEY_IS_LIVE, true)
                    putString(KEY_CHANNEL_ID, channelId)
                    putString(KEY_CHANNEL_LOGIN, channelLogin)
                    putString(KEY_CHANNEL_NAME, channelName)
                    putString(KEY_STREAM_ID, streamId)
                }
            }
        }

        fun newInstance(channelId: String?, channelLogin: String?, videoId: String?, createdAt: String?, startTime: Int?): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(KEY_IS_LIVE, false)
                    putString(KEY_CHANNEL_ID, channelId)
                    putString(KEY_CHANNEL_LOGIN, channelLogin)
                    putString(KEY_VIDEO_ID, videoId)
                    putString(KEY_CREATED_AT, createdAt)
                    putInt(KEY_START_TIME, (startTime ?: -1))
                }
            }
        }

        fun newLocalInstance(channelId: String?, channelLogin: String?, createdAt: String?, chatUrl: String?): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putString(KEY_CHANNEL_ID, channelId)
                    putString(KEY_CHANNEL_LOGIN, channelLogin)
                    putString(KEY_CREATED_AT, createdAt)
                    putString(KEY_CHAT_URL, chatUrl)
                }
            }
        }
    }
}
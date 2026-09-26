package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.network.NetworkHeaders
import com.github.andreyasadchy.xtra.model.chat.ChatImage
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.ChatMessageBackground
import com.github.andreyasadchy.xtra.model.chat.ChatMessageContent
import com.github.andreyasadchy.xtra.model.chat.ChatToken
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import com.github.andreyasadchy.xtra.ui.XtraAsyncImage
import com.github.andreyasadchy.xtra.util.chat.ChatMessageFormatter
import com.github.andreyasadchy.xtra.util.chat.ChatRenderOptions
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Compose-side rendering configuration of a chat row: everything `chat_list_item.xml` plus
 * `ChatAdapter` used to carry (text/emote/badge sizes, GIF animation, the third-party
 * User-Agent and the row padding).
 */
data class ChatMessageStyle(
    val textSize: TextUnit = 14.sp,
    val emoteSize: Dp = 29.5.dp,
    val badgeSize: Dp = 18.5.dp,
    val animateGifs: Boolean = true,
    val thirdPartyUserAgent: String? = null,
    val contentPadding: PaddingValues = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
)

/** Row highlights, mirroring `app/src/main/res/values/colors.xml`. */
private val ChatMessageFirstColor = Color(0x800A4028)
private val ChatMessageRewardColor = Color(0x800E4C68)
private val ChatMessageNoticeColor = Color(0x803E0E68)
private val ChatMessageMentionColor = Color(0x80680E0E)
private val ChatMessageSelectedColor = Color(0x80163584)

/**
 * One chat message: the Compose replacement for `ChatAdapter.ViewHolder` together with
 * `ChatAdapterUtils.prepareChatMessage` and `loadImages`.
 *
 * The formatted [ChatMessageContent] is built once per message and
 * [ChatRenderOptions.generation], then rendered as a single [Text] with emotes, badges and reward
 * icons as inline content. Reply rows are limited to two lines and open their parent message
 * instead of themselves.
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    options: ChatRenderOptions,
    modifier: Modifier = Modifier,
    style: ChatMessageStyle = ChatMessageStyle(),
    selected: Boolean = false,
    onMessageClick: ((ChatMessage) -> Unit)? = null,
    onReplyClick: ((ChatMessage) -> Unit)? = null,
    onImageClick: ((ChatImage) -> Unit)? = null,
) {
    val content = remember(message, options.generation) {
        ChatMessageFormatter.format(message, options)
    }
    val isReply = message.type == ChatMessage.REPLY_MESSAGE
    val backgroundColor = chatMessageColor(content.background, selected)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = style.textSize)
    val thirdPartyHeaders = remember(style.thirdPartyUserAgent) {
        style.thirdPartyUserAgent?.let { agent ->
            NetworkHeaders.Builder().apply { add("User-Agent", agent) }.build()
        }
    }
    val formatted = buildChatMessageText(
        content = content,
        options = options,
        textStyle = textStyle,
        maskBackground = backgroundColor ?: Color.Transparent,
        linkColor = MaterialTheme.colorScheme.primary,
        style = style,
        thirdPartyHeaders = thirdPartyHeaders,
        linkify = !isReply,
        onImageClick = onImageClick,
    )
    val onClick = when {
        isReply -> message.replyParent?.let { parent -> onReplyClick?.let { click -> { click(parent) } } }
        else -> onMessageClick?.let { click -> { click(message) } }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = formatted.text,
            inlineContent = formatted.inlineContent,
            style = textStyle,
            modifier = modifier
                .fillMaxWidth()
                .then(if (backgroundColor != null) Modifier.background(backgroundColor) else Modifier)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(style.contentPadding),
            maxLines = if (isReply) 2 else Int.MAX_VALUE,
            overflow = if (isReply) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
    }
}

/** Row background of [background], with the dialog selection taking precedence. */
private fun chatMessageColor(background: ChatMessageBackground, selected: Boolean): Color? {
    if (selected) {
        return ChatMessageSelectedColor
    }
    return when (background) {
        ChatMessageBackground.NONE -> null
        ChatMessageBackground.FIRST -> ChatMessageFirstColor
        ChatMessageBackground.REWARD -> ChatMessageRewardColor
        ChatMessageBackground.NOTICE -> ChatMessageNoticeColor
        ChatMessageBackground.MENTION -> ChatMessageMentionColor
    }
}

private class ChatMessageText(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
)

/**
 * Turns the formatted tokens into the annotated string and the inline content map [Text] needs.
 * Emotes/badges become inline images, painted names become measured inline content and links get a
 * [LinkAnnotation] (skipped for reply rows, which are not link clickable in the old renderer).
 */
@Composable
private fun buildChatMessageText(
    content: ChatMessageContent,
    options: ChatRenderOptions,
    textStyle: TextStyle,
    maskBackground: Color,
    linkColor: Color,
    style: ChatMessageStyle,
    thirdPartyHeaders: NetworkHeaders?,
    linkify: Boolean,
    onImageClick: ((ChatImage) -> Unit)?,
): ChatMessageText {
    val textMeasurer = rememberTextMeasurer()
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val text = buildAnnotatedString {
        content.tokens.forEach { token ->
            when (token) {
                is ChatToken.Text -> {
                    withStyle(
                        SpanStyle(
                            color = token.color?.let { Color(it) } ?: Color.Unspecified,
                            fontWeight = if (token.bold) FontWeight.Bold else null,
                        )
                    ) {
                        append(token.text)
                    }
                }
                is ChatToken.Link -> {
                    if (linkify) {
                        withLink(LinkAnnotation.Url(token.url, TextLinkStyles(SpanStyle(color = linkColor)))) {
                            append(token.text)
                        }
                    } else {
                        append(token.text)
                    }
                }
                is ChatToken.Image -> {
                    val id = "image${inlineContent.size}"
                    val click = token.image.click
                    inlineContent[id] = inlineChatImage(
                        image = token.image,
                        options = options,
                        size = if (token.image.isBadge) style.badgeSize else style.emoteSize,
                        style = style,
                        thirdPartyHeaders = thirdPartyHeaders,
                        onImageClick = if (click != null) onImageClick else null,
                    )
                    appendInlineContent(id, click?.name ?: "\uFFFC")
                }
                is ChatToken.PaintedName -> {
                    val id = "paint${inlineContent.size}"
                    inlineContent[id] = inlinePaintedName(token, textStyle, maskBackground, textMeasurer, thirdPartyHeaders)
                    appendInlineContent(id, token.text)
                }
            }
        }
    }
    return ChatMessageText(text, inlineContent)
}

@Composable
private fun inlineChatImage(
    image: ChatImage,
    options: ChatRenderOptions,
    size: Dp,
    style: ChatMessageStyle,
    thirdPartyHeaders: NetworkHeaders?,
    onImageClick: ((ChatImage) -> Unit)?,
): InlineTextContent {
    return InlineTextContent(
        placeholder = Placeholder(width = size.value.sp, height = size.value.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Center),
        children = {
            Box(modifier = if (onImageClick != null) Modifier.clickable { onImageClick(image) } else Modifier) {
                chatImageLayers(image, options, style, thirdPartyHeaders)
            }
        },
    )
}

/**
 * Draws an emote/badge and, on top of it, its zero-width overlay emotes. `LayerDrawable` used to
 * do this in the View renderer.
 */
@Composable
private fun chatImageLayers(
    image: ChatImage,
    options: ChatRenderOptions,
    style: ChatMessageStyle,
    thirdPartyHeaders: NetworkHeaders?,
) {
    XtraAsyncImage(
        model = image.dataFor(options.emoteQuality),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        httpHeaders = if (image.thirdParty) thirdPartyHeaders else null,
        modifier = Modifier.fillMaxSize(),
        animate = style.animateGifs,
    )
    image.overlay?.let { overlay ->
        chatImageLayers(overlay, options, style, thirdPartyHeaders)
    }
}

/**
 * Renders a user name with a 7TV name paint.
 *
 * Gradients are drawn into the glyphs with a [Brush], image paints render the image through the
 * glyphs (the `PorterDuff.SRC` text mask `NamePaintImageSpan` drew) and the shadows of the paint
 * are drawn as extra text layers underneath, all like the View spans did.
 */
@Composable
private fun inlinePaintedName(
    token: ChatToken.PaintedName,
    textStyle: TextStyle,
    maskBackground: Color,
    textMeasurer: TextMeasurer,
    thirdPartyHeaders: NetworkHeaders?,
): InlineTextContent {
    val style = textStyle.copy(
        color = Color(token.color),
        fontWeight = if (token.bold) FontWeight.Bold else null,
    )
    val layout = textMeasurer.measure(token.text, style)
    val density = LocalDensity.current
    return InlineTextContent(
        placeholder = Placeholder(
            width = with(density) { layout.size.width.toSp() },
            height = with(density) { layout.size.height.toSp() },
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        ),
        children = {
            if (token.paint.type == "URL" && !token.paint.imageUrl.isNullOrBlank()) {
                paintedNameImage(token, style, maskBackground, thirdPartyHeaders)
            } else {
                paintedNameBrush(token, layout)
            }
        },
    )
}

/** Shadow layers of a paint, drawn before the painted name itself. */
private fun NamePaint.brushShadows(): List<Shadow> = shadows.orEmpty().map { shadow ->
    Shadow(
        color = Color(shadow.color),
        offset = Offset(shadow.xOffset, shadow.yOffset),
        blurRadius = shadow.radius,
    )
}

@Composable
private fun paintedNameBrush(token: ChatToken.PaintedName, layout: TextLayoutResult) {
    val shadows = token.paint.brushShadows()
    Canvas(modifier = Modifier.fillMaxSize()) {
        val brush = paintBrush(token.paint, size)
        shadows.forEach { shadow ->
            if (brush != null) {
                drawText(textLayoutResult = layout, brush = brush, shadow = shadow)
            } else {
                drawText(textLayoutResult = layout, color = Color(token.color), shadow = shadow)
            }
        }
        if (brush != null) {
            drawText(textLayoutResult = layout, brush = brush)
        } else {
            drawText(textLayoutResult = layout, color = Color(token.color))
        }
    }
}

@Composable
private fun paintedNameImage(
    token: ChatToken.PaintedName,
    style: TextStyle,
    maskBackground: Color,
    thirdPartyHeaders: NetworkHeaders?,
) {
    val shadows = token.paint.brushShadows()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(maskBackground))
        shadows.forEach { shadow ->
            Text(
                text = token.text,
                style = style.copy(shadow = shadow),
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
        ) {
            Text(
                text = token.text,
                style = style,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            XtraAsyncImage(
                model = token.paint.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                httpHeaders = thirdPartyHeaders,
                modifier = Modifier.fillMaxSize().graphicsLayer { blendMode = BlendMode.SrcIn },
            )
        }
    }
}

/**
 * Gradient brush of a paint, positioned like `NamePaintSpan`: a horizontal gradient rotated by
 * `angle - 90` degrees around the center, or a radial gradient centered on the name.
 */
private fun paintBrush(paint: NamePaint, size: Size): Brush? {
    val colors = paint.colors?.map { Color(it) } ?: return null
    val positions = paint.colorPositions?.toList() ?: return null
    if (colors.isEmpty() || colors.size != positions.size) {
        return null
    }
    val tileMode = if (paint.repeat == true) TileMode.Repeated else TileMode.Clamp
    return when (paint.type) {
        "LINEAR_GRADIENT" -> {
            val radians = ((paint.angle ?: 0) - 90) * PI / 180.0
            val cos = cos(radians).toFloat()
            val sin = sin(radians).toFloat()
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            fun rotateX(x: Float, y: Float) = centerX + (x - centerX) * cos - (y - centerY) * sin
            fun rotateY(x: Float, y: Float) = centerY + (x - centerX) * sin + (y - centerY) * cos
            val (startX, startY) = rotateX(0f, 0f) to rotateY(0f, 0f)
            val (endX, endY) = rotateX(size.width, 0f) to rotateY(size.width, 0f)
            Brush.linearGradient(
                *(colors.zip(positions) { color, position -> position to color }.toTypedArray()),
                start = Offset(startX, min(startY, size.height)),
                end = Offset(endX, max(endY, 0f)),
                tileMode = tileMode,
            )
        }
        "RADIAL_GRADIENT" -> Brush.radialGradient(
            *(colors.zip(positions) { color, position -> position to color }.toTypedArray()),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.width / 2f,
            tileMode = tileMode,
        )
        else -> null
    }
}

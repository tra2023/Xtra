package com.github.andreyasadchy.xtra.ui.common

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import com.github.andreyasadchy.xtra.util.HeadingFixPlugin

/**
 * Renders Markdown with Markwon inside a Compose screen.
 *
 * Markwon is an Android View library with no Compose equivalent, so this hosts a
 * `TextView` in an `AndroidView`. The click-to-expand is wired onto the TextView
 * itself rather than a Compose modifier, so link taps keep going to
 * [LinkMovementMethod] exactly as they did from XML.
 *
 * @param collapsedMaxLines when set, the text starts clamped to this many lines
 * and a tap toggles between clamped and full.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int? = null,
    color: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(HeadingFixPlugin())
            .build()
    }
    var expanded by remember(markdown) { mutableStateOf(false) }
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            if (view.tag != markdown) {
                view.tag = markdown
                markwon.setMarkdown(view, markdown)
            }
            if (collapsedMaxLines != null) {
                view.maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines
                view.setOnClickListener { expanded = !expanded }
            }
            if (color != Color.Unspecified) {
                view.setTextColor(color.toArgb())
            }
        },
        modifier = modifier,
    )
}

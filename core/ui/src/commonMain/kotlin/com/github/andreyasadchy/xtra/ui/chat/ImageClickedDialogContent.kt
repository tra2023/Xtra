package com.github.andreyasadchy.xtra.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun ImageClickedDialogContent(
    model: Any?,
    imageName: String?,
    imageSource: String?,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 8.dp,
) {
    Surface(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = model,
                contentDescription = imageName,
                modifier = Modifier.fillMaxWidth().height(70.dp),
                contentScale = ContentScale.Fit,
            )
            imageName?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            imageSource?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

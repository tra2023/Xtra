package com.github.andreyasadchy.xtra.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.model.ui.Tag

data class TagSearchLoadState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

@Composable
fun TagSearchContent(
    query: String,
    appliedQuery: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    itemCount: Int,
    tagAt: (Int) -> Tag?,
    refreshState: TagSearchLoadState,
    prependState: TagSearchLoadState,
    appendState: TagSearchLoadState,
    searchLabel: String,
    clearLabel: String,
    emptyLabel: String,
    retryLabel: String,
    onRetry: () -> Unit,
    onTagSelected: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberSaveable(appliedQuery, saver = LazyListState.Saver) { LazyListState() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var requestedFocus by remember { mutableStateOf(false) }
    LaunchedEffect(windowFocused) {
        if (windowFocused && !requestedFocus) {
            focusRequester.requestFocus()
            keyboard?.show()
            requestedFocus = true
        }
    }

    Surface(modifier) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(searchLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSubmit()
                    keyboard?.hide()
                }),
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        TextButton(onClick = {
                            onQueryChange("")
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }) {
                            Text(clearLabel)
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Box(Modifier.fillMaxWidth().weight(1f, fill = false).height(400.dp)) {
                if (itemCount == 0) {
                    when {
                        refreshState.isLoading || refreshState.error != null -> TagSearchLoadStatus(
                            state = refreshState,
                            retryLabel = retryLabel,
                            onRetry = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        emptyLabel.isNotEmpty() -> Text(
                            text = emptyLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        )
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        if (refreshState.isLoading || refreshState.error != null) {
                            item {
                                TagSearchLoadStatus(refreshState, retryLabel, onRetry)
                            }
                        }
                        if (prependState.isLoading || prependState.error != null) {
                            item {
                                TagSearchLoadStatus(prependState, retryLabel, onRetry)
                            }
                        }
                        items(count = itemCount) { index ->
                            val tag = tagAt(index)
                            Box(
                                Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
                                    .clickable(enabled = tag != null, role = Role.Button) {
                                        tag?.let(onTagSelected)
                                    }.padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                tag?.name?.let { name ->
                                    Text(name, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        if (appendState.isLoading || appendState.error != null) {
                            item {
                                TagSearchLoadStatus(appendState, retryLabel, onRetry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagSearchLoadStatus(
    state: TagSearchLoadState,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        }
        state.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            TextButton(onClick = onRetry) {
                Text(retryLabel)
            }
        }
    }
}

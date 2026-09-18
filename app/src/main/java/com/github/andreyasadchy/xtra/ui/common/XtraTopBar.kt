package com.github.andreyasadchy.xtra.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R

/**
 * The app bar shared by the full-Compose screens: up affordance, title, an
 * optional extra-actions slot, search, and an overflow holding settings,
 * log in/out and any [extraOverflow] entries. Replaces the View toolbars these
 * screens used to inflate from `@menu/top_menu`. [liftOptOut] is the
 * `UI_THEME_APPBAR_LIFT` opt-out, which flattens the scrolled container colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XtraTopBar(
    title: String,
    isLoggedIn: Boolean,
    liftOptOut: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    up: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    extraOverflow: List<Pair<String, () -> Unit>> = emptyList(),
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (up != null) {
                IconButton(onClick = up) {
                    Icon(painterResource(R.drawable.baseline_arrow_back_black_24), contentDescription = null)
                }
            }
        },
        actions = {
            actions()
            IconButton(onClick = onSearch) {
                Icon(painterResource(R.drawable.baseline_search_black_24), contentDescription = stringResource(R.string.search))
            }
            IconButton(onClick = { overflowExpanded = true }) {
                OverflowIcon()
            }
            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings)) },
                    onClick = {
                        overflowExpanded = false
                        onSettings()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (isLoggedIn) R.string.log_out else R.string.log_in)) },
                    onClick = {
                        overflowExpanded = false
                        onLogin()
                    },
                )
                extraOverflow.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            overflowExpanded = false
                            action()
                        },
                    )
                }
            }
        },
        colors = if (liftOptOut) {
            TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface)
        } else {
            TopAppBarDefaults.topAppBarColors()
        },
    )
}

@Composable
private fun OverflowIcon() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp)) {
        for (position in 1..3) {
            drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(size.width / 2, size.height * position / 4))
        }
    }
}

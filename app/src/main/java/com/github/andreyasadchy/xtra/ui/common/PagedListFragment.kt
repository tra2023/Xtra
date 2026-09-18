package com.github.andreyasadchy.xtra.ui.common

/**
 * Base for the screens whose content is a Compose paging list.
 *
 * The list bodies live in `:core:ui` ([PagingGrid] and the per-media tabs in
 * `ListTabs.kt`), so this only carries the network-refresh contract and the
 * integrity-token listener the hosts implement. It used to own the hidden
 * RecyclerView container and the `PagingContent` wrapper; both are gone now that
 * every list screen returns its own `ComposeView`.
 */
abstract class PagedListFragment : BaseNetworkFragment(), IntegrityDialog.Listener

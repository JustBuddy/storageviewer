package wtf.buddyworks.storageviewer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.data.AppsState
import wtf.buddyworks.storageviewer.data.ScanState
import wtf.buddyworks.storageviewer.storageViewerApp
import wtf.buddyworks.storageviewer.ui.apps.AppsListPane
import wtf.buddyworks.storageviewer.ui.apps.AppsOverview
import wtf.buddyworks.storageviewer.ui.apps.AppsViewModel
import wtf.buddyworks.storageviewer.ui.components.FloatingTabBar
import wtf.buddyworks.storageviewer.ui.components.FloatingTabBarHeight
import wtf.buddyworks.storageviewer.ui.components.NavItem
import wtf.buddyworks.storageviewer.ui.components.NavRail
import wtf.buddyworks.storageviewer.ui.components.RailWidth
import wtf.buddyworks.storageviewer.ui.components.Toolbar
import wtf.buddyworks.storageviewer.ui.components.ToolbarIcon
import wtf.buddyworks.storageviewer.ui.storage.StorageListPane
import wtf.buddyworks.storageviewer.ui.storage.StorageOverview
import wtf.buddyworks.storageviewer.ui.storage.StorageViewModel
import wtf.buddyworks.storageviewer.ui.theme.OneUi

enum class Tab { Storage, Apps }

/** Where the list and the overview go, derived from window size and hinge position. */
private sealed interface Panes {
    data object Single : Panes

    /** Book posture or a wide window: list | overview, split at the hinge when there is one. */
    data class SideBySide(val listEnd: Dp, val overviewStart: Dp) : Panes

    /** Tabletop posture: overview above the fold, list below it. */
    data class Stacked(val overviewBottom: Dp, val listTop: Dp) : Panes
}

private val PaneGap = 8.dp
private val OuterMargin = 12.dp

@Composable
fun AppRoot() {
    val app = LocalContext.current.storageViewerApp
    val storageVm = viewModel { StorageViewModel(app) }
    val appsVm = viewModel { AppsViewModel(app) }
    var tab by rememberSaveable { mutableStateOf(Tab.Storage) }
    var overviewOpen by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        storageVm.onResume()
        appsVm.onResume()
    }

    val fold = rememberFoldingFeature()
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val stateHolder = rememberSaveableStateHolder()

    val appsState by appsVm.state.collectAsStateWithLifecycle()
    val appsBytes = (appsState as? AppsState.Loaded)?.takeIf { it.withStats }?.apps?.sumOf { it.totalBytes }

    val navItems = listOf(
        NavItem(stringResource(R.string.nav_storage), Icons.Outlined.PieChart, Icons.Filled.PieChart),
        NavItem(stringResource(R.string.nav_apps), Icons.Outlined.GridView, Icons.Filled.GridView),
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(OneUi.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        val useRail = maxWidth >= 600.dp
        val railWidth = if (useRail) RailWidth else 0.dp
        val panes = computePanes(maxWidth, maxHeight, railWidth, fold, density)
        val single = panes is Panes.Single

        @Composable
        fun listPane(config: PaneConfig, modifier: Modifier) {
            Crossfade(targetState = tab, animationSpec = tween(220), label = "tab", modifier = modifier) { current ->
                stateHolder.SaveableStateProvider(current) {
                    when (current) {
                        Tab.Storage -> StorageListPane(storageVm, appsBytes, config, onShowOverview = { overviewOpen = true })
                        Tab.Apps -> AppsListPane(appsVm, config, onShowOverview = { overviewOpen = true })
                    }
                }
            }
        }

        @Composable
        fun overview(contentPadding: PaddingValues) {
            Crossfade(targetState = tab, animationSpec = tween(220), label = "overview") { current ->
                when (current) {
                    Tab.Storage -> StorageOverviewPane(storageVm, contentPadding)
                    Tab.Apps -> AppsOverviewPane(appsVm, contentPadding)
                }
            }
        }

        val rail: @Composable () -> Unit = {
            if (useRail) {
                NavRail(navItems, tab.ordinal, { tab = Tab.entries[it]; overviewOpen = false }, topInset)
            }
        }

        when (panes) {
            Panes.Single -> {
                val tall = maxHeight >= 560.dp
                Row(Modifier.fillMaxSize()) {
                    rail()
                    listPane(
                        PaneConfig(
                            expandableTitle = tall,
                            topInset = topInset,
                            bottomPadding = bottomInset + if (useRail) 16.dp else FloatingTabBarHeight + 28.dp,
                            twoPane = false,
                        ),
                        Modifier.weight(1f),
                    )
                }
                if (!useRail) {
                    FloatingTabBar(
                        navItems,
                        tab.ordinal,
                        { tab = Tab.entries[it] },
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomInset + 14.dp),
                    )
                }
            }

            is Panes.SideBySide -> Row(Modifier.fillMaxSize()) {
                rail()
                listPane(
                    PaneConfig(expandableTitle = false, topInset = topInset, bottomPadding = bottomInset + 16.dp, twoPane = true),
                    Modifier
                        .width(panes.listEnd - railWidth)
                        .fillMaxHeight(),
                )
                Spacer(Modifier.width(panes.overviewStart - panes.listEnd))
                OverviewCard(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = topInset + PaneGap, bottom = bottomInset + OuterMargin, end = OuterMargin),
                ) { overview(PaddingValues(top = 16.dp, bottom = 16.dp)) }
            }

            is Panes.Stacked -> Row(Modifier.fillMaxSize()) {
                rail()
                Column(Modifier.weight(1f)) {
                    OverviewCard(
                        Modifier
                            .fillMaxWidth()
                            .height(panes.overviewBottom - PaneGap)
                            .padding(
                                top = topInset + PaneGap,
                                start = if (useRail) 0.dp else OuterMargin,
                                end = OuterMargin,
                            ),
                    ) { overview(PaddingValues(top = 16.dp, bottom = 16.dp)) }
                    Spacer(Modifier.height(panes.listTop - panes.overviewBottom + PaneGap * 2))
                    listPane(
                        PaneConfig(expandableTitle = false, topInset = 0.dp, bottomPadding = bottomInset + 16.dp, twoPane = true),
                        Modifier.weight(1f),
                    )
                }
            }
        }

        // Cover screen: the overview opens as its own screen.
        AnimatedVisibility(
            visible = single && overviewOpen,
            enter = slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(OneUi.colors.background),
            ) {
                Toolbar(
                    title = stringResource(R.string.overview_title),
                    topInset = topInset,
                    navigationIcon = {
                        ToolbarIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), { overviewOpen = false })
                    },
                )
                OverviewCard(
                    Modifier
                        .fillMaxSize()
                        .padding(start = OuterMargin, end = OuterMargin, bottom = bottomInset + OuterMargin),
                ) { overview(PaddingValues(top = 16.dp, bottom = 16.dp)) }
            }
        }
    }

    BackHandler(enabled = overviewOpen) { overviewOpen = false }
}

/** The big rounded card One UI uses for a detail pane. */
@Composable
private fun OverviewCard(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(OneUi.paneShape)
            .background(OneUi.colors.card),
    ) { content() }
}

@Composable
private fun StorageOverviewPane(vm: StorageViewModel, contentPadding: PaddingValues) {
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val root = (scan as? ScanState.Done)?.result?.root
    val focused = remember(root, vm.focusedPath) { root?.let { r -> vm.focusedPath?.let(r::find) ?: r } }
    if (focused == null) {
        val message = if (!vm.hasAccess) R.string.access_files_title else R.string.scanning
        PanePlaceholder(stringResource(message))
    } else {
        StorageOverview(
            node = focused,
            rootPath = vm.rootPath,
            onFocus = vm::focus,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun AppsOverviewPane(vm: AppsViewModel, contentPadding: PaddingValues) {
    val state by vm.state.collectAsStateWithLifecycle()
    val loaded = state as? AppsState.Loaded
    if (loaded == null) {
        PanePlaceholder(stringResource(R.string.loading_apps, "…", "…"))
    } else {
        val visible = remember(loaded.apps, vm.filter, vm.query) { vm.visible(loaded.apps) }
        AppsOverview(visible, Modifier.fillMaxSize(), contentPadding)
    }
}

@Composable
private fun PanePlaceholder(text: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.SpaceDashboard, contentDescription = null, tint = OneUi.colors.textSection, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, style = OneUi.type.body, color = OneUi.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun rememberFoldingFeature(): FoldingFeature? {
    val activity = LocalActivity.current ?: return null
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info: WindowLayoutInfo? by flow.collectAsStateWithLifecycle(initialValue = null)
    return info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
}

private fun computePanes(width: Dp, height: Dp, railWidth: Dp, fold: FoldingFeature?, density: Density): Panes {
    if (fold != null && fold.state == FoldingFeature.State.HALF_OPENED &&
        fold.orientation == FoldingFeature.Orientation.HORIZONTAL
    ) {
        val top = with(density) { fold.bounds.top.toDp() }
        val bottom = with(density) { fold.bounds.bottom.toDp() }
        if (top > 200.dp && height - bottom > 200.dp) return Panes.Stacked(top, bottom)
    }
    if (width < 720.dp) return Panes.Single
    if (fold != null && fold.orientation == FoldingFeature.Orientation.VERTICAL) {
        val left = with(density) { fold.bounds.left.toDp() }
        val right = with(density) { fold.bounds.right.toDp() }
        if (left - railWidth >= 320.dp && width - right >= 320.dp) {
            return Panes.SideBySide(listEnd = left - PaneGap, overviewStart = right + PaneGap)
        }
    }
    val listWidth = ((width - railWidth) * 0.46f).coerceIn(340.dp, 520.dp)
    return Panes.SideBySide(listEnd = railWidth + listWidth, overviewStart = railWidth + listWidth + PaneGap * 2)
}

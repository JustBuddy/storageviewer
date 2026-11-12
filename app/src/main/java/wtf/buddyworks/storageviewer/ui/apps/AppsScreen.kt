package wtf.buddyworks.storageviewer.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.data.Access
import wtf.buddyworks.storageviewer.data.AppEntry
import wtf.buddyworks.storageviewer.data.AppsState
import wtf.buddyworks.storageviewer.data.Format
import wtf.buddyworks.storageviewer.system.Handoff
import wtf.buddyworks.storageviewer.ui.AccessCard
import wtf.buddyworks.storageviewer.ui.CardMargin
import wtf.buddyworks.storageviewer.ui.PaneConfig
import wtf.buddyworks.storageviewer.ui.ProgressCard
import wtf.buddyworks.storageviewer.ui.components.BarPart
import wtf.buddyworks.storageviewer.ui.components.CollapsingPane
import wtf.buddyworks.storageviewer.ui.components.SegmentedBar
import wtf.buddyworks.storageviewer.ui.components.ToolbarIcon
import wtf.buddyworks.storageviewer.ui.components.groupItem
import wtf.buddyworks.storageviewer.ui.theme.OneUi
import wtf.buddyworks.storageviewer.ui.tint

@Composable
fun AppsListPane(
    vm: AppsViewModel,
    config: PaneConfig,
    onShowOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val loaded = state as? AppsState.Loaded
    val all = loaded?.apps.orEmpty()
    val visible = remember(all, vm.filter, vm.query) { vm.visible(all) }
    val largest = visible.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L
    val subtitle = loaded?.let {
        stringResource(
            R.string.apps_subtitle,
            pluralStringResource(R.plurals.apps_count, visible.size, Format.count(visible.size)),
            Format.bytes(visible.sumOf { it.totalBytes }),
        )
    }
    val listState = rememberLazyListState()

    CollapsingPane(
        title = stringResource(R.string.apps_title),
        subtitle = subtitle,
        expandable = config.expandableTitle,
        listState = listState,
        topInset = config.topInset,
        bottomPadding = config.bottomPadding,
        modifier = modifier,
        actions = {
            ToolbarIcon(Icons.Outlined.Search, stringResource(R.string.action_search), {
                if (vm.searching) vm.closeSearch() else vm.searching = true
            })
            if (!config.twoPane && loaded != null) {
                ToolbarIcon(Icons.Outlined.SpaceDashboard, stringResource(R.string.action_overview), onShowOverview)
            }
            ToolbarIcon(Icons.Outlined.Refresh, stringResource(R.string.action_refresh), vm::refresh)
        },
    ) {
        if (vm.searching) {
            item(key = "search", contentType = "search") {
                SearchField(vm.query, onChange = { vm.query = it }, onClose = vm::closeSearch)
            }
        }
        item(key = "filters", contentType = "filters") {
            FilterTabs(vm.filter, onSelect = { vm.filter = it })
        }
        if (!vm.hasUsageAccess) {
            item(key = "usage-access", contentType = "access") {
                AccessCard(
                    icon = Icons.Outlined.QueryStats,
                    title = stringResource(R.string.access_usage_title),
                    body = stringResource(R.string.access_usage_body),
                    button = stringResource(R.string.access_usage_button),
                    onAllow = { Handoff.openSettings(context, Access.usageAccessIntents(context)) },
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }
        val s = state
        if (s is AppsState.Loading || s is AppsState.Idle) {
            item(key = "loading", contentType = "progress") {
                val loading = s as? AppsState.Loading
                ProgressCard(
                    stringResource(R.string.loading_apps, Format.count(loading?.done ?: 0), Format.count(loading?.total ?: 0)),
                    null,
                )
            }
        }
        if (loaded != null && visible.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                Text(
                    stringResource(R.string.no_apps_found),
                    style = OneUi.type.body,
                    color = OneUi.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
        }
        itemsIndexed(visible, key = { _, app -> app.packageName }, contentType = { _, _ -> "app" }) { index, app ->
            AppRow(
                app = app,
                largest = largest,
                isFirst = index == 0,
                isLast = index == visible.lastIndex,
                modifier = Modifier.animateItem(),
                onClick = { Handoff.openAppInfo(context, app.packageName) },
            )
        }
    }
}

/** App / data / cache in one hue: the system accent, lighter for the parts apps can rebuild. */
@Composable
fun appPartColors(): Triple<Color, Color, Color> {
    val accent = OneUi.colors.accent
    return Triple(accent, tint(accent, 0.45f), tint(accent, 0.72f))
}

@Composable
private fun AppRow(
    app: AppEntry,
    largest: Long,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = OneUi.colors
    val (codeColor, dataColor, cacheColor) = appPartColors()
    Row(
        modifier
            .padding(horizontal = CardMargin)
            .fillMaxWidth()
            .groupItem(isFirst, isLast, colors.card)
            .clickable(onClick = onClick)
            .drawBehind {
                if (!isFirst) {
                    drawLine(
                        colors.divider,
                        Offset(78.dp.toPx(), 0f),
                        Offset(size.width - 20.dp.toPx(), 0f),
                        1.dp.toPx(),
                    )
                }
            }
            .heightIn(min = 76.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    style = OneUi.type.itemTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(Format.bytes(app.totalBytes), style = OneUi.type.itemValue, color = colors.textSecondary, maxLines = 1)
            }
            Text(
                if (app.hasStats) {
                    stringResource(R.string.app_row_breakdown, Format.bytes(app.userDataBytes), Format.bytes(app.cacheBytes))
                } else {
                    stringResource(R.string.app_install_size)
                },
                style = OneUi.type.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            SegmentedBar(
                parts = listOf(
                    BarPart("app", app.appBytes, codeColor),
                    BarPart("data", app.userDataBytes, dataColor),
                    BarPart("cache", app.cacheBytes, cacheColor),
                ),
                total = largest,
                description = "",
                height = 4.dp,
                animate = false,
                minPartWidth = 0.dp,
                gap = 1.5.dp,
            )
        }
    }
}

/** My Files-style tab pill: All / Downloaded / System. */
@Composable
private fun FilterTabs(selected: AppFilter, onSelect: (AppFilter) -> Unit) {
    val colors = OneUi.colors
    val labels = mapOf(
        AppFilter.All to stringResource(R.string.filter_all),
        AppFilter.Downloaded to stringResource(R.string.filter_downloaded),
        AppFilter.System to stringResource(R.string.filter_system),
    )
    Row(
        Modifier
            .padding(horizontal = CardMargin)
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.pill)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        AppFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.card else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(filter) })
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    labels.getValue(filter),
                    style = OneUi.type.itemSubtitle.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val colors = OneUi.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .padding(horizontal = CardMargin)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.card)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.search_apps), style = OneUi.type.body, color = colors.textSecondary)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = OneUi.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        ToolbarIcon(Icons.Rounded.Close, stringResource(R.string.action_clear_search), onClose, tint = colors.textSecondary)
    }
}

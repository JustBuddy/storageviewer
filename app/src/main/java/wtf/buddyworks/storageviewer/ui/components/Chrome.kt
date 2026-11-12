package wtf.buddyworks.storageviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wtf.buddyworks.storageviewer.ui.theme.OneUi

val ToolbarHeight = 64.dp

class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** One UI side rail (inner screen): icons only, selected one on a rounded grey square. */
@Composable
fun NavRail(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(RailWidth)
            .fillMaxHeight()
            .padding(top = topInset + ToolbarHeight + 8.dp)
            .selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .size(width = 56.dp, height = 44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) OneUi.colors.selection else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = item.label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (selected) item.selectedIcon else item.icon,
                    contentDescription = null,
                    tint = OneUi.colors.textPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

val RailWidth = 84.dp

/** Floating pill tab bar for the cover screen, in the style of One UI 9's floating controls. */
@Composable
fun FloatingTabBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneUi.colors
    Row(
        modifier
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .clip(CircleShape)
            .background(colors.card)
            .padding(6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Row(
                Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.selection else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (selected) item.selectedIcon else item.icon,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    item.label,
                    style = OneUi.type.nav.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                    color = colors.textPrimary,
                )
            }
        }
    }
}

val FloatingTabBarHeight = 60.dp

/**
 * A scrolling pane with a One UI app bar. When [expandable], the list starts with a large
 * title occupying the top third of the screen (One UI's "viewing area"), which scrolls away
 * and hands over to the small toolbar title.
 */
@Composable
fun CollapsingPane(
    title: String,
    subtitle: String?,
    expandable: Boolean,
    listState: LazyListState,
    topInset: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val headerHeight = if (expandable) (maxHeight * 0.36f - ToolbarHeight).coerceAtLeast(104.dp) else 0.dp
        val headerPx = with(LocalDensity.current) { headerHeight.toPx() }
        val collapse by remember(expandable, headerPx, listState) {
            derivedStateOf {
                when {
                    !expandable -> 1f
                    listState.firstVisibleItemIndex > 0 -> 1f
                    else -> (listState.firstVisibleItemScrollOffset / headerPx).coerceIn(0f, 1f)
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset + ToolbarHeight, bottom = bottomPadding),
        ) {
            if (expandable) {
                item(key = "large-title", contentType = "large-title") {
                    LargeTitle(
                        title = title,
                        subtitle = subtitle,
                        height = headerHeight,
                        modifier = Modifier.graphicsLayer { alpha = (1f - collapse * 1.7f).coerceIn(0f, 1f) },
                    )
                }
            }
            content()
        }
        Toolbar(
            title = title,
            topInset = topInset,
            titleAlpha = { if (expandable) ((collapse - 0.6f) / 0.4f).coerceIn(0f, 1f) else 1f },
            navigationIcon = navigationIcon,
            actions = actions,
        )
    }
}

@Composable
fun Toolbar(
    title: String,
    topInset: Dp,
    titleAlpha: () -> Float = { 1f },
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(OneUi.colors.background)
            .padding(top = topInset)
            .height(ToolbarHeight)
            .padding(start = if (navigationIcon != null) 8.dp else 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null) {
            navigationIcon()
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            style = OneUi.type.toolbarTitle,
            color = OneUi.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = titleAlpha() },
        )
        actions()
    }
}

@Composable
private fun LargeTitle(title: String, subtitle: String?, height: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .padding(start = 24.dp, end = 24.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(title, style = OneUi.type.largeTitle, color = OneUi.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = OneUi.type.body, color = OneUi.colors.textSecondary, maxLines = 2)
        }
    }
}

package wtf.buddyworks.storageviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wtf.buddyworks.storageviewer.ui.theme.OneUi

/** Shape for one row of a rounded One UI list group. */
fun groupShape(isFirst: Boolean, isLast: Boolean, radius: Dp = OneUi.cardRadius): Shape =
    RoundedCornerShape(
        topStart = if (isFirst) radius else 0.dp,
        topEnd = if (isFirst) radius else 0.dp,
        bottomStart = if (isLast) radius else 0.dp,
        bottomEnd = if (isLast) radius else 0.dp,
    )

/**
 * Background for a lazy-list row that belongs to a rounded card group. Only the first and
 * last rows clip (to keep their ripple inside the rounded corners); middle rows stay plain
 * so scrolling does not pay for an extra layer per row.
 */
fun Modifier.groupItem(isFirst: Boolean, isLast: Boolean, color: Color): Modifier {
    if (!isFirst && !isLast) return this.background(color)
    val shape = groupShape(isFirst, isLast)
    return this.clip(shape).background(color, shape)
}

/** A white rounded One UI card. */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(OneUi.cardShape)
            .background(OneUi.colors.card)
            .padding(contentPadding),
        content = content,
    )
}

/** Grey section label that sits above a card, as in One UI Settings. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text,
            style = OneUi.type.section,
            color = OneUi.colors.textSection,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Text(trailing, style = OneUi.type.caption, color = OneUi.colors.textSection, maxLines = 1)
        }
    }
}

/** Pill-shaped One UI button. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = OneUi.colors
    val container = if (filled) colors.accent else colors.pill
    val content = if (filled) colors.onAccent else colors.textPrimary
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = OneUi.type.button, color = content, maxLines = 1)
    }
}

/** Toolbar action icon with a 48dp touch target. */
@Composable
fun ToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = OneUi.colors.textPrimary,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** Takes a toolbar action's place while it runs, such as Refresh during a background rescan. */
@Composable
fun ToolbarProgress(contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.5.dp,
            color = OneUi.colors.accent,
            trackColor = OneUi.colors.track,
        )
    }
}

/** Rounded-square icon on a soft tint of its colour, like One UI's category icons. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) = IconBadge(rememberVectorPainter(icon), tint, modifier, size)

/** [IconBadge] with a shared painter, for long lists. */
@Composable
fun IconBadge(
    icon: Painter,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .background(tint.copy(alpha = if (OneUi.colors.isDark) 0.22f else 0.14f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** Thin divider inside a card, inset so it lines up with the row text. */
@Composable
fun InsetDivider(start: Dp, end: Dp = 20.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = start, end = end)
            .height(1.dp)
            .background(OneUi.colors.divider),
    )
}

/** Thin rounded bar showing one share of a whole, drawn directly (no layers). */
@Composable
fun ShareBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    val track = OneUi.colors.track.copy(alpha = 0.7f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = radius)
        if (fraction > 0f) {
            drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0.015f, 1f), size.height), cornerRadius = radius)
        }
    }
}

/** A row of a card with title, subtitle and trailing content. */
@Composable
fun CardRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = OneUi.type.itemTitle, color = OneUi.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = OneUi.type.itemSubtitle, color = OneUi.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Small coloured legend dot. */
@Composable
fun LegendDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** Section label inside an overview pane. */
@Composable
fun OverviewSectionTitle(text: String) {
    Text(
        text,
        style = OneUi.type.section,
        color = OneUi.colors.textSection,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 6.dp),
    )
}

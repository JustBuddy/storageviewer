package wtf.buddyworks.storageviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wtf.buddyworks.storageviewer.ui.components.GroupCard
import wtf.buddyworks.storageviewer.ui.components.IconBadge
import wtf.buddyworks.storageviewer.R
import wtf.buddyworks.storageviewer.ui.components.PillButton
import wtf.buddyworks.storageviewer.ui.components.ToolbarIcon
import wtf.buddyworks.storageviewer.ui.theme.OneUi

/** How a list pane is placed on screen. */
data class PaneConfig(
    /** One UI large title in the top third (tall single-pane screens only). */
    val expandableTitle: Boolean,
    val topInset: Dp,
    val bottomPadding: Dp,
    /** True when the overview is shown next to (or above) the list. */
    val twoPane: Boolean,
)

/** Horizontal margin of cards inside a list pane. */
val CardMargin = 12.dp

/**
 * Explains something the user can act on, with one button: a special access and the Settings
 * page that grants it, or a failed load and a retry.
 */
@Composable
fun NoticeCard(
    icon: ImageVector,
    title: String,
    body: String,
    button: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupCard(modifier.padding(horizontal = CardMargin), contentPadding = 22.dp) {
        IconBadge(icon, OneUi.colors.accent, size = 48.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = OneUi.type.itemTitle.copy(fontWeight = FontWeight.Bold), color = OneUi.colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(body, style = OneUi.type.itemSubtitle, color = OneUi.colors.textSecondary)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton(button, onClick)
        }
    }
}

/** Card with a spinner and a status line. */
@Composable
fun ProgressCard(title: String, detail: String?, modifier: Modifier = Modifier) {
    GroupCard(modifier.padding(horizontal = CardMargin), contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = OneUi.colors.accent,
                trackColor = OneUi.colors.track,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = OneUi.type.itemTitle, color = OneUi.colors.textPrimary)
                if (detail != null) {
                    Text(
                        detail,
                        style = OneUi.type.caption,
                        color = OneUi.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
        }
    }
}

/** Rounded search field that takes focus when it appears, with a close button. */
@Composable
fun SearchField(query: String, placeholder: String, onChange: (String) -> Unit, onClose: () -> Unit) {
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
                Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis, style = OneUi.type.body, color = colors.textSecondary)
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

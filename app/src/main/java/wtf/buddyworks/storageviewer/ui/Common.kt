package wtf.buddyworks.storageviewer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wtf.buddyworks.storageviewer.ui.components.GroupCard
import wtf.buddyworks.storageviewer.ui.components.IconBadge
import wtf.buddyworks.storageviewer.ui.components.PillButton
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

/** Explains a special access and links to the Settings page that grants it. */
@Composable
fun AccessCard(
    icon: ImageVector,
    title: String,
    body: String,
    button: String,
    onAllow: () -> Unit,
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
            PillButton(button, onAllow)
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

package io.github.aedev.flow.ui.components.musicplayer.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.RepeatMode
import io.github.aedev.flow.ui.components.musicplayer.common.readableAccentOn

private val SegmentFullRadius = 21.dp
private val SegmentEdgeRadius = 8.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSecondaryActions(
    lyricsActive: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onLyricsClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
    queueActive: Boolean = false,
    sleepTimerActive: Boolean = false,
    onSleepTimerClick: (() -> Unit)? = null,
) {
    val trailingShape =
        RoundedCornerShape(
            topStart = SegmentEdgeRadius,
            bottomStart = SegmentEdgeRadius,
            topEnd = SegmentFullRadius,
            bottomEnd = SegmentFullRadius,
        )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerToggleButton(
            checked = lyricsActive,
            checkedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Outlined.Lyrics,
            contentDescription = stringResource(R.string.lyrics),
            uncheckedShape =
                RoundedCornerShape(
                    topStart = SegmentFullRadius,
                    bottomStart = SegmentFullRadius,
                    topEnd = SegmentEdgeRadius,
                    bottomEnd = SegmentEdgeRadius,
                ),
            onClick = onLyricsClick,
        )
        PlayerToggleButton(
            checked = shuffleEnabled,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Rounded.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            uncheckedShape = RoundedCornerShape(SegmentEdgeRadius),
            onClick = onShuffleClick,
        )
        PlayerToggleButton(
            checked = repeatMode != RepeatMode.OFF,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            icon =
                when (repeatMode) {
                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    else -> Icons.Rounded.Repeat
                },
            contentDescription = stringResource(R.string.repeat),
            uncheckedShape = RoundedCornerShape(SegmentEdgeRadius),
            onClick = onRepeatClick,
        )
        PlayerToggleButton(
            checked = queueActive,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Outlined.QueueMusic,
            contentDescription = stringResource(R.string.playlist_queue),
            uncheckedShape = if (onSleepTimerClick == null) trailingShape else RoundedCornerShape(SegmentEdgeRadius),
            onClick = onQueueClick,
        )
        if (onSleepTimerClick != null) {
            PlayerToggleButton(
                checked = sleepTimerActive,
                checkedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                icon = if (sleepTimerActive) Icons.Filled.Bedtime else Icons.Outlined.Bedtime,
                contentDescription = stringResource(R.string.sleep_timer),
                uncheckedShape = trailingShape,
                onClick = onSleepTimerClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.PlayerToggleButton(
    checked: Boolean,
    checkedContainerColor: Color,
    checkedContentColor: Color,
    icon: ImageVector,
    contentDescription: String?,
    uncheckedShape: Shape,
    onClick: () -> Unit,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        colors =
            ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = checkedContainerColor,
                checkedContentColor = checkedContentColor,
            ),
        shapes =
            ToggleButtonShapes(
                shape = uncheckedShape,
                pressedShape = RoundedCornerShape(12.dp),
                checkedShape = RoundedCornerShape(SegmentFullRadius),
            ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun PlayerMainActionButtons(
    isLiked: Boolean,
    isDisliked: Boolean,
    isDownloaded: Boolean,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val buttonColors = IconButtonDefaults.iconButtonColors(contentColor = scheme.onSurface)
    val toggleColors = IconButtonDefaults.iconToggleButtonColors(contentColor = scheme.onSurface, checkedContentColor = scheme.primary)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.IconToggleButton(colors = toggleColors, checked = isDisliked, onCheckedChange = { onDislikeClick() }) {
            Icon(
                if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                contentDescription = stringResource(if (isDisliked) R.string.hm_undo_dislike else R.string.hm_dislike_remove),
            )
        }
        androidx.compose.material3.IconButton(colors = buttonColors, onClick = onDownloadClick) {
            Icon(
                if (isDownloaded) Icons.Rounded.OfflinePin else Icons.Outlined.Download,
                contentDescription = stringResource(R.string.download),
            )
        }
        androidx.compose.material3.IconButton(colors = buttonColors, onClick = onAddToPlaylist) {
            Icon(Icons.Outlined.PlaylistAdd, contentDescription = stringResource(R.string.add_to_playlist))
        }
        androidx.compose.material3.IconToggleButton(colors = toggleColors, checked = isLiked, onCheckedChange = { onLikeClick() }) {
            Icon(
                if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = stringResource(if (isLiked) R.string.hm_remove_like else R.string.hm_like_download),
            )
        }
    }
}

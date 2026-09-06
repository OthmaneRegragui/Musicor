package com.regtho.musicor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun PlayerBar(
    player: PlayerControllerHolder,
    controller: LibraryController,
    onOpenSong: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val song = player.currentPath?.let { controller.findSongByPath(it) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            if (song == null) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MusicorIcons.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "No track playing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SeekBar(player = player)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Clicking the now-playing info opens the playlist that
                    // contains it.
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenSong),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SongArt(song = song, controller = controller, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.displayTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { player.previous() }) {
                        Icon(
                            imageVector = MusicorIcons.Previous,
                            contentDescription = "Previous track",
                        )
                    }
                    IconButton(onClick = { player.togglePlayPause() }) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Icon(
                                imageVector = if (player.isPlaying) MusicorIcons.Pause else MusicorIcons.Play,
                                contentDescription = if (player.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp),
                            )
                        }
                    }
                    IconButton(onClick = { player.stop() }) {
                        Icon(
                            imageVector = MusicorIcons.Close,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { player.next() }) {
                        Icon(
                            imageVector = MusicorIcons.Next,
                            contentDescription = "Next track",
                        )
                    }
                    // Loop: a filled circle like the play button when active,
                    // a plain gray glyph when off, so the state is unmistakable.
                    IconButton(onClick = { player.loopEnabled = !player.loopEnabled }) {
                        if (player.loopEnabled) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Icon(
                                    imageVector = MusicorIcons.Repeat,
                                    contentDescription = "Loop song on",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(20.dp),
                                )
                            }
                        } else {
                            Icon(
                                imageVector = MusicorIcons.Repeat,
                                contentDescription = "Loop song off",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // Keep the bar clear of the navigation bar / home indicator on phones.
            Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
        }
    }
}

@Composable
private fun SeekBar(player: PlayerControllerHolder) {
    val duration = player.durationMillis
    if (duration <= 0L) return

    // -1 means "not interacting"; otherwise the bar shows the user's scrub
    // position instead of live playback so it feels responsive while dragging.
    var scrub by remember { mutableStateOf(-1f) }
    val shownFraction = if (scrub >= 0f) scrub
        else (player.positionMillis.toFloat() / duration).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatTime((shownFraction * duration).toLong()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        YoutubeSeekBar(
            fraction = shownFraction,
            modifier = Modifier.weight(1f),
            onScrub = { scrub = it },
            onCompleted = {
                val target = (scrub.coerceAtLeast(0f) * duration).toLong()
                scrub = -1f
                player.seekTo(target)
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatTime(duration),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A thin, rounded YouTube-style progress bar. The track is a subtle line that
 * fills with the primary color up to [fraction]; hovering it thickens the
 * line and reveals a draggable thumb, and dragging/tapping scrubs to a new
 * position. While scrubbing, [onScrub] reports the preview fraction and
 * [onCompleted] fires once with the chosen position when the gesture ends.
 */
@Composable
private fun YoutubeSeekBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    onScrub: (Float) -> Unit = {},
    onCompleted: () -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var barWidth by remember { mutableStateOf(0) }

    // A thin 4dp line grows to 8dp on hover, and the thumb fades in.
    val trackHeight = if (hovered) 8.dp else 4.dp
    val thumbVisible = hovered

    Box(
        modifier = modifier
            .height(20.dp)
            .hoverable(interactionSource)
            .onSizeChanged { barWidth = it.width }
            .pointerInput(Unit) {
                detectTapGestures { pos -> onScrub(fractionAt(pos.x, barWidth)); onCompleted() }
            }
            .pointerInput(barWidth) {
                detectDragGestures(
                    onDragStart = { pos -> onScrub(fractionAt(pos.x, barWidth)) },
                    onDrag = { change, _ ->
                        change.consume()
                        onScrub(fractionAt(change.position.x, barWidth))
                    },
                    onDragEnd = { onCompleted() },
                    onDragCancel = { onCompleted() },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(onSurfaceVariant.copy(alpha = 0.25f)),
        )
        // Filled portion
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(primary, primary.copy(alpha = 0.65f)))),
        )
        // Thumb appears on hover/drag (dragging keeps the pointer over the bar).
        if (thumbVisible) {
            val thumbX = (fraction.coerceIn(0f, 1f) * barWidth)
            Box(
                Modifier
                    .offset { IntOffset((thumbX - 6).roundToInt(), 0) }
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(primary),
            )
        }
    }
}

private fun fractionAt(x: Float, width: Int): Float =
    if (width > 0) (x / width).coerceIn(0f, 1f) else 0f

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val paddedSeconds = seconds.toString().padStart(2, '0')
    return "$minutes:$paddedSeconds"
}
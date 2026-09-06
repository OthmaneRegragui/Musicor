package com.regtho.musicor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CategoryScreen(
    category: Category,
    controller: LibraryController,
    player: PlayerControllerHolder,
    scope: CoroutineScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingSong by remember { mutableStateOf<Song?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MusicorIcons.Back,
                    contentDescription = "Back to library",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${category.songs.size} track${if (category.songs.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (controller.isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = { scope.launch { controller.scanCategory(category.id) } }) {
                Icon(
                    imageVector = MusicorIcons.Refresh,
                    contentDescription = "Rescan folder",
                )
            }
        }

        if (category.songs.isEmpty()) {
            EmptyHint(
                title = if (controller.isScanning) "Scanning folder..." else "No music found",
                message = if (controller.isScanning) {
                    "Reading audio files and their tags."
                } else {
                    "No playable audio files were found in this folder.\n" +
                        "Add music files, then rescan."
                },
                modifier = Modifier.padding(top = 48.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(category.songs, key = { it.path }) { song ->
                    SongRow(
                        song = song,
                        controller = controller,
                        isCurrent = player.currentPath == song.path,
                        isPlaying = player.currentPath == song.path && player.isPlaying,
                        onEdit = { editingSong = song },
                        onPlay = { player.play(song, category.songs) },
                    )
                }
            }
        }
    }

    editingSong?.let { song ->
        SongEditDialog(
            song = song,
            categoryId = category.id,
            controller = controller,
            onDismiss = { editingSong = null },
        )
    }
}

@Composable
private fun SongRow(
    song: Song,
    controller: LibraryController,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onEdit: () -> Unit,
    onPlay: () -> Unit,
) {
    val container = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container)
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArt(song = song, controller = controller, size = 44.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                if (song.artistFound || song.releaseDateFound) {
                    buildString {
                        append(if (song.artistFound) song.artist else "Artist not found")
                        append("  ·  ")
                        append(if (song.releaseDateFound) "Released ${song.releaseDate}" else "No release date")
                    }
                } else {
                    "Artist not found  ·  No release date"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onPlay) {
            Icon(
                imageVector = if (isPlaying) MusicorIcons.Pause else MusicorIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .clickable(onClick = onEdit),
        ) {
            Icon(
                imageVector = MusicorIcons.Edit,
                contentDescription = "Edit track info",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(5.dp)
                    .align(Alignment.Center),
            )
        }
    }
}
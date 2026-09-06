package com.regtho.musicor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SongEditDialog(
    song: Song,
    categoryId: String,
    controller: LibraryController,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(song.title ?: "") }
    var artist by remember { mutableStateOf(song.artist ?: "") }
    var releaseDate by remember { mutableStateOf(song.releaseDate ?: "") }
    var stagedImage by remember { mutableStateOf<PickedImage?>(null) }
    var removeArt by remember { mutableStateOf(false) }

    val pickImage = rememberImagePicker { picked ->
        if (picked != null) {
            stagedImage = picked
            removeArt = false
        }
    }

    val stagedBitmap = stagedImage?.let { picked ->
        remember(picked) { decodeImage(picked.bytes) }
    }
    val currentBitmap = remember(song.imageRef, removeArt) {
        if (removeArt) null
        else song.imageRef?.let { controller.readArt(it) }?.let { decodeImage(it) }
    }
    val displayBitmap = stagedBitmap ?: currentBitmap

    val hasChanges = title.isNotBlank() || artist.isNotBlank() || releaseDate.isNotBlank() ||
        stagedImage != null || removeArt

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit track") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Info below was auto-filled from the music file when available. " +
                        "Missing values stay \"Not found\" until you fill them in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (displayBitmap != null) {
                            Image(
                                bitmap = displayBitmap,
                                contentDescription = "Cover art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = MusicorIcons.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Not found",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = pickImage,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (displayBitmap != null) "Change image" else "Pick image")
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                removeArt = true
                                stagedImage = null
                            },
                            enabled = !removeArt && (stagedImage != null || song.imageRef != null),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Remove image")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    placeholder = { Text("Not found — fill to set") },
                    supportingText = {
                        Text(
                            if (song.titleFound) "Auto-filled from the file" else "Not found in the file",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    placeholder = { Text("Not found — fill to set") },
                    supportingText = {
                        Text(
                            if (song.artistFound) "Auto-filled from the file" else "Not found in the file",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = releaseDate,
                    onValueChange = { releaseDate = it },
                    label = { Text("Release date") },
                    placeholder = { Text("Not found — fill to set") },
                    supportingText = {
                        Text(
                            if (song.releaseDateFound) "Auto-filled from the file" else "Not found in the file",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Empty fields keep their \"Not found\" state and fall back to the file name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    controller.updateSongInfo(categoryId, song.path, title, artist, releaseDate)
                    if (removeArt) {
                        controller.removeSongArt(categoryId, song.path)
                    }
                    stagedImage?.let { controller.setSongArt(categoryId, song.path, it.bytes) }
                    onDismiss()
                },
                enabled = hasChanges,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
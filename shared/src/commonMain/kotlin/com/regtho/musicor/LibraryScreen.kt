package com.regtho.musicor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    controller: LibraryController,
    player: PlayerControllerHolder,
    scope: CoroutineScope,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingFolder by remember { mutableStateOf<PickedFolder?>(null) }
    var renameTarget by remember { mutableStateOf<Category?>(null) }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }
    val pickFolder = rememberFolderPicker { picked ->
        if (picked != null) pendingFolder = picked
    }

    val categories = controller.categories

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Library",
            subtitle = if (categories.isEmpty()) {
                "Select music folders to get started"
            } else {
                "${categories.size} categor${if (categories.size == 1) "y" else "ies"}"
            },
            showProgress = controller.isScanning,
            trailing = {
                FilledTonalIconButton(onClick = pickFolder) {
                    Icon(
                        imageVector = MusicorIcons.Add,
                        contentDescription = "Add library",
                    )
                }
            },
        )

        if (categories.isEmpty()) {
            EmptyLibrary(onAddLibrary = pickFolder)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryCard(
                        category = category,
                        controller = controller,
                        player = player,
                        onOpen = { onOpenCategory(category.id) },
                        onRename = { renameTarget = category },
                        onDelete = { deleteTarget = category },
                    )
                }
            }
        }

        AddCategoryButton(
            onClick = pickFolder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }

    pendingFolder?.let { folder ->
        CategoryNameDialog(
            title = "New category",
            confirmLabel = "Add category",
            hint = "Folder selected. Pick a name for this category, or keep the suggested one.",
            initialName = folder.displayName ?: "",
            onConfirm = { name ->
                val id = controller.addCategory(folder, name)
                if (id != null) {
                    scope.launch { controller.scanCategory(id) }
                }
                pendingFolder = null
            },
            onDismiss = { pendingFolder = null },
        )
    }

    renameTarget?.let { category ->
        CategoryNameDialog(
            title = "Rename category",
            confirmLabel = "Rename",
            hint = "Give this category a new name.",
            initialName = category.name,
            onConfirm = { name ->
                controller.updateCategoryName(category.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete category?") },
            text = {
                Text(
                    "This removes \"${category.name}\" and its ${category.songs.size} " +
                        "tracks from your library. The files on disk are not deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.deleteCategory(category.id)
                        if (!controller.isPathInLibrary(player.currentPath.orEmpty())) {
                            player.stop()
                        }
                        deleteTarget = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    controller: LibraryController,
    player: PlayerControllerHolder,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MusicorIcons.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${category.songs.size} track${if (category.songs.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            category.songs.firstOrNull { it.imageRef != null }?.let { artSong ->
                SongArt(song = artSong, controller = controller, size = 40.dp)
                Spacer(Modifier.width(8.dp))
            }
            IconButton(
                onClick = {
                    category.songs.firstOrNull()?.let { first ->
                        player.play(first, category.songs)
                    }
                },
            ) {
                Icon(
                    imageVector = MusicorIcons.Play,
                    contentDescription = "Play ${category.name}",
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = MusicorIcons.Edit,
                    contentDescription = "Rename ${category.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MusicorIcons.Trash,
                    contentDescription = "Delete ${category.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onAddLibrary: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = MusicorIcons.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text("Your library is empty", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(8.dp))
        Text(
            "Select one or more music folders. Each folder becomes a category " +
                "that you can name, rename, scan and play.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(20.dp))
        Button(onClick = onAddLibrary) {
            Icon(
                imageVector = MusicorIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Add library")
        }
    }
}

@Composable
private fun AddCategoryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = MusicorIcons.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Add music folder")
    }
}
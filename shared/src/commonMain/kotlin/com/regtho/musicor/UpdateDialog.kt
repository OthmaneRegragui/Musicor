package com.regtho.musicor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Download progress shown in the update dialog. */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : UpdateDownloadState
    data object Opening : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

@Composable
fun UpdateDialog(
    update: UpdateInfo,
    state: UpdateDownloadState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val busy = state is UpdateDownloadState.Downloading

    val body = when (state) {
        is UpdateDownloadState.Failed -> "Download failed: ${state.message}"
        is UpdateDownloadState.Opening -> "Downloaded. Opening it for you..."
        is UpdateDownloadState.Downloading -> {
            val total = state.totalBytes
            val percent = if (total != null && total > 0) {
                (state.downloadedBytes * 100 / total).toInt()
            } else {
                null
            }
            if (percent != null) "Downloading... $percent%" else "Downloading..."
        }
        UpdateDownloadState.Idle ->
            buildString {
                append("Musicor ${update.version} is available.")
                if (update.asset != null) {
                    append(" ${update.asset.fileName}, ")
                    append(if (update.asset.sizeBytes != null) "${formatBytes(update.asset.sizeBytes)}." else "size unknown.")
                } else {
                    append(" No package for this platform in the release.")
                }
            }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Update available") },
        text = {
            Column {
                Text(body)
                if (state is UpdateDownloadState.Downloading) {
                    val total = state.totalBytes
                    LinearProgressIndicator(
                        progress = { if (total != null && total > 0) state.downloadedBytes.toFloat() / total.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 6.dp),
                    )
                }
                val notes = update.notes
                if (state is UpdateDownloadState.Idle && !notes.isNullOrBlank()) {
                    Text(
                        notes.trim(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            when {
                busy || state is UpdateDownloadState.Opening -> {}
                update.asset == null -> {}
                else -> Button(onClick = onDownload) {
                    Text(if (state is UpdateDownloadState.Failed) "Retry" else "Download")
                }
            }
        },
        dismissButton = {
            if (!busy) TextButton(onClick = onDismiss) { Text("Later") }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024 * 1024 -> {
            val v = bytes * 10 / (1024L * 1024 * 1024)
            "${v / 10}.${v % 10} GB"
        }
        bytes >= 1024L * 1024 -> {
            val v = bytes * 10 / (1024L * 1024)
            "${v / 10}.${v % 10} MB"
        }
        bytes >= 1024L -> {
            val v = bytes * 10 / 1024L
            "${v / 10}.${v % 10} KB"
        }
        else -> "$bytes B"
    }
}
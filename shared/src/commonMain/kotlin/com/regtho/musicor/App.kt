package com.regtho.musicor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Composable
fun App(player: PlayerControllerHolder? = null) {
    AppTheme {
        val controller = remember { LibraryController() }
        val player = remember(player) { player ?: PlayerControllerHolder() }
        val scope = rememberCoroutineScope()
        var selectedCategoryId by remember { mutableStateOf<String?>(null) }

        // Reload the last session without auto-playing; pressing play resumes
        // from where the previous track left off.
        LaunchedEffect(Unit) {
            player.restoreLastSession { controller.findSongByPath(it) }
        }

        // Keep the last session refreshed: immediately on track changes and
        // every few seconds while a track is loaded, plus one final write
        // when the app shuts down.
        DisposableEffect(Unit) {
            onDispose { player.savePlaybackState() }
        }
        LaunchedEffect(player.currentPath) {
            while (player.currentPath != null) {
                player.savePlaybackState()
                delay(3_000)
            }
        }

        PlayerKeyboardShortcuts(player = player) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = MusicorIcons.Logo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Musicor",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                bottomBar = {
                    PlayerBar(
                        player = player,
                        controller = controller,
                        onOpenSong = {
                            val path = player.currentPath
                            if (path != null) {
                                val category = controller.categoryForPath(path)
                                if (category != null) {
                                    selectedCategoryId = category.id
                                }
                            }
                        },
                    )
                },
            ) { innerPadding ->
                val category = selectedCategoryId?.let { id -> controller.categoryById(id) }
                if (category != null) {
                    CategoryScreen(
                        category = category,
                        controller = controller,
                        player = player,
                        scope = scope,
                        onBack = { selectedCategoryId = null },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                } else {
                    LibraryScreen(
                        controller = controller,
                        player = player,
                        scope = scope,
                        onOpenCategory = { selectedCategoryId = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}

/**
 * Global player hotkeys: Space toggles play/pause, N goes to the next track,
 * P goes back to the previous one, and the left/right arrows seek by 10
 * seconds (hold the key to keep scrubbing). Held modifier keys and repeated
 * key events are ignored; the root is focusable and click-capturing so the
 * hotkeys keep working after the window or any control loses focus. Modal
 * dialogs run in their own window and keep their own keyboard input.
 */
@Composable
private fun PlayerKeyboardShortcuts(
    player: PlayerControllerHolder,
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {}
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
                    return@onPreviewKeyEvent false
                }
                val action: (() -> Unit)? = when (event.key) {
                    Key.Spacebar -> { { player.togglePlayPause() } }
                    Key.N -> { { player.next() } }
                    Key.P -> { { player.previous() } }
                    Key.DirectionLeft -> { { player.seekBy(-10_000L) } }
                    Key.DirectionRight -> { { player.seekBy(10_000L) } }
                    else -> null
                }
                if (action == null) return@onPreviewKeyEvent false
                // Arrows scrub like a YouTube bar: a low throttle means holding
                // the key sweeps through the track smoothly (OS auto-repeat
                // retriggers each ~70ms instead of being dropped). Other
                // commands use a slightly longer debounce.
                val isScrub = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                val throttleMs = if (isScrub) 70L else 220L
                val heldFor = lastShortcutHandled[event.key]?.elapsedNow()?.inWholeMilliseconds ?: Long.MAX_VALUE
                if (heldFor in 0L until throttleMs) return@onPreviewKeyEvent true
                lastShortcutHandled[event.key] = TimeSource.Monotonic.markNow()
                action()
                true
            },
    ) {
        content()
    }
}

private val lastShortcutHandled = mutableMapOf<Key, TimeMark>()
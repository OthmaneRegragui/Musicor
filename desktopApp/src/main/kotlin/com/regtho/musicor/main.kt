package com.regtho.musicor

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1200.dp, 800.dp),
        position = WindowPosition(Alignment.Center),
    )
    val player = remember { PlayerControllerHolder() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Musicor",
        state = windowState,
    ) {
        // Keep the window from being resized too small to be usable.
        window.minimumSize = java.awt.Dimension(900, 600)
        DisposableEffect(Unit) {
            MprisService.start(player)
            onDispose { MprisService.stop() }
        }
        App(player = player)
    }
}
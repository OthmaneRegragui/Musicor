package com.regtho.musicor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    // Deep link from the playback notification: (track path, nonce). The
    // nonce changes on every click so the app re-navigates even when the
    // same song is tapped twice.
    private var openTrackRequest by mutableStateOf<Pair<String, Long>?>(null)
    private var openTrackNonce = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark; keep the status bar and navigation bar icons
        // light (white) so they stay readable against the black background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        AndroidAppContext.appContext = applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        readDeepLink(intent)
        setContent {
            App(openTrackRequest = openTrackRequest)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDeepLink(intent)
    }

    private fun readDeepLink(intent: Intent?) {
        val path = intent?.getStringExtra(MediaPlaybackService.EXTRA_OPEN_TRACK)
        if (!path.isNullOrBlank()) {
            openTrackNonce += 1
            openTrackRequest = path to openTrackNonce
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
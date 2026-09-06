package com.regtho.musicor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun vector(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) { block() }
    }.build()

/** Hand-drawn icon set used by the app (black/gray material-style glyphs). */
object MusicorIcons {

    val Play: ImageVector by lazy {
        vector("play") {
            moveTo(7f, 5f)
            lineTo(19f, 12f)
            lineTo(7f, 19f)
            close()
        }
    }

    val Pause: ImageVector by lazy {
        vector("pause") {
            moveTo(6f, 5f)
            lineTo(10f, 5f)
            lineTo(10f, 19f)
            lineTo(6f, 19f)
            close()
            moveTo(14f, 5f)
            lineTo(18f, 5f)
            lineTo(18f, 19f)
            lineTo(14f, 19f)
            close()
        }
    }

    val Next: ImageVector by lazy {
        vector("next") {
            moveTo(5f, 5f)
            lineTo(7f, 5f)
            lineTo(7f, 19f)
            lineTo(5f, 19f)
            close()
            moveTo(9f, 5f)
            lineTo(19f, 12f)
            lineTo(9f, 19f)
            close()
        }
    }

    val Previous: ImageVector by lazy {
        vector("previous") {
            moveTo(17f, 5f)
            lineTo(19f, 5f)
            lineTo(19f, 19f)
            lineTo(17f, 19f)
            close()
            moveTo(15f, 5f)
            lineTo(5f, 12f)
            lineTo(15f, 19f)
            close()
        }
    }

    val Folder: ImageVector by lazy {
        vector("folder") {
            moveTo(10f, 4f)
            lineTo(4f, 4f)
            curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
            lineTo(20f, 20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            lineTo(22f, 8f)
            curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
            lineTo(12f, 6f)
            lineTo(10f, 4f)
            close()
        }
    }

    val Add: ImageVector by lazy {
        vector("add") {
            moveTo(11f, 5f)
            lineTo(13f, 5f)
            lineTo(13f, 11f)
            lineTo(19f, 11f)
            lineTo(19f, 13f)
            lineTo(13f, 13f)
            lineTo(13f, 19f)
            lineTo(11f, 19f)
            lineTo(11f, 13f)
            lineTo(5f, 13f)
            lineTo(5f, 11f)
            lineTo(11f, 11f)
            close()
        }
    }

    val Back: ImageVector by lazy {
        vector("back") {
            moveTo(20f, 11f)
            lineTo(7.83f, 11f)
            lineTo(13.42f, 5.41f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(13.42f, 18.59f)
            lineTo(7.83f, 13f)
            lineTo(20f, 13f)
            close()
        }
    }

    val Close: ImageVector by lazy {
        vector("close") {
            moveTo(19f, 6.41f)
            lineTo(17.59f, 5f)
            lineTo(12f, 10.59f)
            lineTo(6.41f, 5f)
            lineTo(5f, 6.41f)
            lineTo(10.59f, 12f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(12f, 13.41f)
            lineTo(17.59f, 19f)
            lineTo(19f, 17.59f)
            lineTo(13.41f, 12f)
            close()
        }
    }

    val Refresh: ImageVector by lazy {
        vector("refresh") {
            moveTo(17.65f, 6.35f)
            curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
            curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
            curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
            curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
            lineTo(17.65f, 14f)
            curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
            curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
            lineTo(13f, 11f)
            lineTo(20f, 11f)
            lineTo(20f, 4f)
            lineTo(17.65f, 6.35f)
            close()
        }
    }

    val Trash: ImageVector by lazy {
        vector("trash") {
            moveTo(6f, 19f)
            curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
            lineTo(16f, 21f)
            curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
            lineTo(18f, 7f)
            lineTo(6f, 7f)
            close()
            moveTo(19f, 4f)
            lineTo(15.5f, 4f)
            lineTo(14.5f, 3f)
            lineTo(9.5f, 3f)
            lineTo(8.5f, 4f)
            lineTo(5f, 4f)
            lineTo(5f, 6f)
            lineTo(19f, 6f)
            close()
        }
    }

    val MusicNote: ImageVector by lazy {
        vector("music_note") {
            moveTo(12f, 3f)
            lineTo(12f, 13.55f)
            curveTo(11.41f, 13.21f, 10.73f, 13f, 10f, 13f)
            curveTo(7.79f, 13f, 6f, 14.79f, 6f, 17f)
            curveTo(6f, 19.21f, 7.79f, 21f, 10f, 21f)
            curveTo(12.21f, 21f, 14f, 19.21f, 14f, 17f)
            lineTo(14f, 7f)
            lineTo(18f, 7f)
            lineTo(18f, 3f)
            close()
        }
    }

    val Repeat: ImageVector by lazy {
        vector("repeat") {
            moveTo(7f, 7f)
            lineTo(17f, 7f)
            lineTo(17f, 10f)
            lineTo(21f, 6f)
            lineTo(17f, 2f)
            lineTo(17f, 5f)
            lineTo(5f, 5f)
            lineTo(5f, 11f)
            lineTo(7f, 11f)
            lineTo(7f, 7f)
            close()
            moveTo(17f, 17f)
            lineTo(7f, 17f)
            lineTo(7f, 14f)
            lineTo(3f, 18f)
            lineTo(7f, 22f)
            lineTo(7f, 19f)
            lineTo(19f, 19f)
            lineTo(19f, 13f)
            lineTo(17f, 13f)
            lineTo(17f, 17f)
            close()
        }
    }

    val Edit: ImageVector by lazy {
        vector("edit") {
            moveTo(3f, 17.25f)
            lineTo(3f, 21f)
            lineTo(6.75f, 21f)
            lineTo(17.81f, 9.94f)
            lineTo(14.06f, 6.19f)
            lineTo(3f, 17.25f)
            close()
            moveTo(20.71f, 7.04f)
            curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
            lineTo(18.37f, 3.29f)
            curveTo(17.67f, 2.9f, 18.37f, 2.9f, 16.96f, 3.29f)
            lineTo(15.13f, 5.12f)
            lineTo(18.88f, 8.87f)
            lineTo(20.71f, 7.04f)
            close()
        }
    }

    val Logo: ImageVector by lazy {
        ImageVector.Builder(
            name = "logo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
            ) {
                moveTo(12f, 2.5f)
                curveTo(17.25f, 2.5f, 21.5f, 6.75f, 21.5f, 12f)
                curveTo(21.5f, 17.25f, 17.25f, 21.5f, 12f, 21.5f)
                curveTo(6.75f, 21.5f, 2.5f, 17.25f, 2.5f, 12f)
                curveTo(2.5f, 6.75f, 6.75f, 2.5f, 12f, 2.5f)
                close()
                moveTo(10f, 8f)
                lineTo(16.5f, 12f)
                lineTo(10f, 16f)
                close()
            }
        }.build()
    }
}
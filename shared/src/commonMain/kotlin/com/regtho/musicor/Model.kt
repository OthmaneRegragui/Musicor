package com.regtho.musicor

import kotlinx.serialization.Serializable

@Serializable
data class Library(
    val categories: List<Category> = emptyList(),
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val folderUri: String,
    val songs: List<Song> = emptyList(),
)

@Serializable
data class Song(
    val path: String,
    val fileName: String,
    val title: String? = null,
    val artist: String? = null,
    val releaseDate: String? = null,
    val imageRef: String? = null,
    val artSource: ArtSource = ArtSource.NONE,
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: fileName

    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() } ?: "Unknown artist"

    val titleFound: Boolean
        get() = title?.isNotBlank() == true

    val artistFound: Boolean
        get() = artist?.isNotBlank() == true

    val releaseDateFound: Boolean
        get() = releaseDate?.isNotBlank() == true
}

@Serializable
enum class ArtSource {
    NONE,
    EMBEDDED,
    CUSTOM,
}

/** Metadata extracted from the music file itself (ID3 tags and embedded art). */
data class SongMetadata(
    val title: String? = null,
    val artist: String? = null,
    val releaseDate: String? = null,
    val artBytes: ByteArray? = null,
)

/** One playable file found when scanning a folder. */
data class ScannedSong(
    val path: String,
    val displayName: String,
    val metadata: SongMetadata?,
)

/** Folder selected by the user (becomes a category). */
data class PickedFolder(
    val uri: String,
    val displayName: String?,
)

/** Image selected by the user (cover art). */
data class PickedImage(
    val bytes: ByteArray,
    val displayName: String?,
)

/** One item handed to the platform player (queue entry + display info for the notification). */
data class PlayableItem(
    val path: String,
    val title: String = "",
    val artist: String = "",
    val artRef: String? = null,
)
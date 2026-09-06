package com.regtho.musicor

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicorLogicTest {

    @Test
    fun scanExtractsTagsAndPersistsRoundTrip() = runBlocking {
        val root = kotlin.io.path.createTempDirectory("musicor-test").toFile()
        try {
            val storage = JvmStorage(root)
            val controller = LibraryController(storage)

            val picked = PickedFolder(uri = MUSIC_DIR.absolutePath, displayName = "Test Music")
            val id = controller.addCategory(picked, "My Category")
            assertNotNull(id)

            controller.scanCategory(id)

            val category = controller.categoryById(id)
            assertNotNull(category, "category should exist after scan")
            assertEquals("My Category", category.name)

            // At least the tagged MP3 and the plain MP3 must be found (wav too, if present).
            val tagged = category.songs.firstOrNull { it.fileName == "tagged_track.mp3" }
            assertNotNull(tagged, "tagged_track.mp3 should be scanned")
            assertEquals("Hello World", tagged.title, "title should be extracted from ID3 tag")
            assertEquals("Test Artist", tagged.artist, "artist should be extracted from ID3 tag")
            assertEquals("2015", tagged.releaseDate, "year should be extracted from ID3 tag")
            assertTrue(tagged.imageRef != null && storage.readArt(tagged.imageRef) != null,
                "embedded cover art should be extracted and stored")

            val plain = category.songs.firstOrNull { it.fileName == "plain_tone.mp3" }
            assertNotNull(plain, "plain_tone.mp3 should be scanned")
            assertNull(plain.title, "untagged file should have no title")
            assertNull(plain.artist, "untagged file should have no artist")
            assertNull(plain.imageRef, "untagged file should have no art")
            assertEquals("plain_tone.mp3", plain.displayTitle, "title falls back to file name")
            assertEquals("Unknown artist", plain.displayArtist)

            // Manual metadata fill-in for the untagged track.
            controller.updateSongInfo(id, plain.path, "My Name", "Some Artist", "2020")
            val updated = controller.categoryById(id)!!.songs.first { it.path == plain.path }
            assertEquals("My Name", updated.title)
            assertEquals("Some Artist", updated.artist)
            assertEquals("2020", updated.releaseDate)

            // Custom art can be applied, then removed.
            controller.setSongArt(id, plain.path, COVER_BYTES)
            var withArt = controller.categoryById(id)!!.songs.first { it.path == plain.path }
            assertEquals(ArtSource.CUSTOM, withArt.artSource)
            assertNotNull(withArt.imageRef)
            assertNotNull(controller.readArt(withArt.imageRef))

            controller.removeSongArt(id, plain.path)
            withArt = controller.categoryById(id)!!.songs.first { it.path == plain.path }
            assertEquals(ArtSource.NONE, withArt.artSource)
            assertNull(withArt.imageRef)

            // Rename + persistence round trip.
            controller.updateCategoryName(id, "Renamed")
            val reloaded = LibraryController(storage)
            val reloadedCategory = reloaded.categoryById(id)
            assertNotNull(reloadedCategory)
            assertEquals("Renamed", reloadedCategory.name)
            assertEquals(3, reloadedCategory.songs.size)
            assertTrue(
                reloadedCategory.songs.any { it.title == "Hello World" },
                "extracted tags should survive a reload",
            )
            assertTrue(
                reloadedCategory.songs.any { it.fileName == "plain_tone.mp3" && it.title == "My Name" },
                "manually filled info should survive a reload",
            )

            // Deleting a category clears its art files.
            val refToCheck = reloadedCategory.songs.firstOrNull { it.title == "Hello World" }?.imageRef
            assertNotNull(refToCheck)
            reloaded.deleteCategory(id)
            assertTrue(reloaded.categories.isEmpty())
            assertNull(storage.readArt(refToCheck), "art file should be deleted with the category")
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        val MUSIC_DIR: java.io.File =
            checkNotNull(MusicorLogicTest::class.java.classLoader.getResource("music")) {
                "missing jvmTest resources/music fixtures"
            }.toURI().let { java.io.File(it) }
        val COVER_BYTES: ByteArray = ByteArray(128) { (it * 2).toByte() }
    }
}
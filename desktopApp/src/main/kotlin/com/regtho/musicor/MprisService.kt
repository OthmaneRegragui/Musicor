package com.regtho.musicor

import org.freedesktop.dbus.ObjectPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.annotations.DBusProperty.Access
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.Message
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Publishes an MPRIS2 service on the session bus so GNOME/KDE show media
 * controls in the notification shade / quick settings.  Degrades silently
 * when no session bus is available (e.g. headless CI).
 */
object MprisService {

    private var connection: DBusConnection? = null
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "musicor-mpris").also { it.isDaemon = true }
    }
    private var lastEmittedUs = -1L

    fun start(player: PlayerControllerHolder) {
        try {
            val conn = DBusConnectionBuilder.forSessionBus().build()
            conn.requestBusName("org.mpris.MediaPlayer2.musicor")
            conn.exportObject("/org/mpris/MediaPlayer2", RootImpl())
            conn.exportObject("/org/mpris/MediaPlayer2/Player", PlayerImpl(player))
            connection = conn

            executor.scheduleAtFixedRate({
                try { emitPosition(player) } catch (_: Exception) {}
            }, 1, 1, TimeUnit.SECONDS)

            player.addStateListener {
                try { emitFullUpdate(player) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            System.err.println("Musicor: MPRIS init failed — ${e.message}")
        }
    }

    fun stop() {
        executor.shutdownNow()
        runCatching { connection?.disconnect() }
        connection = null
    }

    // ───────────────────────────────────────────────────────────────────
    // org.mpris.MediaPlayer2  (root object)
    // ───────────────────────────────────────────────────────────────────

    @DBusInterfaceName("org.mpris.MediaPlayer2")
    @DBusProperty(name = "Identity", type = String::class, access = Access.READ)
    @DBusProperty(name = "DesktopEntry", type = String::class, access = Access.READ)
    @DBusProperty(name = "SupportedUriSchemes", type = Array<String>::class, access = Access.READ)
    @DBusProperty(name = "SupportedMimeTypes", type = Array<String>::class, access = Access.READ)
    @DBusProperty(name = "CanRaise", type = Boolean::class, access = Access.READ)
    @DBusProperty(name = "CanQuit", type = Boolean::class, access = Access.READ)
    @DBusProperty(name = "HasTrackList", type = Boolean::class, access = Access.READ)
    class RootImpl : DBusInterface, Properties {

        fun Raise() {}
        fun Quit() {}

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(interfaceName: String, propertyName: String): A =
            (properties()[propertyName]
                ?: throw DBusException("No such property: $interfaceName.$propertyName"))
                as A

        override fun <A> Set(interfaceName: String, propertyName: String, value: A) {
            throw DBusException("Property $interfaceName.$propertyName is read-only")
        }

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties()

        override fun getObjectPath(): String = "/org/mpris/MediaPlayer2"

        private fun properties(): Map<String, Variant<*>> = mapOf(
            "Identity" to Variant("Musicor"),
            "DesktopEntry" to Variant("musicor"),
            "SupportedUriSchemes" to Variant(arrayOf("file")),
            "SupportedMimeTypes" to Variant(arrayOf("audio/*")),
            "CanRaise" to Variant(false),
            "CanQuit" to Variant(false),
            "HasTrackList" to Variant(false),
        )
    }

    // ───────────────────────────────────────────────────────────────────
    // org.mpris.MediaPlayer2.Player
    // ───────────────────────────────────────────────────────────────────

    @DBusInterfaceName("org.mpris.MediaPlayer2.Player")
    @DBusProperty(name = "PlaybackStatus", type = String::class, access = Access.READ)
    @DBusProperty(name = "LoopStatus", type = String::class, access = Access.READ_WRITE)
    @DBusProperty(name = "Rate", type = Double::class, access = Access.READ_WRITE)
    @DBusProperty(name = "Shuffle", type = Boolean::class, access = Access.READ_WRITE)
    @DBusProperty(name = "Metadata", type = Map::class, access = Access.READ)
    @DBusProperty(name = "Volume", type = Double::class, access = Access.READ_WRITE)
    @DBusProperty(name = "Position", type = Long::class, access = Access.READ)
    @DBusProperty(name = "MinimumRate", type = Double::class, access = Access.READ)
    @DBusProperty(name = "MaximumRate", type = Double::class, access = Access.READ)
    @DBusProperty(name = "CanGoNext", type = Boolean::class, access = Access.READ)
    @DBusProperty(name = "CanGoPrevious", type = Boolean::class, access = Access.READ)
    @DBusProperty(name = "CanPlay", type = Boolean::class, access = Access.READ)
    @DBusProperty(name = "CanPause", type = Boolean::class, access = Access.READ)
    @DBusProperty(name = "CanSeek", type = Boolean::class, access = Access.READ)
    @DBusProperty(name = "CanControl", type = Boolean::class, access = Access.READ)
    class PlayerImpl(private val player: PlayerControllerHolder) : DBusInterface, Properties {

        fun Play() { player.togglePlayPause() }
        fun Pause() { player.togglePlayPause() }
        fun PlayPause() { player.togglePlayPause() }
        fun Stop() { player.stop() }
        fun Next() { player.next() }
        fun Previous() { player.previous() }
        fun Seek(offsetUs: Long) { player.seekBy(offsetUs / 1000) }
        fun SetPosition(trackId: ObjectPath, positionUs: Long) { player.seekTo(positionUs / 1000) }
        fun OpenUri(uri: String) {}

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(interfaceName: String, propertyName: String): A =
            (properties()[propertyName]
                ?: throw DBusException("No such property: $interfaceName.$propertyName"))
                as A

        override fun <A> Set(interfaceName: String, propertyName: String, value: A) {
            when (propertyName) {
                "LoopStatus" -> {
                    player.loopEnabled = when (value as? String) {
                        "Track" -> true
                        "None" -> false
                        else -> throw DBusException("Invalid LoopStatus: $value")
                    }
                }
                "Rate" -> Unit // always 1.0
                "Shuffle" -> Unit // unsupported, accept silently
                "Volume" -> Unit // sink volume is fixed
                else -> throw DBusException("Property $propertyName is read-only")
            }
        }

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties()

        override fun getObjectPath(): String = "/org/mpris/MediaPlayer2/Player"

        private fun properties(): Map<String, Variant<*>> {
            val map = HashMap<String, Variant<*>>()
            map["PlaybackStatus"] = Variant(
                when {
                    player.isPlaying -> "Playing"
                    player.currentPath != null -> "Paused"
                    else -> "Stopped"
                }
            )
            map["LoopStatus"] = Variant(if (player.loopEnabled) "Track" else "None")
            map["Rate"] = Variant(1.0)
            map["Shuffle"] = Variant(false)
            map["Metadata"] = Variant(metadata(), "a{sv}")
            map["Volume"] = Variant(1.0)
            map["Position"] = Variant(player.positionMillis * 1000L)
            map["MinimumRate"] = Variant(1.0)
            map["MaximumRate"] = Variant(1.0)
            map["CanGoNext"] = Variant(true)
            map["CanGoPrevious"] = Variant(true)
            map["CanPlay"] = Variant(true)
            map["CanPause"] = Variant(true)
            map["CanSeek"] = Variant(true)
            map["CanControl"] = Variant(true)
            return map
        }

        private fun metadata(): Map<String, Variant<*>> {
            val map = HashMap<String, Variant<*>>()
            val path = player.currentPath
            if (path != null) {
                map["mpris:trackid"] = Variant(
                    ObjectPath("org.mpris.MediaPlayer2.musicor", "/org/mpris/MediaPlayer2/Track/${path.hashCode()}")
                )
                map["xesam:title"] = Variant(player.currentTitle ?: path.substringAfterLast('/'))
                map["xesam:artist"] = Variant(arrayOf(player.currentArtist ?: "Unknown"))
                if (player.durationMillis > 0) {
                    map["mpris:length"] = Variant(player.durationMillis * 1000L)
                }
                if (path.startsWith("/")) {
                    map["xesam:url"] = Variant("file://$path")
                }
            }
            return map
        }
    }

    private fun send(signal: Message) {
        val conn = connection ?: return
        runCatching { conn.sendMessage(signal) }
    }

    /** Full invalidation — GNOME re-fetches everything it needs. */
    private fun emitFullUpdate(player: PlayerControllerHolder) {
        lastEmittedUs = -1L
        send(Properties.PropertiesChanged(
            "/org/mpris/MediaPlayer2/Player",
            "org.mpris.MediaPlayer2.Player",
            emptyMap(),
            listOf("PlaybackStatus", "LoopStatus", "Metadata", "Position"),
        ))
    }

    /** Emits Position as the playhead advances so the scrubber stays live. */
    private fun emitPosition(player: PlayerControllerHolder) {
        if (!player.isPlaying) return
        val posUs = player.positionMillis * 1000L
        if (posUs == lastEmittedUs) return
        lastEmittedUs = posUs
        send(Properties.PropertiesChanged(
            "/org/mpris/MediaPlayer2/Player",
            "org.mpris.MediaPlayer2.Player",
            mapOf("Position" to Variant(posUs)),
            emptyList(),
        ))
    }
}
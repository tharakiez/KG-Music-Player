
package com.kgmusic.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONArray
import org.json.JSONObject

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        var audioSessionId: Int = 0
            private set

        private const val PREFS = "kg_playback_session"
        private const val QUEUE_KEY = "queue"
        private const val INDEX_KEY = "current_index"
        private const val POSITION_KEY = "position"
        private const val SHUFFLE_KEY = "shuffle"
        private const val REPEAT_KEY = "repeat"
        private const val SPEED_KEY = "speed"
    }

    private val saveTask = object : Runnable {
        override fun run() {
            saveSession()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer

        audioSessionId = exoPlayer.audioSessionId

        restoreSession(exoPlayer)

        exoPlayer.addListener(object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events
            ) {
                saveSession()
            }
        })

        handler.postDelayed(saveTask, 1000)

        val activityIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    private fun restoreSession(exoPlayer: ExoPlayer) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedQueue = prefs.getString(QUEUE_KEY, null) ?: return

        try {
            val array = JSONArray(savedQueue)
            if (array.length() == 0) return

            val items = mutableListOf<MediaItem>()

            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)

                val metadata = MediaMetadata.Builder()
                    .setTitle(entry.optString("title", "Unknown song"))
                    .setArtist(entry.optString("artist", ""))
                    .build()

                items.add(
                    MediaItem.Builder()
                        .setUri(Uri.parse(entry.getString("uri")))
                        .setMediaMetadata(metadata)
                        .build()
                )
            }

            if (items.isEmpty()) return

            val index = prefs.getInt(INDEX_KEY, 0)
                .coerceIn(0, items.lastIndex)

            val position = prefs.getLong(POSITION_KEY, 0L)
                .coerceAtLeast(0L)

            exoPlayer.setMediaItems(items, index, position)
            exoPlayer.shuffleModeEnabled =
                prefs.getBoolean(SHUFFLE_KEY, false)

            exoPlayer.repeatMode = prefs.getInt(
                REPEAT_KEY,
                Player.REPEAT_MODE_OFF
            )

            val speed = prefs.getFloat(SPEED_KEY, 1f)
                .coerceIn(0.25f, 4f)

            exoPlayer.setPlaybackParameters(
                PlaybackParameters(speed, 1f)
            )

            exoPlayer.prepare()

            // Restore the session paused. Never autoplay on reopening.
            exoPlayer.playWhenReady = false

        } catch (_: Exception) {
            // Ignore an invalid saved session.
        }
    }

    private fun saveSession() {
        val currentPlayer = player ?: return
        if (currentPlayer.mediaItemCount == 0) return

        try {
            val array = JSONArray()

            for (i in 0 until currentPlayer.mediaItemCount) {
                val item = currentPlayer.getMediaItemAt(i)

                val uri = item.localConfiguration?.uri ?: continue

                val entry = JSONObject().apply {
                    put("uri", uri.toString())
                    put(
                        "title",
                        item.mediaMetadata.title?.toString()
                            ?: "Unknown song"
                    )
                    put(
                        "artist",
                        item.mediaMetadata.artist?.toString() ?: ""
                    )
                }

                array.put(entry)
            }

            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

            prefs.edit()
                .putString(QUEUE_KEY, array.toString())
                .putInt(
                    INDEX_KEY,
                    currentPlayer.currentMediaItemIndex
                        .coerceAtLeast(0)
                )
                .putLong(
                    POSITION_KEY,
                    currentPlayer.currentPosition.coerceAtLeast(0L)
                )
                .putBoolean(
                    SHUFFLE_KEY,
                    currentPlayer.shuffleModeEnabled
                )
                .putInt(
                    REPEAT_KEY,
                    currentPlayer.repeatMode
                )
                .putFloat(
                    SPEED_KEY,
                    currentPlayer.playbackParameters.speed
                )
                .apply()

        } catch (_: Exception) {
            // Keep the player running if saving fails.
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveSession()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(saveTask)
        saveSession()

        mediaSession?.release()
        mediaSession = null

        player?.release()
        player = null

        audioSessionId = 0

        super.onDestroy()
    }
}


package com.kgmusic.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlaylistSong(
    val title: String,
    val artist: String,
    val uri: String,
    val folderPath: String
)

data class SavedPlaylist(
    val name: String,
    val songs: List<SavedPlaylistSong>
)

object PlaylistStorage {

    private const val PREFS = "kg_music_playlists"
    private const val KEY_PLAYLISTS = "saved_playlists"

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<SavedPlaylist> {
        return try {
            val json = preferences(context)
                .getString(KEY_PLAYLISTS, "[]") ?: "[]"

            val array = JSONArray(json)

            (0 until array.length()).map { i ->
                val playlist = array.getJSONObject(i)
                val songArray = playlist.getJSONArray("songs")

                val songs = (0 until songArray.length()).map { j ->
                    val song = songArray.getJSONObject(j)

                    SavedPlaylistSong(
                        title = song.optString("title"),
                        artist = song.optString("artist"),
                        uri = song.optString("uri"),
                        folderPath = song.optString("folderPath")
                    )
                }

                SavedPlaylist(
                    name = playlist.optString("name"),
                    songs = songs
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, playlists: List<SavedPlaylist>) {
        val array = JSONArray()

        playlists.forEach { playlist ->
            val songArray = JSONArray()

            playlist.songs.forEach { song ->
                songArray.put(
                    JSONObject().apply {
                        put("title", song.title)
                        put("artist", song.artist)
                        put("uri", song.uri)
                        put("folderPath", song.folderPath)
                    }
                )
            }

            array.put(
                JSONObject().apply {
                    put("name", playlist.name)
                    put("songs", songArray)
                }
            )
        }

        preferences(context).edit()
            .putString(KEY_PLAYLISTS, array.toString())
            .apply()
    }

    fun createPlaylist(context: Context, name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return false

        val playlists = load(context)

        if (playlists.any {
                it.name.equals(cleanName, ignoreCase = true)
            }) {
            return false
        }

        save(
            context,
            playlists + SavedPlaylist(cleanName, emptyList())
        )

        return true
    }

    fun addSong(
        context: Context,
        playlistName: String,
        song: SavedPlaylistSong
    ): Boolean {
        val playlists = load(context)
        val index = playlists.indexOfFirst {
            it.name == playlistName
        }

        if (index == -1) return false

        val playlist = playlists[index]

        if (playlist.songs.any { it.uri == song.uri }) {
            return false
        }

        val updated = playlists.toMutableList()
        updated[index] = playlist.copy(
            songs = playlist.songs + song
        )

        save(context, updated)
        return true
    }

    fun removeSong(
        context: Context,
        playlistName: String,
        songUri: String
    ) {
        val updated = load(context).map { playlist ->
            if (playlist.name == playlistName) {
                playlist.copy(
                    songs = playlist.songs.filter {
                        it.uri != songUri
                    }
                )
            } else {
                playlist
            }
        }

        save(context, updated)
    }

    fun deletePlaylist(context: Context, playlistName: String) {
        save(
            context,
            load(context).filter {
                it.name != playlistName
            }
        )
    }
}

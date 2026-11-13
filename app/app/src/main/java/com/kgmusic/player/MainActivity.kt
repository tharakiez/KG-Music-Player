     
package com.kgmusic.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

data class Song(
    val title: String,
    val artist: String,
    val uri: android.net.Uri
)

class MainActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var audioEffects: AudioEffects? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        player = ExoPlayer.Builder(this).build()

        audioEffects = AudioEffects(
            player!!.audioSessionId
        )

        setContent {
            KGMusicApp()
        }
    }

    private fun loadSongs(): List<Song> {
        val songs = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )

        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->

            while (cursor.moveToNext()) {
                val id = cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media._ID
                    )
                )

                val title = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.TITLE
                    )
                ) ?: "Unknown"

                val artist = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.ARTIST
                    )
                ) ?: "Unknown Artist"

                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                songs.add(Song(title, artist, uri))
            }
        }

        return songs
    }

    @Composable
    private fun KGMusicApp() {

        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        var currentSong by remember {
            mutableStateOf("Nothing playing")
        }

        var hasPermission by remember {
            mutableStateOf(false)
        }

        var showEqualizer by remember {
            mutableStateOf(false)
        }

        var shuffle by remember {
            mutableStateOf(false)
        }

        var repeatAll by remember {
            mutableStateOf(false)
        }

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val permissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasPermission = granted
                if (granted) songs = loadSongs()
            }

        LaunchedEffect(Unit) {
            hasPermission = ContextCompat.checkSelfPermission(
                this@MainActivity,
                permission
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                songs = loadSongs()
            }
        }

        val black = Color(0xFF080808)
        val gold = Color(0xFFFFC400)

        MaterialTheme(
            colorScheme = darkColorScheme(
                background = black,
                surface = Color(0xFF171717),
                primary = gold
            )
        ) {

            if (showEqualizer) {

                EqualizerScreen(
                    onBack = {
                        showEqualizer = false
                    },
                    audioEffects = audioEffects
                )

            } else {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(black)
                        .padding(18.dp)
                ) {

                    Text(
                        "🎧 KG",
                        color = gold,
                        fontSize = 38.sp
                    )

                    Text(
                        "MUSIC PLAYER",
                        color = Color.White,
                        fontSize = 20.sp
                    )

                    Spacer(Modifier.height(15.dp))

                    Button(
                        onClick = {
                            showEqualizer = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OPEN SOUND ENGINE • EQ")
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (songs.isNotEmpty()) {
                                player?.apply {
                                    setMediaItems(
                                        songs.map {
                                            MediaItem.fromUri(it.uri)
                                        }
                                    )
                                    shuffleModeEnabled = shuffle
                                    repeatMode =
                                        if (repeatAll) {
                                            Player.REPEAT_MODE_ALL
                                        } else {
                                            Player.REPEAT_MODE_OFF
                                        }
                                    prepare()
                                    play()
                                }

                                currentSong = songs.first().title
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("▶ PLAY ALL SONGS")
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {
                        Text(
                            "MY MUSIC",
                            color = Color.White
                        )

                        Text(
                            "${songs.size} Songs",
                            color = gold
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (!hasPermission) {

                        Button(
                            onClick = {
                                permissionLauncher.launch(permission)
                            }
                        ) {
                            Text("ALLOW MUSIC ACCESS")
                        }

                    } else if (songs.isEmpty()) {

                        Text(
                            "No music found on your phone.",
                            color = Color.LightGray
                        )

                    } else {

                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {

                            items(songs) { song ->

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {

                                            val index =
                                                songs.indexOf(song)

                                            player?.apply {
                                                setMediaItems(
                                                    songs.map {
                                                        MediaItem.fromUri(
                                                            it.uri
                                                        )
                                                    },
                                                    index,
                                                    0L
                                                )
                                                prepare()
                                                play()
                                            }

                                            currentSong = song.title
                                        }
                                        .padding(vertical = 12.dp)
                                ) {

                                    Text(
                                        song.title,
                                        color = Color.White
                                    )

                                    Text(
                                        song.artist,
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }

                                HorizontalDivider(
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Surface(
                        color = Color(0xFF202020),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {

                            Text(
                                "NOW PLAYING",
                                color = gold
                            )

                            Text(
                                currentSong,
                                color = Color.White
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceEvenly,
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                TextButton(
                                    onClick = {
                                        player?.seekToPrevious()
                                    }
                                ) {
                                    Text("PREV", color = gold)
                                }

                                Button(
                                    onClick = {
                                        player?.let {
                                            if (it.isPlaying) {
                                                it.pause()
                                            } else {
                                                it.play()
                                            }
                                        }
                                    }
                                ) {
                                    Text("PLAY / PAUSE")
                                }

                                TextButton(
                                    onClick = {
                                        player?.seekToNext()
                                    }
                                ) {
                                    Text("NEXT", color = gold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceEvenly
                            ) {

                                TextButton(
                                    onClick = {
                                        shuffle = !shuffle
                                        player?.shuffleModeEnabled =
                                            shuffle
                                    }
                                ) {
                                    Text(
                                        if (shuffle) "SHUFFLE ON"
                                        else "SHUFFLE OFF",
                                        color = if (shuffle) gold
                                        else Color.Gray
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        repeatAll = !repeatAll
                                        player?.repeatMode =
                                            if (repeatAll) {
                                                Player.REPEAT_MODE_ALL
                                            } else {
                                                Player.REPEAT_MODE_OFF
                                            }
                                    }
                                ) {
                                    Text(
                                        if (repeatAll) "REPEAT ALL ON"
                                        else "REPEAT ALL OFF",
                                        color = if (repeatAll) gold
                                        else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        audioEffects?.release()
        audioEffects = null

        player?.release()
        player = null

        super.onDestroy()
    }
}

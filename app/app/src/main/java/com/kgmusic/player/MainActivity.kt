
package com.kgmusic.player

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
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
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

data class Song(
    val title: String,
    val artist: String,
    val uri: android.net.Uri
)

class MainActivity : ComponentActivity() {

    private var mediaController: MediaController? = null
    private var audioEffects: AudioEffects? = null

    private val controllerReady = mutableStateOf(false)
    private val currentSongState =
        mutableStateOf("Nothing playing")
    private val playingState = mutableStateOf(false)

    private var controllerListener: Player.Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectPlaybackService()

        setContent {
            KGMusicApp()
        }
    }

    private fun connectPlaybackService() {
        val token = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )

        val future = MediaController.Builder(this, token)
            .buildAsync()

        future.addListener({
            try {
                val controller = future.get()

                mediaController = controller
                controllerReady.value = true

                audioEffects = AudioEffects(
                    controller.audioSessionId
                )

                val listener = object : Player.Listener {

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        currentSongState.value =
                            mediaItem?.mediaMetadata?.title
                                ?.toString() ?: "Nothing playing"
                    }

                    override fun onIsPlayingChanged(
                        isPlaying: Boolean
                    ) {
                        playingState.value = isPlaying
                    }
                }

                controllerListener = listener
                controller.addListener(listener)

                currentSongState.value =
                    controller.currentMediaItem
                        ?.mediaMetadata?.title
                        ?.toString() ?: "Nothing playing"

                playingState.value = controller.isPlaying

            } catch (e: Exception) {
                controllerReady.value = false
                currentSongState.value =
                    "Playback connection failed"
            }
        }, ContextCompat.getMainExecutor(this))
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

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                songs.add(
                    Song(title, artist, uri)
                )
            }
        }

        return songs
    }

    private fun playSongs(
        songs: List<Song>,
        startIndex: Int,
        shuffle: Boolean,
        repeatAll: Boolean
    ) {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return

        val items = songs.map { song ->

            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .build()

            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaMetadata(metadata)
                .build()
        }

        controller.apply {
            setMediaItems(items, startIndex, 0L)
            shuffleModeEnabled = shuffle

            repeatMode = if (repeatAll) {
                Player.REPEAT_MODE_ALL
            } else {
                Player.REPEAT_MODE_OFF
            }

            prepare()
            play()
        }
    }

    @Composable
    private fun KGMusicApp() {

        var songs by remember {
            mutableStateOf(emptyList<Song>())
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

        val connected by controllerReady
        val currentSong by currentSongState
        val isPlaying by playingState

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

                if (granted) {
                    songs = loadSongs()
                }
            }

        LaunchedEffect(Unit) {
            hasPermission =
                ContextCompat.checkSelfPermission(
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

                    Spacer(Modifier.height(10.dp))

                    Text(
                        if (connected) {
                            "PLAYBACK SERVICE CONNECTED"
                        } else {
                            "CONNECTING TO PLAYBACK SERVICE..."
                        },
                        color = if (connected) gold
                        else Color.LightGray,
                        fontSize = 11.sp
                    )

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = {
                            showEqualizer = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OPEN SOUND ENGINE • EQ")
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            playSongs(
                                songs,
                                0,
                                shuffle,
                                repeatAll
                            )
                        },
                        enabled = connected && songs.isNotEmpty(),
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
                        Text("MY MUSIC", color = Color.White)

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

                                            playSongs(
                                                songs,
                                                index,
                                                shuffle,
                                                repeatAll
                                            )
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
                                    enabled = connected,
                                    onClick = {
                                        mediaController
                                            ?.seekToPrevious()
                                    }
                                ) {
                                    Text("PREV", color = gold)
                                }

                                Button(
                                    enabled = connected,
                                    onClick = {
                                        mediaController?.let {
                                            if (it.isPlaying) {
                                                it.pause()
                                            } else {
                                                it.play()
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        if (isPlaying) "PAUSE"
                                        else "PLAY"
                                    )
                                }

                                TextButton(
                                    enabled = connected,
                                    onClick = {
                                        mediaController
                                            ?.seekToNext()
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
                                    enabled = connected,
                                    onClick = {
                                        shuffle = !shuffle
                                        mediaController
                                            ?.shuffleModeEnabled = shuffle
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
                                    enabled = connected,
                                    onClick = {
                                        repeatAll = !repeatAll

                                        mediaController?.repeatMode =
                                            if (repeatAll) {
                                                Player.REPEAT_MODE_ALL
                                            } else {
                                                Player.REPEAT_MODE_OFF
                                            }
                                    }
                                ) {
                                    Text(
                                        if (repeatAll) "REPEAT ON"
                                        else "REPEAT OFF",
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
        controllerListener?.let {
            mediaController?.removeListener(it)
        }

        audioEffects?.release()
        audioEffects = null

        mediaController?.release()
        mediaController = null

        super.onDestroy()
    }
}

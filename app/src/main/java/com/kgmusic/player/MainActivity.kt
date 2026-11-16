
package com.kgmusic.player

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File

data class Song(
    val title: String,
    val artist: String,
    val uri: android.net.Uri,
    val folderPath: String
)

data class QueueTrack(
    val title: String,
    val artist: String,
    val uri: android.net.Uri
)

class MainActivity : ComponentActivity() {

    private var mediaController: MediaController? = null
    private var audioEffects: AudioEffects? = null

    private val controllerReady = mutableStateOf(false)
    private val currentSongState = mutableStateOf("Nothing playing")
    private val playingState = mutableStateOf(false)

    private val queueState =
        mutableStateOf<List<QueueTrack>>(emptyList())

    private val currentQueueIndexState =
        mutableStateOf(-1)

    private var controllerListener: Player.Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectPlaybackService()

        setContent {
            KGMusicApp()
        }

        hideNavigationBar()
    }

    private fun hideNavigationBar() {
        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideNavigationBar()
        }
    }

    private fun updateQueue(controller: MediaController) {
        val updatedQueue = (0 until controller.mediaItemCount).map { index ->
            val item = controller.getMediaItemAt(index)

            QueueTrack(
                title = item.mediaMetadata.title?.toString()
                    ?: "Unknown song",
                artist = item.mediaMetadata.artist?.toString()
                    ?: "",
                uri = item.localConfiguration?.uri
                    ?: android.net.Uri.EMPTY
            )
        }

        queueState.value = updatedQueue
        currentQueueIndexState.value =
            controller.currentMediaItemIndex
    }

    private fun connectPlaybackService() {
        val token = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )

        val future = MediaController.Builder(this, token).buildAsync()

        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                controllerReady.value = true

                audioEffects = AudioEffects(
                    PlaybackService.audioSessionId
                )

                val listener = object : Player.Listener {

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        currentSongState.value =
                            mediaItem?.mediaMetadata?.title?.toString()
                                ?: "Nothing playing"

                        updateQueue(controller)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playingState.value = isPlaying
                    }

                    override fun onEvents(
                        player: Player,
                        events: Player.Events
                    ) {
                        updateQueue(controller)

                        currentSongState.value =
                            controller.currentMediaItem
                                ?.mediaMetadata
                                ?.title
                                ?.toString()
                                ?: "Nothing playing"

                        playingState.value = controller.isPlaying
                    }
                }

                controllerListener = listener
                controller.addListener(listener)

                currentSongState.value =
                    controller.currentMediaItem?.mediaMetadata?.title
                        ?.toString() ?: "Nothing playing"

                playingState.value = controller.isPlaying

                updateQueue(controller)

            } catch (e: Exception) {
                controllerReady.value = false
                currentSongState.value = "Playback connection failed"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadSongs(): List<Song> {
        val songs = mutableListOf<Song>()

        val projection = if (Build.VERSION.SDK_INT >= 29) {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.RELATIVE_PATH
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
            )
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

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

                val rawArtist = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.ARTIST
                    )
                )?.trim().orEmpty()

                val artist = if (
                    rawArtist.isBlank() ||
                    rawArtist.equals("<unknown>", ignoreCase = true) ||
                    rawArtist.equals("unknown", ignoreCase = true) ||
                    rawArtist.equals("unknown artist", ignoreCase = true)
                ) {
                    ""
                } else {
                    rawArtist
                }

                val folderPath = if (Build.VERSION.SDK_INT >= 29) {
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Media.RELATIVE_PATH
                        )
                    )?.trim('/') ?: ""
                } else {
                    val filePath = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Media.DATA
                        )
                    )
                    filePath?.let { File(it).parent } ?: ""
                }

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                songs.add(
                    Song(
                        title = title,
                        artist = artist,
                        uri = uri,
                        folderPath = folderPath
                    )
                )
            }
        }

        return songs
    }

    private fun createMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .build()

        return MediaItem.Builder()
            .setUri(song.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun playSongs(
        songs: List<Song>,
        startIndex: Int,
        shuffle: Boolean,
        repeatMode: Int
    ) {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return

        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        val items = songs.map { createMediaItem(it) }

        controller.apply {
            setMediaItems(items, safeIndex, 0L)
            shuffleModeEnabled = shuffle

            this.repeatMode = when (repeatMode) {
                1 -> Player.REPEAT_MODE_ALL
                2 -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }

            prepare()
            play()
        }

        updateQueue(controller)
    }

    private fun addToQueue(song: Song) {
        val controller = mediaController

        if (controller == null) {
            Toast.makeText(
                this,
                "Playback service is not ready",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val item = createMediaItem(song)

        if (controller.mediaItemCount == 0) {
            controller.setMediaItem(item)
            controller.prepare()
            controller.play()
        } else {
            controller.addMediaItem(item)
        }

        updateQueue(controller)

        Toast.makeText(
            this,
            "Added to current playlist: ${song.title}",
            Toast.LENGTH_SHORT
        ).show()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun KGMusicApp() {

        var songs by remember { mutableStateOf(emptyList<Song>()) }
        var hasPermission by remember { mutableStateOf(false) }
        var showEqualizer by remember { mutableStateOf(false) }
        var showQueue by remember { mutableStateOf(false) }
        var showPlaylists by remember { mutableStateOf(false) }
        var shuffle by remember { mutableStateOf(false) }

        // 0 = repeat off, 1 = repeat playlist, 2 = repeat one song
        var repeatMode by remember { mutableStateOf(0) }

        var currentFolder by remember { mutableStateOf<String?>(null) }

        val connected by controllerReady
        val currentSong by currentSongState
        val isPlaying by playingState
        val queue by queueState
        val currentQueueIndex by currentQueueIndexState

        val black = Color(0xFF090706)
        val copper = Color(0xFFB87333)
        val lightCopper = Color(0xFFE0A06A)
        val bronze = Color(0xFF75421F)
        val panel = Color(0xFF1C1511)

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

        BackHandler(
            enabled = showEqualizer ||
                showQueue ||
                showPlaylists ||
                currentFolder != null
        ) {
            when {
                showEqualizer -> showEqualizer = false

                showQueue -> showQueue = false

                showPlaylists -> showPlaylists = false

                currentFolder != null -> {
                    val folder = currentFolder ?: ""
                    val parent = folder.substringBeforeLast(
                        "/",
                        missingDelimiterValue = ""
                    )
                    currentFolder = parent.ifEmpty { null }
                }
            }
        }

        MaterialTheme(
            colorScheme = darkColorScheme(
                background = black,
                surface = panel,
                primary = copper,
                secondary = lightCopper
            )
        ) {

            if (showEqualizer) {

                EqualizerScreen(
                    onBack = { showEqualizer = false },
                    audioEffects = audioEffects
                )

            } else if (showPlaylists) {

                PlaylistScreen(
                    songs = songs,
                    onBack = { showPlaylists = false },
                    onPlayPlaylist = { playlistSongs ->
                        playSongs(
                            playlistSongs,
                            0,
                            shuffle,
                            repeatMode
                        )
                        showPlaylists = false
                        showQueue = true
                    }
                )

            } else if (showQueue) {

                NowPlayingScreen(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    connected = connected,
                    queue = queue,
                    currentQueueIndex = currentQueueIndex,
                    shuffle = shuffle,
                    repeatMode = repeatMode,
                    controller = mediaController,

                    onBack = {
                        showQueue = false
                    },

                    onOpenSettings = {
                        showEqualizer = true
                    },

                    onOpenPlaylists = {
                        showQueue = false
                        showPlaylists = true
                    },

                    onShuffleChange = { enabled ->
                        shuffle = enabled
                        mediaController?.shuffleModeEnabled = enabled
                    },

                    onRepeatChange = { mode ->
                        repeatMode = mode

                        mediaController?.repeatMode = when (mode) {
                            1 -> Player.REPEAT_MODE_ALL
                            2 -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    },

                    onSelectQueueItem = { index ->
                        mediaController?.let { controller ->
                            controller.seekTo(index, 0L)
                            controller.play()
                            updateQueue(controller)
                        }
                    }
                )

            } else {

                if (!hasPermission) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(black)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "♫",
                            color = copper,
                            fontSize = 36.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "LET THE MUSIC IN",
                            color = lightCopper,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                permissionLauncher.launch(permission)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = bronze
                            )
                        ) {
                            Text("ALLOW MUSIC ACCESS")
                        }
                    }

                } else {

                    HomeScreen(
                        songs = songs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        connected = connected,
                        shuffle = shuffle,
                        repeatMode = repeatMode,

                        onPlayAll = {
                            playSongs(
                                songs,
                                0,
                                shuffle,
                                repeatMode
                            )
                        },

                        onShuffle = {
                            shuffle = !shuffle
                            mediaController?.shuffleModeEnabled = shuffle
                        },

                        onOpenEqualizer = {
                            showEqualizer = true
                        },

                        onOpenPlaylists = {
                            showPlaylists = true
                        },

                        onExit = {
                            mediaController?.pause()
                            finishAndRemoveTask()
                        },

                        onPlaySongs = { selectedSongs, startIndex ->
                            playSongs(
                                selectedSongs,
                                startIndex,
                                shuffle,
                                repeatMode
                            )
                        },

                        onOpenQueue = {
                            mediaController?.let { controller ->
                                updateQueue(controller)
                                showQueue = true
                            }
                        },

                        onPrevious = {
                            mediaController?.seekToPrevious()
                        },

                        onTogglePlayback = {
                            mediaController?.let { controller ->
                                if (controller.isPlaying) {
                                    controller.pause()
                                } else {
                                    controller.play()
                                }
                            }
                        },

                        onNext = {
                            mediaController?.seekToNext()
                        },

                        onRepeat = {
                            repeatMode = (repeatMode + 1) % 3

                            mediaController?.repeatMode = when (repeatMode) {
                                1 -> Player.REPEAT_MODE_ALL
                                2 -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        controllerListener?.let { listener ->
            mediaController?.removeListener(listener)
        }

        mediaController?.release()
        mediaController = null

        audioEffects?.release()
        audioEffects = null

        super.onDestroy()
    }
}

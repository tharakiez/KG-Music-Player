
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
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
import java.io.File

data class Song(
    val title: String,
    val artist: String,
    val uri: android.net.Uri,
    val folderPath: String
)

class MainActivity : ComponentActivity() {

    private var mediaController: MediaController? = null
    private var audioEffects: AudioEffects? = null

    private val controllerReady = mutableStateOf(false)
    private val currentSongState = mutableStateOf("Nothing playing")
    private val playingState = mutableStateOf(false)

    private var controllerListener: Player.Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectPlaybackService()
        setContent { KGMusicApp() }
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

                audioEffects =
                    AudioEffects(PlaybackService.audioSessionId)

                val listener = object : Player.Listener {

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        currentSongState.value =
                            mediaItem?.mediaMetadata?.title?.toString()
                                ?: "Nothing playing"
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playingState.value = isPlaying
                    }
                }

                controllerListener = listener
                controller.addListener(listener)

                currentSongState.value =
                    controller.currentMediaItem?.mediaMetadata?.title
                        ?.toString() ?: "Nothing playing"

                playingState.value = controller.isPlaying

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

                val artist = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.ARTIST
                    )
                ) ?: "Unknown Artist"

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
        repeatAll: Boolean
    ) {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return

        val items = songs.map { createMediaItem(it) }

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
        var shuffle by remember { mutableStateOf(false) }
        var repeatAll by remember { mutableStateOf(false) }
        var currentFolder by remember { mutableStateOf<String?>(null) }

        val connected by controllerReady
        val currentSong by currentSongState
        val isPlaying by playingState

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val permissionLauncher = rememberLauncherForActivityResult(
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

            if (hasPermission) songs = loadSongs()
        }

        BackHandler(
            enabled = showEqualizer || currentFolder != null
        ) {
            if (showEqualizer) {
                showEqualizer = false
            } else {
                val folder = currentFolder ?: ""
                val parent = folder.substringBeforeLast(
                    "/",
                    missingDelimiterValue = ""
                )
                currentFolder = parent.ifEmpty { null }
            }
        }

        val black = Color(0xFF080808)
        val gold = Color(0xFFFFC400)

        val folderNames = remember(songs, currentFolder) {
            val prefix = currentFolder?.let { "$it/" } ?: ""

            songs.mapNotNull { song ->
                val path = song.folderPath

                if (path == currentFolder || path.isEmpty()) {
                    null
                } else if (path.startsWith(prefix)) {
                    path.removePrefix(prefix)
                        .substringBefore("/")
                        .takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }.distinct().sorted()
        }

        val visibleSongs = remember(songs, currentFolder) {
            songs.filter {
                it.folderPath == (currentFolder ?: "")
            }
        }

        MaterialTheme(
            colorScheme = darkColorScheme(
                background = black,
                surface = Color(0xFF171717),
                primary = gold
            )
        ) {

            if (showEqualizer) {

                EqualizerScreen(
                    onBack = { showEqualizer = false },
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

                    Spacer(Modifier.height(8.dp))

                    Text(
                        if (connected) "PLAYBACK SERVICE CONNECTED"
                        else "CONNECTING TO PLAYBACK SERVICE...",
                        color = if (connected) gold else Color.LightGray,
                        fontSize = 11.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { showEqualizer = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OPEN SOUND ENGINE • EQ")
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            playSongs(songs, 0, shuffle, repeatAll)
                        },
                        enabled = connected && songs.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("▶ PLAY ALL SONGS")
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "MY MUSIC",
                            color = Color.White,
                            fontSize = 18.sp
                        )

                        Text(
                            "${songs.size} Songs",
                            color = gold
                        )
                    }

                    if (currentFolder != null) {

                        TextButton(
                            onClick = {
                                val folder = currentFolder ?: ""
                                val parent = folder.substringBeforeLast(
                                    "/",
                                    missingDelimiterValue = ""
                                )
                                currentFolder = parent.ifEmpty { null }
                            }
                        ) {
                            Text(
                                "← Back  •  ${currentFolder?.substringAfterLast("/")}",
                                color = gold
                            )
                        }

                        Text(
                            currentFolder ?: "",
                            color = Color.Gray,
                            fontSize = 11.sp
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

                            items(
                                folderNames,
                                key = { "folder-$it" }
                            ) { folder ->

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentFolder =
                                                if (currentFolder == null) {
                                                    folder
                                                } else {
                                                    "${currentFolder}/$folder"
                                                }
                                        }
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = "Folder",
                                        tint = gold
                                    )

                                    Spacer(Modifier.width(12.dp))

                                    Text(
                                        folder,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Text(
                                        "OPEN  ›",
                                        color = gold,
                                        fontSize = 11.sp
                                    )
                                }

                                HorizontalDivider(
                                    color = Color.DarkGray
                                )
                            }

                            items(
                                visibleSongs,
                                key = { it.uri.toString() }
                            ) { song ->

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                val index =
                                                    visibleSongs.indexOf(song)

                                                playSongs(
                                                    visibleSongs,
                                                    index,
                                                    shuffle,
                                                    repeatAll
                                                )
                                            },
                                            onLongClick = {
                                                addToQueue(song)
                                            }
                                        )
                                        .padding(vertical = 13.dp)
                                ) {

                                    Row(
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {

                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = "Song",
                                            tint = gold
                                        )

                                        Spacer(Modifier.width(12.dp))

                                        Column(
                                            modifier = Modifier.weight(1f)
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

                                 

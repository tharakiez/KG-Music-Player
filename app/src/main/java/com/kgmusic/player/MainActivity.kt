
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                    ?: "Unknown artist",
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
        repeatMode: Int
    ) {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return

        val items = songs.map { createMediaItem(it) }

        controller.apply {
            setMediaItems(items, startIndex, 0L)
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
        val muted = Color(0xFFAA9380)
        val line = Color(0xFF493326)

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

                // MAIN AFRICAN COPPER HOME SCREEN

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(black)
                        .padding(
                            horizontal = 14.dp,
                            vertical = 12.dp
                        )
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(205.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(
                                1.dp,
                                copper.copy(alpha = 0.8f),
                                RoundedCornerShape(24.dp)
                            )
                    ) {

                        Image(
                            painter = painterResource(
                                id = R.drawable.kg_portrait
                            ),
                            contentDescription = "KG portrait",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x33090706),
                                            Color.Transparent,
                                            Color(0xF5090706)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                "BANJUKA ARTS  •  PRESENTS",
                                color = lightCopper,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(18.dp)
                        ) {
                            Text(
                                "KG",
                                color = lightCopper,
                                fontSize = 43.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 3.sp
                            )

                            Text(
                                "AFRICAN SOUL",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                "YOUR SOUND. YOUR ROOTS. YOUR STORY.",
                                color = lightCopper,
                                fontSize = 9.sp,
                                letterSpacing = 1.5.sp
                            )
                        }

                        Text(
                            "◇  ◆  ◇",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            color = lightCopper,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        "◆  ◇  ◆  ◇  ◆  ◇  ◆",
                        modifier = Modifier.fillMaxWidth(),
                        color = copper,
                        fontSize = 12.sp,
                        letterSpacing = 4.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "KG MUSIC PLAYER",
                                color = lightCopper,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )

                            Text(
                                if (connected) {
                                    "● SOUND ENGINE CONNECTED"
                                } else {
                                    "○ CONNECTING TO SOUND ENGINE..."
                                },
                                color = if (connected) copper else muted,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            "COPPER EDITION",
                            color = muted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        OutlinedButton(
                            onClick = { showEqualizer = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                copper
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = lightCopper
                            ),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text(
                                "♫ ENGINE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { showPlaylists = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                copper
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = lightCopper
                            ),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text(
                                "♫ PLAYLISTS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                playSongs(
                                    songs,
                                    0,
                                    shuffle,
                                    repeatMode
                                )
                            },
                            enabled = connected && songs.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = bronze
                            ),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text(
                                "▶ PLAY ALL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(15.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "MY MUSIC",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )

                            Text(
                                "YOUR COLLECTION",
                                color = muted,
                                fontSize = 9.sp,
                                letterSpacing = 2.sp
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                "${songs.size}",
                                color = lightCopper,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "TRACKS",
                                color = muted,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 9.dp),
                        color = line
                    )

                    if (currentFolder != null) {
                        TextButton(
                            onClick = {
                                val folder = currentFolder ?: ""
                                val parent = folder.substringBeforeLast(
                                    "/",
                                    missingDelimiterValue = ""
                                )
                                currentFolder = parent.ifEmpty { null }
                            },
                            contentPadding = PaddingValues(
                                horizontal = 8.dp,
                                vertical = 2.dp
                            )
                        ) {
                            Text(
                                "← BACK TO PARENT",
                                color = lightCopper,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            currentFolder ?: "",
                            color = muted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(
                                start = 8.dp,
                                bottom = 6.dp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {

                        if (!hasPermission) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "♫",
                                    color = copper,
                                    fontSize = 36.sp
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "LET THE MUSIC IN",
                                    color = lightCopper,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )

                                Spacer(Modifier.height(10.dp))

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

                        } else if (songs.isEmpty()) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "♫",
                                    color = copper,
                                    fontSize = 36.sp
                                )

                                Text(
                                    "NO MUSIC FOUND",
                                    color = lightCopper,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )

                                Text(
                                    "Add music to your phone to begin.",
                                    color = muted,
                                    fontSize = 11.sp
                                )
                            }

                        } else {

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {

                                items(
                                    folderNames,
                                    key = { "folder-$it" }
                                ) { folder ->

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(panel)
                                            .clickable {
                                                currentFolder =
                                                    if (currentFolder == null) {
                                                        folder
                                                    } else {
                                                        "${currentFolder}/$folder"
                                                    }
                                            }
                                            .padding(
                                                horizontal = 13.dp,
                                                vertical = 13.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {

                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = "Folder",
                                            tint = copper,
                                            modifier = Modifier.size(23.dp)
                                        )

                                        Spacer(Modifier.width(13.dp))

                                        Text(
                                            folder,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Text(
                                            "OPEN  ›",
                                            color = lightCopper,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(Modifier.height(5.dp))
                                }

                                items(
                                    visibleSongs,
                                    key = { it.uri.toString() }
                                ) { song ->

                                    Row(
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
                                                        repeatMode
                                                    )
                                                },
                                                onLongClick = {
                                                    addToQueue(song)
                                                }
                                            )
                                            .padding(
                                                horizontal = 12.dp,
                                                vertical = 11.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {

                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF302016)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.MusicNote,
                                                contentDescription = "Song",
                                                tint = lightCopper,
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                song.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(Modifier.height(3.dp))

                                            Text(
                                                song.artist,
                                                color = muted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Text(
                                            "•••",
                                            color = copper,
                                            fontSize = 15.sp
                                        )
                                    }

                                    HorizontalDivider(
                                        color = line.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                copper.copy(alpha = 0.65f),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                updateQueue(mediaController ?: return@clickable)
                                showQueue = true
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = panel
                        )
                    ) {

                        Column(
                            modifier = Modifier.padding(
                                horizontal = 13.dp,
                                vertical = 11.dp
                            )
                        ) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "NOW PLAYING",
                                    color = lightCopper,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    "VIEW QUEUE  ›",
                                    color = copper,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.width(8.dp))

                                Text(
                                    if (isPlaying) "● PLAYING" else "● READY",
                                    color = if (isPlaying) copper else muted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.height(5.dp))

                            Text(
                                currentSong,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(7.dp))

                            HorizontalDivider(color = line)

                            Spacer(Modifier.height(5.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                IconButton(
                                    onClick = {
                                        shuffle = !shuffle
                                        mediaController?.shuffleModeEnabled =
                                            shuffle
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = if (shuffle) {
                                            "Shuffle on"
                                        } else {
                                            "Shuffle off"
                                        },
                                        tint = if (shuffle) lightCopper else muted,
                                        modifier = Modifier.size(23.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        mediaController?.seekToPrevious()
                                    },
                                    enabled = connected,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Text(
                                        "⏮",
                                        color = lightCopper,
                                        fontSize = 21.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        mediaController?.let { controller ->
                                            if (controller.isPlaying) {
                                                controller.pause()
                                            } else {
                                                controller.play()
                                            }
                                        }
                                    },
                                    enabled = connected,
                                    modifier = Modifier.size(
                                        width = 55.dp,
                                        height = 42.dp
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = copper
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        if (isPlaying) "Ⅱ" else "▶",
                                        color = Color.White,
                                        fontSize = 21.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        mediaController?.seekToNext()
                                    },
                                    enabled = connected,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Text(
                                        "⏭",
                                        color = lightCopper,
                                        fontSize = 21.sp
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        repeatMode = (repeatMode + 1) % 3

                                        mediaController?.repeatMode =
                                            when (repeatMode) {
                                                1 -> Player.REPEAT_MODE_ALL
                                                2 -> Player.REPEAT_MODE_ONE
                                                else -> Player.REPEAT_MODE_OFF
                                            }
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = if (repeatMode == 2) {
                                            Icons.Default.RepeatOne
                                        } else {
                                            Icons.Default.Repeat
                                        },
                                        contentDescription = when (repeatMode) {
                                            1 -> "Repeat playlist"
                                            2 -> "Repeat current song"
                                            else -> "Repeat off"
                                        },
                                        tint = when (repeatMode) {
                                            1 -> copper
                                            2 -> lightCopper
                                            else -> muted
                                        },
                                        modifier = Modifier.size(23.dp)
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

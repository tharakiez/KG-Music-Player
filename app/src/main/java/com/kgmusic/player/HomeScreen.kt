
package com.kgmusic.player

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    songs: List<Song>,
    currentSong: String,
    isPlaying: Boolean,
    connected: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onExit: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onAddToQueue: (List<Song>) -> Unit,
    onOpenQueue: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit
) {
    val black = Color(0xFF090706)
    val copper = Color(0xFFB87333)
    val lightCopper = Color(0xFFE0A06A)
    val gold = Color(0xFFD6A84B)
    val panel = Color(0xFF1C1511)
    val muted = Color(0xFFAA9380)
    val line = Color(0xFF493326)

    val context = LocalContext.current

    val navigationPreferences = remember(context) {
        context.getSharedPreferences(
            "kg_navigation",
            Context.MODE_PRIVATE
        )
    }

    var drawerOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    var section by remember {
        mutableStateOf(
            navigationPreferences.getString("last_section", "Home")
                ?: "Home"
        )
    }

    var selectedFolder by remember {
        mutableStateOf(
            navigationPreferences.getString("last_folder", null)
        )
    }

    LaunchedEffect(section, selectedFolder) {
        val editor = navigationPreferences.edit()
            .putString("last_section", section)

        if (selectedFolder == null) {
            editor.remove("last_folder")
        } else {
            editor.putString("last_folder", selectedFolder)
        }

        editor.apply()
    }

    // Existing song playback prompt
    var showSubfolderPrompt by remember { mutableStateOf(false) }
    var pendingSong by remember { mutableStateOf<Song?>(null) }

    // New folder action menu and dialogs
    var folderActionMenuPath by remember { mutableStateOf<String?>(null) }
    var pendingFolderPath by remember { mutableStateOf<String?>(null) }
    var pendingFolderAction by remember { mutableStateOf("") }
    var showFolderActionPrompt by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    var pendingFolderSongs by remember {
        mutableStateOf<List<Song>>(emptyList())
    }

    var availablePlaylists by remember {
        mutableStateOf(PlaylistStorage.load(context))
    }

    fun normalizedPath(path: String): String {
        return path.trim('/')
    }

    fun parentPath(path: String): String {
        val clean = normalizedPath(path)
        return clean.substringBeforeLast("/", missingDelimiterValue = "")
    }

    fun folderName(path: String): String {
        return normalizedPath(path).substringAfterLast('/')
    }

    fun directSongs(path: String): List<Song> {
        return songs.filter {
            normalizedPath(it.folderPath) == normalizedPath(path)
        }.sortedBy { it.title.lowercase() }
    }

    fun childFolders(path: String): List<String> {
        val parent = normalizedPath(path)

        return songs.mapNotNull { song ->
            val clean = normalizedPath(song.folderPath)

            if (clean.isBlank() || clean == parent) {
                null
            } else {
                val belongsToParent = if (parent.isBlank()) {
                    true
                } else {
                    clean.startsWith("$parent/")
                }

                if (!belongsToParent) {
                    null
                } else {
                    val remainder = if (parent.isBlank()) {
                        clean
                    } else {
                        clean.removePrefix("$parent/")
                    }

                    remainder.substringBefore('/')
                        .takeIf { it.isNotBlank() }
                        ?.let { name ->
                            if (parent.isBlank()) name else "$parent/$name"
                        }
                }
            }
        }.distinct().sortedBy { it.lowercase() }
    }

    fun songsInFolder(
        path: String,
        includeSubfolders: Boolean
    ): List<Song> {
        val clean = normalizedPath(path)

        return songs.filter { song ->
            val songPath = normalizedPath(song.folderPath)

            if (includeSubfolders) {
                songPath == clean || songPath.startsWith("$clean/")
            } else {
                songPath == clean
            }
        }.sortedWith(
            compareBy<Song>(
                { normalizedPath(it.folderPath).lowercase() },
                { it.title.lowercase() }
            )
        )
    }

    // Existing folder playback
    fun playSelectedFolder(includeSubfolders: Boolean) {
        val folder = selectedFolder ?: return
        val folderSongs = songsInFolder(folder, includeSubfolders)

        if (folderSongs.isNotEmpty()) {
            onPlaySongs(folderSongs, 0)
        }

        showSubfolderPrompt = false
        pendingSong = null
    }

    fun requestFolderPlayback(song: Song? = null) {
        val folder = selectedFolder ?: return
        val children = childFolders(folder)

        if (children.isNotEmpty()) {
            pendingSong = song
            showSubfolderPrompt = true
        } else {
            val folderSongs = directSongs(folder)

            val startIndex = if (song == null) {
                0
            } else {
                folderSongs.indexOfFirst {
                    it.uri == song.uri
                }.coerceAtLeast(0)
            }

            if (folderSongs.isNotEmpty()) {
                onPlaySongs(folderSongs, startIndex)
            }
        }
    }

    fun playFolderChoice(includeSubfolders: Boolean) {
        val folder = selectedFolder ?: return
        val folderSongs = songsInFolder(folder, includeSubfolders)
        val selectedSong = pendingSong

        val startIndex = if (selectedSong == null) {
            0
        } else {
            folderSongs.indexOfFirst {
                it.uri == selectedSong.uri
            }.coerceAtLeast(0)
        }

        if (folderSongs.isNotEmpty()) {
            onPlaySongs(folderSongs, startIndex)
        }

        showSubfolderPrompt = false
        pendingSong = null
    }

    // New folder action handler
    fun executeFolderAction(
        path: String,
        action: String,
        includeSubfolders: Boolean
    ) {
        val folderSongs = songsInFolder(path, includeSubfolders)

        if (folderSongs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found in this folder",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        when (action) {
            "play" -> {
                onPlaySongs(folderSongs, 0)
            }

            "queue" -> {
                onAddToQueue(folderSongs)
            }

            "playlist" -> {
                pendingFolderSongs = folderSongs
                availablePlaylists = PlaylistStorage.load(context)
                showPlaylistPicker = true
            }
        }
    }

    fun requestFolderAction(path: String, action: String) {
        folderActionMenuPath = null

        if (childFolders(path).isNotEmpty()) {
            pendingFolderPath = path
            pendingFolderAction = action
            showFolderActionPrompt = true
        } else {
            executeFolderAction(path, action, false)
        }
    }

    fun completeFolderAction(includeSubfolders: Boolean) {
        val path = pendingFolderPath
        val action = pendingFolderAction

        showFolderActionPrompt = false
        pendingFolderPath = null
        pendingFolderAction = ""

        if (path != null) {
            executeFolderAction(path, action, includeSubfolders)
        }
    }

    fun addFolderSongsToPlaylist(playlistName: String) {
        var addedCount = 0

        pendingFolderSongs.forEach { song ->
            val added = PlaylistStorage.addSong(
                context,
                playlistName,
                SavedPlaylistSong(
                    title = song.title,
                    artist = song.artist,
                    uri = song.uri.toString(),
                    folderPath = song.folderPath
                )
            )

            if (added) addedCount++
        }

        Toast.makeText(
            context,
            "$addedCount songs added to $playlistName",
            Toast.LENGTH_SHORT
        ).show()

        showPlaylistPicker = false
        pendingFolderSongs = emptyList()
        availablePlaylists = PlaylistStorage.load(context)
    }

    @Composable
    fun FolderActionMenu(path: String) {
        var expanded by remember(path) {
            mutableStateOf(false)
        }

        Box {
            TextButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    "⋮",
                    color = lightCopper,
                    fontSize = 23.sp
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(panel)
            ) {
                DropdownMenuItem(
                    text = {
                        Text("Play folder", color = Color.White)
                    },
                    onClick = {
                        expanded = false
                        requestFolderAction(path, "play")
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Add to playlist", color = Color.White)
                    },
                    onClick = {
                        expanded = false
                        requestFolderAction(path, "playlist")
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Add to Now Playing", color = Color.White)
                    },
                    onClick = {
                        expanded = false
                        requestFolderAction(path, "queue")
                    }
                )
            }
        }
    }

    BackHandler(
        enabled = drawerOpen || menuOpen ||
            showSubfolderPrompt ||
            showFolderActionPrompt ||
            showPlaylistPicker ||
            folderActionMenuPath != null ||
            searchOpen || section != "Home"
    ) {
        when {
            showPlaylistPicker -> {
                showPlaylistPicker = false
            }

            showFolderActionPrompt -> {
                showFolderActionPrompt = false
                pendingFolderPath = null
            }

            showSubfolderPrompt -> {
                showSubfolderPrompt = false
                pendingSong = null
            }

            folderActionMenuPath != null -> {
                folderActionMenuPath = null
            }

            menuOpen -> menuOpen = false
            drawerOpen -> drawerOpen = false

            section == "Folder" && selectedFolder != null -> {
                val parent = parentPath(selectedFolder!!)

                if (parent.isNotBlank()) {
                    selectedFolder = parent
                } else {
                    selectedFolder = null
                    section = "Folders"
                }

                searchText = ""
            }

            section != "Home" -> {
                section = "Home"
                selectedFolder = null
                searchText = ""
                searchOpen = false
            }

            searchOpen -> {
                searchOpen = false
                searchText = ""
            }
        }
    }

    val rootFolders = remember(songs) {
        songs.mapNotNull { song ->
            normalizedPath(song.folderPath)
                .takeIf { it.isNotBlank() }
                ?.substringBefore('/')
        }.distinct().sortedBy { it.lowercase() }
    }

    val artists = remember(songs) {
        songs.map { it.artist.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    val visibleSongs = remember(
        songs,
        searchText,
        section,
        selectedFolder
    ) {
        val base = when (section) {
            "Folder" -> selectedFolder?.let {
                directSongs(it)
            } ?: emptyList()

            "Artists" -> songs
            else -> songs
        }

        if (searchText.isBlank()) {
            base
        } else {
            base.filter {
                it.title.contains(searchText, ignoreCase = true) ||
                    it.artist.contains(searchText, ignoreCase = true) ||
                    it.folderPath.contains(searchText, ignoreCase = true)
            }
        }
    }

    @Composable
    fun menuEntry(label: String, action: () -> Unit) {
        DropdownMenuItem(
            text = {
                Text(
                    label,
                    color = Color.White,
                    fontSize = 14.sp
                )
            },
            onClick = action
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // KG MUSIC HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(
                    onClick = {
                        drawerOpen = true
                        menuOpen = false
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        "☰",
                        color = lightCopper,
                        fontSize = 25.sp
                    )
                }

                DropdownMenu(
                    expanded = drawerOpen,
                    onDismissRequest = { drawerOpen = false },
                    modifier = Modifier
                        .background(panel)
                        .width(220.dp)
                ) {
                    Text(
                        "KG MUSIC",
                        color = lightCopper,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        )
                    )

                    HorizontalDivider(color = line)

                    listOf(
                        "Songs",
                        "Folders",
                        "Genres",
                        "Artists",
                        "Playlists",
                        "Settings"
                    ).forEach { item ->
                        menuEntry(item) {
                            drawerOpen = false
                            searchText = ""
                            selectedFolder = null

                            when (item) {
                                "Playlists" -> onOpenPlaylists()
                                "Settings" -> onOpenEqualizer()
                                else -> section = item
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "KG MUSIC",
                    color = lightCopper,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Text(
                    "BANJUKA ARTS",
                    color = muted,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )
            }

            TextButton(
                onClick = {
                    searchOpen = !searchOpen
                    searchText = ""
                },
                contentPadding = PaddingValues(6.dp)
            ) {
                Text(
                    "⌕",
                    color = lightCopper,
                    fontSize = 27.sp
                )
            }

            Box {
                TextButton(
                    onClick = {
                        menuOpen = true
                        drawerOpen = false
                    },
                    contentPadding = PaddingValues(6.dp)
                ) {
                    Text(
                        "⋮",
                        color = lightCopper,
                        fontSize = 27.sp
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(panel)
                ) {
                    menuEntry("Play All") {
                        menuOpen = false
                        onPlayAll()
                    }

                    menuEntry("Shuffle") {
                        menuOpen = false
                        onShuffle()
                    }

                    menuEntry("Equalizer") {
                        menuOpen = false
                        onOpenEqualizer()
                    }

                    HorizontalDivider(color = line)

                    menuEntry("Exit") {
                        menuOpen = false
                        onExit()
                    }
                }
            }
        }

        if (searchOpen) {
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        "Search your music",
                        color = muted
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = copper,
                    unfocusedBorderColor = line,
                    cursorColor = lightCopper
                )
            )
        }

        // HOME SCREEN
        if (section == "Home") {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    Image(
                        painter = painterResource(
                            id = R.drawable.african_sunset_acacia
                        ),
                        contentDescription =
                            "African sunset with acacia trees and African music artwork",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPlayAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = copper
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            "▶  PLAY ALL SONGS",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            gold
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            "♫  SOUND ENGINE",
                            color = lightCopper,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (section == "Folder" && selectedFolder != null) {
                        folderName(selectedFolder!!)
                    } else {
                        section.uppercase()
                    },
                    color = lightCopper,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f)
                )

                if (section == "Folder" && selectedFolder != null) {
                    TextButton(
                        onClick = { requestFolderPlayback() }
                    ) {
                        Text(
                            "PLAY FOLDER",
                            color = copper,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(
                    onClick = {
                        if (section == "Folder" && selectedFolder != null) {
                            val parent = parentPath(selectedFolder!!)

                            if (parent.isNotBlank()) {
                                selectedFolder = parent
                            } else {
                                selectedFolder = null
                                section = "Folders"
                            }
                        } else {
                            section = "Home"
                            selectedFolder = null
                            searchText = ""
                            searchOpen = false
                        }
                    }
                ) {
                    Text(
                        if (section == "Folder") "BACK" else "CLOSE ×",
                        color = copper,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = line)
            Spacer(Modifier.height(8.dp))

            when (section) {
                "Genres" -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Genre information is not yet available\nin the phone's music library.",
                            color = muted,
                            fontSize = 13.sp
                        )
                    }
                }

                "Folders" -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(rootFolders, key = { it }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedFolder = folder
                                            section = "Folder"
                                            searchText = ""
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "▣",
                                        color = copper,
                                        fontSize = 21.sp
                                    )

                                    Spacer(Modifier.width(14.dp))

                                    Text(
                                        folder,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Text(
                                        "›",
                                        color = lightCopper,
                                        fontSize = 22.sp
                                    )
                                }

                                FolderActionMenu(folder)
                            }

                            HorizontalDivider(color = line)
                        }
                    }
                }

                "Folder" -> {
                    val folder = selectedFolder ?: ""
                    val children = remember(songs, folder) {
                        childFolders(folder)
                    }
                    val folderSongs = remember(songs, folder) {
                        directSongs(folder)
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(children, key = { it }) { child ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedFolder = child
                                            searchText = ""
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "▣",
                                        color = copper,
                                        fontSize = 21.sp
                                    )

                                    Spacer(Modifier.width(14.dp))

                                    Text(
                                        folderName(child),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Text(
                                        "›",
                                        color = lightCopper,
                                        fontSize = 22.sp
                                    )
                                }

                                FolderActionMenu(child)
                            }

                            HorizontalDivider(color = line)
                        }

                        items(
                            folderSongs,
                            key = { it.uri.toString() }
                        ) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        requestFolderPlayback(song)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF302016)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "♫",
                                        color = lightCopper,
                                        fontSize = 21.sp
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

                                    if (song.artist.isNotBlank()) {
                                        Text(
                                            song.artist,
                                            color = muted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Text(
                                    "▶",
                                    color = copper,
                                    fontSize = 13.sp
                                )
                            }

                            HorizontalDivider(color = line)
                        }
                    }
                }

                "Artists" -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(
                            artists.filter {
                                it.contains(searchText, ignoreCase = true)
                            }
                        ) { artist ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        section = "Songs"
                                        searchText = artist
                                    }
                                    .padding(vertical = 14.dp)
                            ) {
                                Text(
                                    artist,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )

                                Text(
                                    "VIEW ARTIST SONGS  ›",
                                    color = muted,
                                    fontSize = 9.sp
                                )
                            }

                            HorizontalDivider(color = line)
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(
                            visibleSongs,
                            key = { it.uri.toString() }
                        ) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val ordered = songs.sortedBy {
                                            it.title.lowercase()
                                        }

                                        val index = ordered.indexOfFirst {
                                            it.uri == song.uri
                                        }

                                        if (index >= 0) {
                                            onPlaySongs(ordered, index)
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF302016)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "♫",
                                        color = lightCopper,
                                        fontSize = 21.sp
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

                                    if (song.artist.isNotBlank()) {
                                        Text(
                                            song.artist,
                                            color = muted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Text(
                                    "▶",
                                    color = copper,
                                    fontSize = 13.sp
                                )
                            }

                            HorizontalDivider(color = line)
                        }
                    }
                }
            }
        }

        // NOW PLAYING PANEL
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenQueue() },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = panel
            )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 9.dp
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "NOW PLAYING",
                            color = lightCopper,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Text(
                            currentSong,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        if (isPlaying) "● PLAYING" else "● READY",
                        color = if (isPlaying) copper else muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(5.dp))
                HorizontalDivider(color = line)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onShuffle) {
                        Text(
                            if (shuffle) "⤨" else "⇄",
                            color = if (shuffle) lightCopper else muted,
                            fontSize = 20.sp
                        )
                    }

                    TextButton(
                        onClick = onPrevious,
                        enabled = connected
                    ) {
                        Text(
                            "⏮",
                            color = lightCopper,
                            fontSize = 19.sp
                        )
                    }

                    Button(
                        onClick = onTogglePlayback,
                        enabled = connected,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = copper
                        ),
                        contentPadding = PaddingValues(
                            horizontal = 18.dp,
                            vertical = 5.dp
                        )
                    ) {
                        Text(
                            if (isPlaying) "Ⅱ" else "▶",
                            color = Color.White,
                            fontSize = 19.sp
                        )
                    }

                    TextButton(
                        onClick = onNext,
                        enabled = connected
                    ) {
                        Text(
                            "⏭",
                            color = lightCopper,
                            fontSize = 19.sp
                        )
                    }

                    TextButton(onClick = onRepeat) {
                        Text(
                            when (repeatMode) {
                                1 -> "🔁"
                                2 -> "🔂"
                                else -> "↻"
                            },
                            color = if (repeatMode == 0) muted else lightCopper,
                            fontSize = 19.sp
                        )
                    }
                }
            }
        }
    }

    // Existing song subfolder prompt
    if (showSubfolderPrompt) {
        AlertDialog(
            onDismissRequest = {
                showSubfolderPrompt = false
                pendingSong = null
            },
            containerColor = panel,
            title = {
                Text(
                    "Add subfolders too?",
                    color = lightCopper,
                    fontWeight = FontWeight.Bold
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { playFolderChoice(true) }
                ) {
                    Text("YES", color = lightCopper)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { playFolderChoice(false) }
                ) {
                    Text("NO", color = muted)
                }
            }
        )
    }

    // New folder action subfolder prompt
    if (showFolderActionPrompt) {
        AlertDialog(
            onDismissRequest = {
                showFolderActionPrompt = false
                pendingFolderPath = null
            },
            containerColor = panel,
            title = {
                Text(
                    "Include subfolders?",
                    color = lightCopper,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Should this action include songs inside the subfolders too?",
                    color = muted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        completeFolderAction(true)
                    }
                ) {
                    Text("YES", color = lightCopper)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        completeFolderAction(false)
                    }
                ) {
                    Text("NO", color = muted)
                }
            }
        )
    }

    // Playlist selection dialog
    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = {
                showPlaylistPicker = false
                pendingFolderSongs = emptyList()
            },
            containerColor = panel,
            title = {
                Text(
                    "Choose playlist",
                    color = lightCopper,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (availablePlaylists.isEmpty()) {
                    Text(
                        "You don't have any playlists yet. Create a playlist first.",
                        color = muted
                    )
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        availablePlaylists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    addFolderSongsToPlaylist(playlist.name)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    playlist.name,
                                    color = Color.White
                                )
                            }

                            HorizontalDivider(color = line)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlaylistPicker = false
                        onOpenPlaylists()
                    }
                ) {
                    Text(
                        if (availablePlaylists.isEmpty()) {
                            "CREATE PLAYLIST"
                        } else {
                            "CLOSE"
                        },
                        color = lightCopper
                    )
                }
            }
        )
    }
}

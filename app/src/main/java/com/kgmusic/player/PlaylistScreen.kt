
package com.kgmusic.player

import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlaylistScreen(
    songs: List<Song>,
    onBack: () -> Unit,
    onPlayPlaylist: (List<Song>) -> Unit
) {
    val context = LocalContext.current

    val black = Color(0xFF090706)
    val copper = Color(0xFFB87333)
    val lightCopper = Color(0xFFE0A06A)
    val panel = Color(0xFF1C1511)
    val muted = Color(0xFFAA9380)
    val line = Color(0xFF493326)

    var playlists by remember {
        mutableStateOf(PlaylistStorage.load(context))
    }

    var selectedPlaylistName by remember {
        mutableStateOf<String?>(null)
    }

    var showCreateDialog by remember {
        mutableStateOf(false)
    }

    var showAddDialog by remember {
        mutableStateOf(false)
    }

    var showFolderPrompt by remember {
        mutableStateOf(false)
    }

    var newPlaylistName by remember {
        mutableStateOf("")
    }

    var selectedUris by remember {
        mutableStateOf(setOf<String>())
    }

    var browseFolder by remember {
        mutableStateOf<String?>(null)
    }

    var pendingFolder by remember {
        mutableStateOf<String?>(null)
    }

    fun refreshPlaylists() {
        playlists = PlaylistStorage.load(context)
    }

    fun cleanPath(path: String): String {
        return path.trim('/')
    }

    fun folderName(path: String): String {
        return cleanPath(path).substringAfterLast('/')
    }

    fun childFolders(path: String): List<String> {
        val parent = cleanPath(path)

        return songs.mapNotNull { song ->
            val clean = cleanPath(song.folderPath)

            if (clean.isBlank() || clean == parent) {
                null
            } else {
                val belongs = if (parent.isBlank()) {
                    true
                } else {
                    clean.startsWith("$parent/")
                }

                if (!belongs) {
                    null
                } else {
                    val remainder = if (parent.isBlank()) {
                        clean
                    } else {
                        clean.removePrefix("$parent/")
                    }

                    val child = remainder.substringBefore('/')

                    if (child.isBlank()) {
                        null
                    } else if (parent.isBlank()) {
                        child
                    } else {
                        "$parent/$child"
                    }
                }
            }
        }.distinct().sortedBy { it.lowercase() }
    }

    fun directSongs(path: String): List<Song> {
        val clean = cleanPath(path)

        return songs.filter {
            cleanPath(it.folderPath) == clean
        }.sortedBy { it.title.lowercase() }
    }

    fun songsInFolder(
        path: String,
        includeSubfolders: Boolean
    ): List<Song> {
        val clean = cleanPath(path)

        return songs.filter { song ->
            val songPath = cleanPath(song.folderPath)

            if (includeSubfolders) {
                songPath == clean || songPath.startsWith("$clean/")
            } else {
                songPath == clean
            }
        }.sortedWith(
            compareBy<Song>(
                { cleanPath(it.folderPath).lowercase() },
                { it.title.lowercase() }
            )
        )
    }

    fun saveSongsToPlaylist(
        playlistName: String,
        songsToAdd: List<Song>
    ) {
        songsToAdd.forEach { song ->
            PlaylistStorage.addSong(
                context,
                playlistName,
                SavedPlaylistSong(
                    title = song.title,
                    artist = song.artist,
                    uri = song.uri.toString(),
                    folderPath = song.folderPath
                )
            )
        }

        refreshPlaylists()
    }

    fun addFolderToPlaylist(
        folder: String,
        includeSubfolders: Boolean
    ) {
        val playlistName = selectedPlaylistName ?: return

        val folderSongs = songsInFolder(
            folder,
            includeSubfolders
        )

        saveSongsToPlaylist(playlistName, folderSongs)
        pendingFolder = null
        showFolderPrompt = false
    }

    fun requestAddFolder(folder: String) {
        if (childFolders(folder).isNotEmpty()) {
            pendingFolder = folder
            showFolderPrompt = true
        } else {
            addFolderToPlaylist(folder, false)
        }
    }

    val selectedPlaylist = playlists.firstOrNull {
        it.name == selectedPlaylistName
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    if (selectedPlaylistName != null) {
                        selectedPlaylistName = null
                    } else {
                        onBack()
                    }
                }
            ) {
                Text(
                    "‹ BACK",
                    color = lightCopper,
                    fontSize = 12.sp
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "KG MUSIC",
                    color = lightCopper,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Text(
                    "PLAYLISTS",
                    color = muted,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )
            }

            if (selectedPlaylistName == null) {
                TextButton(
                    onClick = {
                        newPlaylistName = ""
                        showCreateDialog = true
                    }
                ) {
                    Text(
                        "+ CREATE",
                        color = copper,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = line)
        Spacer(Modifier.height(12.dp))

        if (selectedPlaylistName == null) {
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "♫",
                            color = copper,
                            fontSize = 38.sp
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "No playlists yet",
                            color = lightCopper,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Create a playlist to begin",
                            color = muted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(
                        playlists,
                        key = { it.name }
                    ) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPlaylistName = playlist.name
                                }
                                .padding(vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(
                                        Color(0xFF302016),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "♫",
                                    color = lightCopper,
                                    fontSize = 23.sp
                                )
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    playlist.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "${playlist.songs.size} songs",
                                    color = muted,
                                    fontSize = 10.sp
                                )
                            }

                            Text(
                                "›",
                                color = copper,
                                fontSize = 24.sp
                            )
                        }

                        HorizontalDivider(color = line)
                    }
                }
            }
        } else {
            val playlist = selectedPlaylist

            if (playlist != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            playlist.name,
                            color = lightCopper,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "${playlist.songs.size} songs",
                            color = muted,
                            fontSize = 11.sp
                        )
                    }

                    TextButton(
                        onClick = {
                            showAddDialog = true
                            browseFolder = null
                            selectedUris = emptySet()
                        }
                    ) {
                        Text(
                            "+ ADD",
                            color = copper,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val playable = playlist.songs.mapNotNull { saved ->
                                songs.firstOrNull {
                                    it.uri.toString() == saved.uri
                                }
                            }

                            if (playable.isNotEmpty()) {
                                onPlayPlaylist(playable)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = copper
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("▶ PLAY")
                    }

                    OutlinedButton(
                        onClick = {
                            PlaylistStorage.deletePlaylist(
                                context,
                                playlist.name
                            )
                            selectedPlaylistName = null
                            refreshPlaylists()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = lightCopper
                        ),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Text("DELETE")
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = line)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(
                        playlist.songs,
                        key = { it.uri }
                    ) { savedSong ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    savedSong.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (savedSong.artist.isNotBlank()) {
                                    Text(
                                        savedSong.artist,
                                        color = muted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    PlaylistStorage.removeSong(
                                        context,
                                        playlist.name,
                                        savedSong.uri
                                    )
                                    refreshPlaylists()
                                }
                            ) {
                                Text(
                                    "REMOVE",
                                    color = copper,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        HorizontalDivider(color = line)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
            },
            containerColor = panel,
            title = {
                Text(
                    "Create playlist",
                    color = lightCopper
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = {
                        newPlaylistName = it
                    },
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Playlist name",
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()

                        if (name.isNotBlank()) {
                            val created = PlaylistStorage.createPlaylist(
                                context,
                                name
                            )

                            if (created) {
                                refreshPlaylists()
                                selectedPlaylistName = name
                            }
                        }

                        showCreateDialog = false
                    }
                ) {
                    Text("CREATE", color = lightCopper)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                    }
                ) {
                    Text("CANCEL", color = muted)
                }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                browseFolder = null
                selectedUris = emptySet()
            },
            containerColor = panel,
            title = {
                Column {
                    Text(
                        "Add songs",
                        color = lightCopper,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        if (browseFolder == null) {
                            "Choose a folder or individual songs"
                        } else {
                            folderName(browseFolder!!)
                        },
                        color = muted,
                        fontSize = 11.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    if (browseFolder != null) {
                        TextButton(
                            onClick = {
                                val current = cleanPath(browseFolder!!)
                                val parent = current.substringBeforeLast(
                                    "/",
                                    missingDelimiterValue = ""
                                )

                                browseFolder = if (parent.isBlank()) {
                                    null
                                } else {
                                    parent
                                }
                            }
                        ) {
                            Text(
                                "‹ BACK TO FOLDERS",
                                color = lightCopper,
                                fontSize = 11.sp
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        val folderList = childFolders(
                            browseFolder ?: ""
                        )

                        items(folderList, key = { it }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "▣  ${folderName(folder)}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            browseFolder = folder
                                        }
                                        .padding(vertical = 8.dp)
                                )

                                TextButton(
                                    onClick = {
                                        requestAddFolder(folder)
                                    }
                                ) {
                                    Text(
                                        "ADD",
                                        color = copper,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = line)
                        }

                        val currentFolderSongs = if (browseFolder == null) {
                            emptyList()
                        } else {
                            directSongs(browseFolder!!)
                        }

                        items(
                            currentFolderSongs,
                            key = { it.uri.toString() }
                        ) { song ->
                            val uri = song.uri.toString()
                            val checked = selectedUris.contains(uri)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedUris = if (checked) {
                                            selectedUris - uri
                                        } else {
                                            selectedUris + uri
                                        }
                                    }
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedUris = if (isChecked) {
                                            selectedUris + uri
                                        } else {
                                            selectedUris - uri
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = copper,
                                        uncheckedColor = muted,
                                        checkmarkColor = Color.White
                                    )
                                )

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        song.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (song.artist.isNotBlank()) {
                                        Text(
                                            song.artist,
                                            color = muted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "${selectedUris.size} songs selected",
                        color = lightCopper,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val playlistName = selectedPlaylistName

                        if (playlistName != null) {
                            val chosenSongs = songs.filter {
                                selectedUris.contains(it.uri.toString())
                            }

                            saveSongsToPlaylist(
                                playlistName,
                                chosenSongs
                            )
                        }

                        showAddDialog = false
                        browseFolder = null
                        selectedUris = emptySet()
                    }
                ) {
                    Text("ADD SELECTED", color = lightCopper)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        browseFolder = null
                        selectedUris = emptySet()
                    }
                ) {
                    Text("DONE", color = muted)
                }
            }
        )
    }

    if (showFolderPrompt) {
        AlertDialog(
            onDismissRequest = {
                showFolderPrompt = false
                pendingFolder = null
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
                    onClick = {
                        pendingFolder?.let {
                            addFolderToPlaylist(it, true)
                        }
                    }
                ) {
                    Text("YES", color = lightCopper)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingFolder?.let {
                            addFolderToPlaylist(it, false)
                        }
                    }
                ) {
                    Text("NO", color = muted)
                }
            }
        )
    }
}

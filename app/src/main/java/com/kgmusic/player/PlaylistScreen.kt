
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

    var playlists by remember {
        mutableStateOf(PlaylistStorage.load(context))
    }

    var selectedPlaylistName by remember {
        mutableStateOf<String?>(null)
    }

    var showCreateDialog by remember {
        mutableStateOf(false)
    }

    var showAddSongsDialog by remember {
        mutableStateOf(false)
    }

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    var newPlaylistName by remember {
        mutableStateOf("")
    }

    var selectedUris by remember {
        mutableStateOf(setOf<String>())
    }

    var message by remember {
        mutableStateOf("")
    }

    val black = Color(0xFF090706)
    val copper = Color(0xFFB87333)
    val lightCopper = Color(0xFFE0A06A)
    val bronze = Color(0xFF75421F)
    val panel = Color(0xFF1C1511)
    val muted = Color(0xFFAA9380)
    val line = Color(0xFF493326)

    fun refreshPlaylists() {
        playlists = PlaylistStorage.load(context)
    }

    val selectedPlaylist = playlists.firstOrNull {
        it.name == selectedPlaylistName
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
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
                    "← BACK",
                    color = lightCopper,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "MY PLAYLISTS",
                    color = lightCopper,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Text(
                    "YOUR MUSIC. YOUR OCCASIONS.",
                    color = muted,
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = line
        )

        if (selectedPlaylist == null) {

            Button(
                onClick = {
                    newPlaylistName = ""
                    showCreateDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = bronze
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "+  CREATE NEW PLAYLIST",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "${playlists.size} SAVED PLAYLISTS",
                color = muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.height(10.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "♫",
                            color = copper,
                            fontSize = 44.sp
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "YOUR PLAYLISTS START HERE",
                            color = lightCopper,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Create a playlist for any occasion.",
                            color = muted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        playlists,
                        key = { it.name }
                    ) { playlist ->

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPlaylistName = playlist.name
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = panel
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "♫",
                                    color = lightCopper,
                                    fontSize = 27.sp
                                )

                                Spacer(Modifier.width(14.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        playlist.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )

                                    Text(
                                        "${playlist.songs.size} songs",
                                        color = muted,
                                        fontSize = 11.sp
                                    )
                                }

                                Text(
                                    "OPEN  ›",
                                    color = copper,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

        } else {

            Text(
                selectedPlaylist.name,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "${selectedPlaylist.songs.size} SONGS",
                color = muted,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        selectedUris = emptySet()
                        showAddSongsDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = bronze
                    )
                ) {
                    Text("+ ADD SONGS")
                }

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = lightCopper
                    )
                ) {
                    Text("DELETE")
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val tracks = selectedPlaylist.songs.mapNotNull { saved ->
                        runCatching {
                            Song(
                                title = saved.title,
                                artist = saved.artist,
                                uri = Uri.parse(saved.uri),
                                folderPath = saved.folderPath
                            )
                        }.getOrNull()
                    }

                    if (tracks.isNotEmpty()) {
                        onPlayPlaylist(tracks)
                    } else {
                        message = "This playlist has no songs."
                    }
                },
                enabled = selectedPlaylist.songs.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = copper
                )
            ) {
                Text(
                    "▶  PLAY THIS PLAYLIST",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            if (selectedPlaylist.songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No songs yet. Add music from your library.",
                        color = muted,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        selectedPlaylist.songs,
                        key = { it.uri }
                    ) { song ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "♫",
                                color = copper,
                                fontSize = 21.sp
                            )

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

                                Text(
                                    song.artist,
                                    color = muted,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }

                            TextButton(
                                onClick = {
                                    PlaylistStorage.removeSong(
                                        context,
                                        selectedPlaylist.name,
                                        song.uri
                                    )
                                    refreshPlaylists()
                                }
                            ) {
                                Text(
                                    "REMOVE",
                                    color = lightCopper,
                                    fontSize = 9.sp
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
                    "CREATE PLAYLIST",
                    color = lightCopper,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = copper,
                        unfocusedBorderColor = muted,
                        focusedLabelColor = lightCopper,
                        unfocusedLabelColor = muted
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val created = PlaylistStorage.createPlaylist(
                            context,
                            newPlaylistName
                        )

                        if (created) {
                            refreshPlaylists()
                            showCreateDialog = false
                            message = "Playlist created."
                        } else {
                            message = "Enter a name that is not already used."
                        }
                    }
                ) {
                    Text("CREATE", color = lightCopper)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false }
                ) {
                    Text("CANCEL", color = muted)
                }
            }
        )
    }

    if (showAddSongsDialog && selectedPlaylist != null) {
        AlertDialog(
            onDismissRequest = {
                showAddSongsDialog = false
            },
            containerColor = panel,
            title = {
                Text(
                    "ADD SONGS",
                    color = lightCopper,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (songs.isEmpty()) {
                    Text(
                        "No music was found in your library.",
                        color = muted
                    )
                } else {
                    Column {
                        Text(
                            "Select songs from your music library:",
                            color = muted,
                            fontSize = 11.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            items(
                                songs,
                                key = { it.uri.toString() }
                            ) { song ->
                                val uri = song.uri.toString()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedUris =
                                                if (uri in selectedUris) {
                                                    selectedUris - uri
                                                } else {
                                                    selectedUris + uri
                                                }
                                        }
                                        .padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = uri in selectedUris,
                                        onCheckedChange = { checked ->
                                            selectedUris =
                                                if (checked) {
                                                    selectedUris + uri
                                                } else {
                                                    selectedUris - uri
                                                }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = copper
                                        )
                                    )

                                    Column {
                                        Text(
                                            song.title,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

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
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedUris.forEach { uri ->
                            val song = songs.firstOrNull {
                                it.uri.toString() == uri
                            }

                            if (song != null) {
                                PlaylistStorage.addSong(
                                    context,
                                    selectedPlaylist.name,
                                    SavedPlaylistSong(
                                        title = song.title,
                                        artist = song.artist,
                                        uri = song.uri.toString(),
                                        folderPath = song.folderPath
                                    )
                                )
                            }
                        }

                        refreshPlaylists()
                        showAddSongsDialog = false
                        selectedUris = emptySet()
                        message = "Selected songs have been added."
                    }
                ) {
                    Text(
                        "ADD SELECTED (${selectedUris.size})",
                        color = lightCopper
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddSongsDialog = false }
                ) {
                    Text("CANCEL", color = muted)
                }
            }
        )
    }

    if (showDeleteDialog && selectedPlaylist != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            containerColor = panel,
            title = {
                Text(
                    "DELETE PLAYLIST?",
                    color = lightCopper
                )
            },
            text = {
                Text(
                    "Delete '${selectedPlaylist.name}'? This will remove the saved playlist, but will not delete your music files.",
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        PlaylistStorage.deletePlaylist(
                            context,
                            selectedPlaylist.name
                        )
                        refreshPlaylists()
                        selectedPlaylistName = null
                        showDeleteDialog = false
                    }
                ) {
                    Text("DELETE", color = lightCopper)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("CANCEL", color = muted)
                }
            }
        )
    }

    if (message.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { message = "" },
            containerColor = panel,
            text = {
                Text(message, color = Color.White)
            },
            confirmButton = {
                TextButton(onClick = { message = "" }) {
                    Text("OK", color = lightCopper)
                }
            }
        )
    }
}


package com.kgmusic.player

import androidx.activity.compose.BackHandler
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
    onPlaySong: (Song) -> Unit,
    onOpenQueue: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit
) {
    val black = Color(0xFF090706)
    val copper = Color(0xFFB87333)
    val lightCopper = Color(0xFFE0A06A)
    val bronze = Color(0xFF75421F)
    val panel = Color(0xFF1C1511)
    val muted = Color(0xFFAA9380)
    val line = Color(0xFF493326)

    var drawerOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("Home") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    // Handle phone Back before allowing the activity to close.
    BackHandler(
        enabled = drawerOpen || menuOpen ||
            searchOpen || section != "Home"
    ) {
        when {
            menuOpen -> menuOpen = false

            drawerOpen -> drawerOpen = false

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

    val folders = remember(songs) {
        songs.map { it.folderPath }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val artists = remember(songs) {
        songs.map { it.artist }
            .filter { it.isNotBlank() && it != "<unknown>" }
            .distinct()
            .sorted()
    }

    val matchingSongs = remember(
        songs, searchText, section, selectedFolder
    ) {
        val base = when (section) {
            "Folder" -> songs.filter {
                it.folderPath == selectedFolder
            }

            "Artists" -> songs

            "Folders" -> emptyList()

            else -> songs
        }

        if (searchText.isBlank()) base
        else base.filter {
            it.title.contains(searchText, ignoreCase = true) ||
                it.artist.contains(searchText, ignoreCase = true) ||
                it.folderPath.contains(searchText, ignoreCase = true)
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
        // HOME HEADER
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

            Column(
                modifier = Modifier.weight(1f)
            ) {
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

        // SEARCH
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

        // HOME OR SELECTED SECTION
        if (section == "Home") {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "◇     ◆     ◇",
                    color = bronze,
                    fontSize = 17.sp,
                    letterSpacing = 5.sp
                )
            }
        } else {
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    section.uppercase(),
                    color = lightCopper,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = {
                        section = "Home"
                        selectedFolder = null
                        searchText = ""
                        searchOpen = false
                    }
                ) {
                    Text(
                        "CLOSE  ×",
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
                        items(folders) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFolder = folder
                                        section = "Folder"
                                    }
                                    .padding(vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "▣",
                                    color = copper,
                                    fontSize = 21.sp
                                )

                                Spacer(Modifier.width(14.dp))

                                Text(
                                    folder.substringAfterLast('/'),
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
                                it.contains(
                                    searchText,
                                    ignoreCase = true
                                )
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
                            matchingSongs,
                            key = { it.uri.toString() }
                        ) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlaySong(song)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(
                                            RoundedCornerShape(10.dp)
                                        )
                                        .background(
                                            Color(0xFF302016)
                                        ),
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

                                    Text(
                                        song.artist,
                                        color = muted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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

        // PERSISTENT MINI-PLAYER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onOpenQueue()
                },
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
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
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
}

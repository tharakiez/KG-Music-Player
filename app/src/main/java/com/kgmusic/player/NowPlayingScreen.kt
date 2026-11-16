
package com.kgmusic.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private val KGBlack = Color(0xFF090706)
private val KGCopper = Color(0xFFB87333)
private val KGLightGold = Color(0xFFE0B16D)
private val KGPanel = Color(0xFF1C1511)
private val KGMuted = Color(0xFFAA9380)

@Composable
fun NowPlayingScreen(
    currentSong: String,
    isPlaying: Boolean,
    connected: Boolean,
    queue: List<QueueTrack>,
    currentQueueIndex: Int,
    shuffle: Boolean,
    repeatMode: Int,
    controller: MediaController?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onSelectQueueItem: (Int) -> Unit
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    var showSleepDialog by remember { mutableStateOf(false) }
    var sleepLabel by remember { mutableStateOf("Sleep timer") }
    var sleepJob by remember { mutableStateOf<Job?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                val actualDuration = it.duration

                if (actualDuration > 0L &&
                    actualDuration != C.TIME_UNSET
                ) {
                    duration = actualDuration
                }

                if (!dragging) {
                    position = it.currentPosition.coerceAtLeast(0L)
                    sliderPosition = position.toFloat()
                }

                speed = it.playbackParameters.speed
            }

            delay(400)
        }
    }

    fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(minutes, seconds)
    }

    fun setTempo(newSpeed: Float) {
        speed = newSpeed

        controller?.setPlaybackParameters(
            PlaybackParameters(newSpeed, 1f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KGBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {

        // TOP BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = KGLightGold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "KG MUSIC",
                    color = KGLightGold,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Text(
                    "AFRICAN RHYTHM",
                    color = KGMuted,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )
            }

            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Equalizer,
                    contentDescription = "Settings",
                    tint = KGLightGold
                )
            }

            IconButton(onClick = onOpenPlaylists) {
                Icon(
                    Icons.Default.PlaylistPlay,
                    contentDescription = "Playlists",
                    tint = KGLightGold
                )
            }

            IconButton(onClick = { showSleepDialog = true }) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "Sleep timer",
                    tint = KGLightGold
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // MAIN PLAYER CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = KGPanel
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                KGCopper.copy(alpha = 0.75f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    "BANJUKA ARTS",
                    color = KGLightGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )

                Spacer(Modifier.height(10.dp))

                // YOUR PORTRAIT
                Image(
                    painter = painterResource(
                        id = R.drawable.kg_portrait
                    ),
                    contentDescription = "KG Music artwork",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            1.dp,
                            KGCopper,
                            RoundedCornerShape(18.dp)
                        ),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    currentSong,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    queue.getOrNull(currentQueueIndex)?.artist
                        ?: "Unknown artist",
                    color = KGMuted,
                    fontSize = 12.sp,
                    maxLines = 1
                )

                Spacer(Modifier.height(8.dp))

                // SEEK BAR
                Slider(
                    value = sliderPosition.coerceIn(
                        0f,
                        duration.coerceAtLeast(1L).toFloat()
                    ),
                    onValueChange = {
                        dragging = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        controller?.seekTo(
                            sliderPosition.roundToLong()
                        )

                        position = sliderPosition.roundToLong()
                        dragging = false
                    },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    enabled = connected && duration > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = KGLightGold,
                        activeTrackColor = KGCopper,
                        inactiveTrackColor = KGMuted
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTime(position),
                        color = KGMuted,
                        fontSize = 10.sp
                    )

                    Text(
                        formatTime(duration),
                        color = KGMuted,
                        fontSize = 10.sp
                    )
                }

                Spacer(Modifier.height(6.dp))

                // FIXED TEMPO AND 10-SECOND SEEK CONTROLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            controller?.let {
                                it.seekTo(
                                    (it.currentPosition - 10_000L)
                                        .coerceAtLeast(0L)
                                )
                            }
                        },
                        enabled = connected
                    ) {
                        Text(
                            "−10",
                            color = KGLightGold,
                            fontSize = 12.sp
                        )
                    }

                    TextButton(
                        onClick = { setTempo(0.8f) },
                        enabled = connected
                    ) {
                        Text(
                            "80%",
                            color = if (speed == 0.8f) {
                                KGLightGold
                            } else {
                                KGMuted
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "${(speed * 100).toInt()}%",
                        color = KGLightGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = { setTempo(1.2f) },
                        enabled = connected
                    ) {
                        Text(
                            "120%",
                            color = if (speed == 1.2f) {
                                KGLightGold
                            } else {
                                KGMuted
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = {
                            controller?.let {
                                it.seekTo(
                                    (it.currentPosition + 10_000L)
                                        .coerceAtMost(duration)
                                )
                            }
                        },
                        enabled = connected
                    ) {
                        Text(
                            "+10",
                            color = KGLightGold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // MAIN PLAYBACK CONTROLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onShuffleChange(!shuffle)
                        }
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffle) KGLightGold else KGMuted
                        )
                    }

                    IconButton(
                        onClick = {
                            controller?.seekToPrevious()
                        },
                        enabled = connected
                    ) {
                        Text(
                            "⏮",
                            color = KGLightGold,
                            fontSize = 23.sp
                        )
                    }

                    Button(
                        onClick = {
                            controller?.let {
                                if (it.isPlaying) {
                                    it.pause()
                                } else {
                                    it.play()
                                }
                            }
                        },
                        enabled = connected,
                        modifier = Modifier.size(
                            width = 66.dp,
                            height = 50.dp
                        ),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KGCopper
                        )
                    ) {
                        Text(
                            if (isPlaying) "Ⅱ" else "▶",
                            color = Color.White,
                            fontSize = 23.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            controller?.seekToNext()
                        },
                        enabled = connected
                    ) {
                        Text(
                            "⏭",
                            color = KGLightGold,
                            fontSize = 23.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            onRepeatChange((repeatMode + 1) % 3)
                        }
                    ) {
                        Icon(
                            if (repeatMode == 2) {
                                Icons.Default.RepeatOne
                            } else {
                                Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (repeatMode == 0) {
                                KGMuted
                            } else {
                                KGLightGold
                            }
                        )
                    }
                }

                Text(
                    when (repeatMode) {
                        1 -> "REPEAT PLAYLIST"
                        2 -> "REPEAT ONE"
                        else -> "REPEAT OFF"
                    },
                    color = KGMuted,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )

                Text(
                    sleepLabel,
                    color = KGLightGold,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // PLAYING QUEUE
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PLAYING QUEUE",
                color = KGLightGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )

            Text(
                "${queue.size} TRACKS",
                color = KGMuted,
                fontSize = 10.sp
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            itemsIndexed(
                queue,
                key = { index, track -> "$index-${track.uri}" }
            ) { index, track ->

                val current = index == currentQueueIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (current) {
                                Color(0xFF302016)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable {
                            onSelectQueueItem(index)
                        }
                        .padding(
                            horizontal = 10.dp,
                            vertical = 9.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (current) {
                            KGLightGold
                        } else {
                            KGCopper
                        }
                    )

                    Spacer(Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            track.title,
                            color = if (current) {
                                KGLightGold
                            } else {
                                Color.White
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            track.artist,
                            color = KGMuted,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }

                    if (current) {
                        Text(
                            "PLAYING",
                            color = KGLightGold,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }

    // SLEEP TIMER
    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = {
                showSleepDialog = false
            },
            containerColor = KGPanel,
            title = {
                Text(
                    "SLEEP TIMER",
                    color = KGLightGold
                )
            },
            text = {
                Column {
                    Text(
                        "Pause music after:",
                        color = Color.White
                    )

                    listOf(15, 30, 45, 60).forEach { minutes ->
                        TextButton(
                            onClick = {
                                sleepJob?.cancel()
                                sleepLabel = "Sleep in $minutes min"

                                sleepJob = scope.launch {
                                    delay(minutes * 60_000L)
                                    controller?.pause()
                                    sleepLabel = "Sleep timer finished"
                                }

                                showSleepDialog = false
                            }
                        ) {
                            Text(
                                "$minutes minutes",
                                color = KGLightGold
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            sleepJob?.cancel()
                            sleepJob = null
                            sleepLabel = "Sleep timer"
                            showSleepDialog = false
                        }
                    ) {
                        Text(
                            "CANCEL TIMER",
                            color = KGMuted
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepDialog = false
                    }
                ) {
                    Text(
                        "CLOSE",
                        color = KGLightGold
                    )
                }
            }
        )
    }
}

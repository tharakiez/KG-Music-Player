
package com.kgmusic.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    audioEffects: AudioEffects? = null
) {
    val gold = Color(0xFFFFC400)
    val black = Color(0xFF080808)

    val frequencies = listOf(
        "31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz",
        "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
    )

    val levels = remember {
        mutableStateListOf<Float>().apply {
            repeat(10) { add(0f) }
        }
    }

    var bass by remember { mutableFloatStateOf(0f) }
    var volume by remember { mutableFloatStateOf(100f) }
    var preset by remember { mutableStateOf("Normal") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← BACK", color = gold)
        }

        Text(
            "KG SOUND ENGINE",
            color = gold,
            fontSize = 25.sp
        )

        Text(
            "10-BAND EQUALIZER",
            color = Color.White,
            fontSize = 17.sp
        )

        Spacer(Modifier.height(15.dp))

        Text("PRESETS", color = gold)

        var expanded by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(preset, color = Color.White)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                listOf(
                    "Normal", "Rock", "Pop", "Jazz",
                    "Classical", "Dance", "Bass"
                ).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            preset = option
                            val values = when (option) {
                                "Rock" -> listOf(4f, 3f, -1f, -2f, 1f, 3f, 4f, 4f, 3f, 3f)
                                "Pop" -> listOf(-1f, 2f, 4f, 4f, 1f, -1f, -1f, 0f, 2f, 2f)
                                "Jazz" -> listOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 2f, 3f, 4f)
                                "Classical" -> listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)
                                "Dance" -> listOf(5f, 4f, 2f, 0f, 1f, 3f, 4f, 3f, 2f, 2f)
                                "Bass" -> listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)
                                else -> List(10) { 0f }
                            }

                            values.forEachIndexed { index, value ->
                                levels[index] = value
                                audioEffects?.setEqualizerBand(
                                    index,
                                    (value * 100).toInt()
                                )
                            }

                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        frequencies.forEachIndexed { index, frequency ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(frequency, color = Color.White)
                    Text(
                        "${levels[index].toInt()} dB",
                        color = gold
                    )
                }

                Slider(
                    value = levels[index],
                    onValueChange = {
                        levels[index] = it
                        audioEffects?.setEqualizerBand(
                            index,
                            (it * 100).toInt()
                        )
                    },
                    valueRange = -15f..15f,
                    colors = SliderDefaults.colors(
                        thumbColor = gold,
                        activeTrackColor = gold
                    )
                )
            }
        }

        HorizontalDivider(color = Color.DarkGray)

        Spacer(Modifier.height(15.dp))

        Text("BASS BOOST", color = gold)

        Slider(
            value = bass,
            onValueChange = {
                bass = it
                audioEffects?.setBassBoost((it * 10).toInt())
            },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = gold,
                activeTrackColor = gold
            )
        )

        Text("${bass.toInt()}%", color = Color.White)

        Spacer(Modifier.height(15.dp))

        Text("VOLUME BOOST", color = gold)

        Slider(
            value = volume,
            onValueChange = {
                volume = it
                audioEffects?.setVolumeBoost(it.toInt())
            },
            valueRange = 100f..200f,
            colors = SliderDefaults.colors(
                thumbColor = gold,
                activeTrackColor = gold
            )
        )

        Text(
            "${volume.toInt()}%",
            color = gold,
            fontSize = 22.sp
        )

        Text(
            "High volume may cause distortion or hearing damage.",
            color = Color.LightGray,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                levels.indices.forEach {
                    levels[it] = 0f
                    audioEffects?.setEqualizerBand(it, 0)
                }

                bass = 0f
                volume = 100f
                preset = "Normal"

                audioEffects?.setBassBoost(0)
                audioEffects?.setVolumeBoost(100)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("RESET SOUND SETTINGS")
        }
    }
}

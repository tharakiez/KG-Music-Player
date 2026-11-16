
package com.kgmusic.player

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.sin

private val EQBlack = Color(0xFF090706)
private val EQPanel = Color(0xFF1C1511)
private val EQCopper = Color(0xFFB87333)
private val EQGold = Color(0xFFE0B16D)
private val EQMuted = Color(0xFFAA9380)

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    audioEffects: AudioEffects? = null
) {
    val context = LocalContext.current

    val preferences = remember(context) {
        context.getSharedPreferences(
            "kg_sound_settings",
            Context.MODE_PRIVATE
        )
    }

    val frequencies = listOf(
        "31", "62", "125", "250", "500",
        "1k", "2k", "4k", "8k", "16k"
    )

    val presets = listOf(
        "Normal", "Rock", "Pop", "Jazz",
        "Classical", "Dance", "Bass"
    )

    val levels = remember {
        mutableStateListOf<Float>().apply {
            repeat(10) { index ->
                add(preferences.getFloat("eq_$index", 0f))
            }
        }
    }

    var bass by remember {
        mutableFloatStateOf(
            preferences.getFloat("bass", 0f)
        )
    }

    var volume by remember {
        mutableFloatStateOf(
            preferences.getFloat("volume", 100f)
                .coerceIn(100f, 200f)
        )
    }

    var volumeDial by remember {
        mutableFloatStateOf(volume)
    }

    var pendingVolume by remember {
        mutableFloatStateOf(volume)
    }

    var showVolumeWarning by remember {
        mutableStateOf(false)
    }

    var preset by remember {
        mutableStateOf(
            preferences.getString("preset", "Normal") ?: "Normal"
        )
    }

    fun saveLevels() {
        preferences.edit().apply {
            levels.forEachIndexed { index, value ->
                putFloat("eq_$index", value)
            }
            putString("preset", preset)
        }.apply()
    }

    fun applyPreset(name: String) {
        val values = when (name) {
            "Rock" -> listOf(
                4f, 3f, -1f, -2f, 1f,
                3f, 4f, 4f, 3f, 3f
            )

            "Pop" -> listOf(
                -1f, 2f, 4f, 4f, 1f,
                -1f, -1f, 0f, 2f, 2f
            )

            "Jazz" -> listOf(
                3f, 2f, 1f, 2f, -2f,
                -2f, 0f, 2f, 3f, 4f
            )

            "Classical" -> listOf(
                4f, 3f, 2f, 1f, -1f,
                -1f, 0f, 2f, 3f, 4f
            )

            "Dance" -> listOf(
                5f, 4f, 2f, 0f, 1f,
                3f, 4f, 3f, 2f, 2f
            )

            "Bass" -> listOf(
                6f, 5f, 4f, 2f, 0f,
                0f, 0f, 0f, 0f, 0f
            )

            else -> List(10) { 0f }
        }

        preset = name

        values.forEachIndexed { index, value ->
            levels[index] = value
            audioEffects?.setEqualizerBand(
                index,
                (value * 100).toInt()
            )
        }

        saveLevels()
    }

    fun applyVolume(value: Float) {
        volume = value.coerceIn(100f, 200f)
        volumeDial = volume

        audioEffects?.setVolumeBoost(volume.toInt())

        preferences.edit()
            .putFloat("volume", volume)
            .apply()
    }

    fun changeBass(value: Float) {
        bass = value.coerceIn(0f, 100f)

        audioEffects?.setBassBoost(
            (bass * 10).toInt()
        )

        preferences.edit()
            .putFloat("bass", bass)
            .apply()
    }

    LaunchedEffect(audioEffects) {
        audioEffects?.let { effects ->
            levels.forEachIndexed { index, value ->
                effects.setEqualizerBand(
                    index,
                    (value * 100).toInt()
                )
            }

            effects.setBassBoost((bass * 10).toInt())
            effects.setVolumeBoost(volume.toInt())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EQBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← BACK", color = EQGold)
        }

        Text(
            "KG SOUND ENGINE",
            color = EQGold,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Text(
            "SHAPE YOUR SOUND",
            color = EQMuted,
            fontSize = 10.sp,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.height(18.dp))

        Text(
            "SOUND PRESETS",
            color = EQGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            presets.forEach { option ->
                val selected = preset == option

                Surface(
                    onClick = { applyPreset(option) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) EQCopper else EQPanel,
                    border = BorderStroke(
                        1.dp,
                        if (selected) EQGold else EQCopper.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        option,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = 13.dp,
                            vertical = 9.dp
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = EQPanel
            ),
            border = BorderStroke(1.dp, EQCopper.copy(alpha = 0.6f))
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "10-BAND EQUALIZER",
                    color = EQGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Fine tune each frequency",
                    color = EQMuted,
                    fontSize = 10.sp
                )

                Spacer(Modifier.height(10.dp))

                for (rowIndex in 0 until 5) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (columnIndex in 0 until 2) {
                            val index = rowIndex * 2 + columnIndex

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${frequencies[index]} Hz",
                                        color = EQMuted,
                                        fontSize = 10.sp
                                    )

                                    Text(
                                        "${levels[index].toInt()} dB",
                                        color = EQGold,
                                        fontSize = 10.sp
                                    )
                                }

                                Slider(
                                    value = levels[index],
                                    onValueChange = { value ->
                                        levels[index] = value
                                        preset = "Custom"

                                        audioEffects?.setEqualizerBand(
                                            index,
                                            (value * 100).toInt()
                                        )
                                    },
                                    onValueChangeFinished = {
                                        saveLevels()
                                    },
                                    valueRange = -15f..15f,
                                    modifier = Modifier.height(30.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = EQGold,
                                        activeTrackColor = EQCopper,
                                        inactiveTrackColor = EQMuted.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "SOUND ENHANCEMENT",
            color = EQGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(9.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = EQPanel
                ),
                border = BorderStroke(1.dp, EQCopper)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "BASS BOOST",
                        color = EQGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(7.dp))

                    BoostDial(
                        value = bass,
                        maximum = 100f,
                        display = "${bass.toInt()}%",
                        modifier = Modifier.size(104.dp)
                    )

                    Slider(
                        value = bass,
                        onValueChange = { changeBass(it) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = EQGold,
                            activeTrackColor = EQCopper,
                            inactiveTrackColor = EQMuted.copy(alpha = 0.3f)
                        )
                    )

                    Text(
                        "BASS POWER",
                        color = EQMuted,
                        fontSize = 9.sp
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = EQPanel
                ),
                border = BorderStroke(1.dp, EQCopper)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "VOLUME BOOST",
                        color = EQGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(7.dp))

                    BoostDial(
                        value = volumeDial - 100f,
                        maximum = 100f,
                        display = "${volumeDial.toInt()}%",
                        modifier = Modifier.size(104.dp)
                    )

                    Slider(
                        value = volumeDial,
                        onValueChange = { volumeDial = it },
                        onValueChangeFinished = {
                            if (volumeDial > 100f) {
                                pendingVolume = volumeDial
                                volumeDial = volume
                                showVolumeWarning = true
                            } else {
                                applyVolume(volumeDial)
                            }
                        },
                        valueRange = 100f..200f,
                        colors = SliderDefaults.colors(
                            thumbColor = EQGold,
                            activeTrackColor = EQCopper,
                            inactiveTrackColor = EQMuted.copy(alpha = 0.3f)
                        )
                    )

                    Text(
                        "100% = NORMAL",
                        color = EQMuted,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Volume above 100% requires your confirmation.",
            color = EQMuted,
            fontSize = 10.sp
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                applyPreset("Normal")
                changeBass(0f)
                applyVolume(100f)
                pendingVolume = 100f
                volumeDial = 100f
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EQCopper
            )
        ) {
            Text(
                "RESET SOUND SETTINGS",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))
    }

    if (showVolumeWarning) {
        AlertDialog(
            onDismissRequest = {
                showVolumeWarning = false
                volumeDial = volume
            },
            containerColor = EQPanel,
            title = {
                Text(
                    "HIGH VOLUME WARNING",
                    color = EQGold,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "You requested ${pendingVolume.toInt()}% volume. " +
                        "High volume can damage hearing or cause audio distortion. " +
                        "Do you want to proceed?",
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        applyVolume(pendingVolume)
                        showVolumeWarning = false
                    }
                ) {
                    Text(
                        "YES, PROCEED",
                        color = EQGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showVolumeWarning = false
                        volumeDial = volume
                    }
                ) {
                    Text(
                        "NO, CANCEL",
                        color = EQMuted
                    )
                }
            }
        )
    }
}

@Composable
private fun BoostDial(
    value: Float,
    maximum: Float,
    display: String,
    modifier: Modifier = Modifier
) {
    val progress = (value / maximum).coerceIn(0f, 1f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )

            drawArc(
                color = EQMuted.copy(alpha = 0.25f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(
                    diameter,
                    diameter
                ),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )

            drawArc(
                color = EQGold,
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(
                    diameter,
                    diameter
                ),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )

            val angle = Math.toRadians(
                (135f + 270f * progress).toDouble()
            )

            val radius = diameter / 2f
            val center = Offset(
                size.width / 2f,
                size.height / 2f
            )

            val end = Offset(
                center.x + cos(angle).toFloat() * radius * 0.72f,
                center.y + sin(angle).toFloat() * radius * 0.72f
            )

            drawLine(
                color = EQCopper,
                start = center,
                end = end,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawCircle(
                color = EQGold,
                radius = 5.dp.toPx(),
                center = center
            )
        }

        Text(
            display,
            color = EQGold,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

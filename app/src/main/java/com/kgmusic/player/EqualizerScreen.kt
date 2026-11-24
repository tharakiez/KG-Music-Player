
package com.kgmusic.player

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val EQBlack = Color(0xFF090706)
private val EQPanel = Color(0xFF1C1511)
private val EQCopper = Color(0xFFB87333)
private val EQGold = Color(0xFFE0A06A)
private val EQMuted = Color(0xFF9B8B7A)
private val EQGreen = Color(0xFF70D69A)

private val eqLabels = listOf(
    "31", "62", "125", "250", "500",
    "1k", "2k", "4k", "8k", "16k"
)

private val eqPresets = linkedMapOf(
    "Normal" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Lounge" to listOf(3f, 2f, 1f, 0f, 0f, 1f, 2f, 2f, 1f, 0f),
    "Jazz" to listOf(3f, 2f, 1f, 2f, -1f, -1f, 1f, 2f, 3f, 4f),
    "Rap" to listOf(4f, 4f, 2f, 1f, 0f, 1f, 2f, 2f, 3f, 3f),
    "Rock" to listOf(4f, 3f, 2f, 0f, -1f, 1f, 2f, 3f, 4f, 4f),
    "Pop" to listOf(-1f, 0f, 2f, 3f, 4f, 3f, 1f, 0f, -1f, -1f),
    "Classical" to listOf(4f, 3f, 2f, 1f, -1f, -1f, 1f, 2f, 3f, 3f),
    "Dance" to listOf(4f, 3f, 1f, 2f, 3f, 2f, 1f, 2f, 3f, 3f),
    "Bass" to listOf(6f, 5f, 4f, 2f, 1f, 0f, 0f, 0f, 0f, 0f)
)

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    audioEffects: AudioEffects? = null
) {
    val context = LocalContext.current

    val prefs = remember(context) {
        context.getSharedPreferences(
            "kg_sound_settings",
            Context.MODE_PRIVATE
        )
    }

    val processor = PlaybackService.tenBandEqualizer

    var eqEnabled by remember {
        mutableStateOf(prefs.getBoolean("eq_enabled", true))
    }
    var bassEnabled by remember {
        mutableStateOf(prefs.getBoolean("bass_enabled", true))
    }
    var virtualizerEnabled by remember {
        mutableStateOf(prefs.getBoolean("virtualizer_enabled", true))
    }
    var reverbEnabled by remember {
        mutableStateOf(prefs.getBoolean("reverb_enabled", false))
    }
    var volumeEnabled by remember {
        mutableStateOf(prefs.getBoolean("volume_enabled", true))
    }

    val levels = remember {
        mutableStateListOf<Float>().apply {
            repeat(10) { index ->
                val oldValue = if (index % 2 == 1) {
                    prefs.getFloat("eq_$index", 0f)
                } else {
                    0f
                }
                add(
                    prefs.getFloat("ten_eq_$index", oldValue)
                        .coerceIn(-12f, 12f)
                )
            }
        }
    }

    val bandEnabled = remember {
        mutableStateListOf<Boolean>().apply {
            repeat(10) { index ->
                add(
                    prefs.getBoolean(
                        "ten_band_enabled_$index",
                        if (index % 2 == 1) {
                            prefs.getBoolean("band_${index / 2}", true)
                        } else {
                            true
                        }
                    )
                )
            }
        }
    }

    var preamp by remember {
        mutableFloatStateOf(
            prefs.getFloat("eq_preamp", 0f).coerceIn(-12f, 6f)
        )
    }
    var bass by remember {
        mutableFloatStateOf(prefs.getFloat("bass", 0f).coerceIn(0f, 100f))
    }
    var virtualizer by remember {
        mutableFloatStateOf(
            prefs.getFloat("virtualizer", 0f).coerceIn(0f, 100f)
        )
    }
    var reverb by remember {
        mutableFloatStateOf(
            prefs.getFloat("reverb", 35f).coerceIn(0f, 100f)
        )
    }
    var volume by remember {
        mutableFloatStateOf(
            prefs.getInt("volume", 100).toFloat().coerceIn(0f, 200f)
        )
    }
    var preset by remember {
        mutableStateOf(prefs.getString("preset", "Normal") ?: "Normal")
    }

    fun savePower() {
        prefs.edit()
            .putBoolean("eq_enabled", eqEnabled)
            .putBoolean("bass_enabled", bassEnabled)
            .putBoolean("virtualizer_enabled", virtualizerEnabled)
            .putBoolean("reverb_enabled", reverbEnabled)
            .putBoolean("volume_enabled", volumeEnabled)
            .apply()
    }

    fun updateBand(index: Int, value: Float) {
        levels[index] = value.coerceIn(-12f, 12f)
        preset = "Custom"

        processor.setBandGain(
            index,
            if (bandEnabled[index]) levels[index] else 0f
        )

        prefs.edit()
            .putFloat("ten_eq_$index", levels[index])
            .putString("preset", preset)
            .apply()
    }

    fun toggleBand(index: Int) {
        bandEnabled[index] = !bandEnabled[index]

        processor.setBandGain(
            index,
            if (bandEnabled[index]) levels[index] else 0f
        )

        prefs.edit()
            .putBoolean("ten_band_enabled_$index", bandEnabled[index])
            .apply()
    }

    fun applyPreset(name: String) {
        val values = eqPresets[name] ?: return
        preset = name

        values.forEachIndexed { index, value ->
            levels[index] = value
            processor.setBandGain(
                index,
                if (bandEnabled[index]) value else 0f
            )
            prefs.edit().putFloat("ten_eq_$index", value).apply()
        }

        prefs.edit().putString("preset", name).apply()
    }

    fun changePreamp(value: Float) {
        preamp = value.coerceIn(-12f, 6f)
        processor.setPreamp(preamp)
        prefs.edit().putFloat("eq_preamp", preamp).apply()
    }

    fun changeBass(value: Float) {
        bass = value.coerceIn(0f, 100f)
        if (bassEnabled) {
            audioEffects?.setBassBoost((bass * 10).roundToInt())
        }
        prefs.edit().putFloat("bass", bass).apply()
    }

    fun changeVirtualizer(value: Float) {
        virtualizer = value.coerceIn(0f, 100f)
        if (virtualizerEnabled) {
            audioEffects?.setVirtualizer((virtualizer * 10).roundToInt())
        }
        prefs.edit().putFloat("virtualizer", virtualizer).apply()
    }

    fun changeReverb(value: Float) {
        reverb = value.coerceIn(0f, 100f)
        if (reverbEnabled) {
            audioEffects?.setReverbStrength(reverb.roundToInt())
        }
        prefs.edit().putFloat("reverb", reverb).apply()
    }

    fun changeVolume(value: Float) {
        volume = value.coerceIn(0f, 200f)
        audioEffects?.setVolumeLevel(volume.roundToInt())
        prefs.edit().putInt("volume", volume.roundToInt()).apply()
    }

    LaunchedEffect(Unit) {
        processor.setEnabled(eqEnabled)
        processor.setPreamp(preamp)

        levels.forEachIndexed { index, value ->
            processor.setBandGain(
                index,
                if (bandEnabled[index]) value else 0f
            )
        }

        audioEffects?.let {
            it.setBassEnabled(bassEnabled)
            it.setBassBoost((bass * 10).roundToInt())
            it.setVirtualizerEnabled(virtualizerEnabled)
            it.setVirtualizer((virtualizer * 10).roundToInt())
            it.setReverbEnabled(reverbEnabled)
            it.setReverbStrength(reverb.roundToInt())
            it.setVolumeEnabled(volumeEnabled)
            it.setVolumeLevel(volume.roundToInt())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EQBlack)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("← BACK", color = EQGold, fontWeight = FontWeight.Bold)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "KG SOUND CONSOLE",
                    color = EQGold,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "10-BAND AUDIO CONTROL",
                    color = EQMuted,
                    fontSize = 8.sp,
                    letterSpacing = 2.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EQ", color = EQMuted, fontSize = 10.sp)
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = {
                        eqEnabled = it
                        processor.setEnabled(it)
                        savePower()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EQGreen,
                        checkedTrackColor = EQCopper
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            eqPresets.keys.forEach { name ->
                val selected = preset == name
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) EQCopper else EQPanel,
                            RoundedCornerShape(5.dp)
                        )
                        .clickable { applyPreset(name) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.uppercase(),
                        color = if (selected) Color.White else EQMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(5f)
                    .fillMaxHeight()
                    .background(EQPanel, RoundedCornerShape(9.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "GRAPHIC EQUALIZER",
                        color = EQGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "−12 dB                         +12 dB",
                        color = EQMuted,
                        fontSize = 8.sp
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    levels.forEachIndexed { index, level ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "%+.0f".format(level),
                                color = EQGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )

                            VerticalBandFader(
                                value = level,
                                enabled = bandEnabled[index],
                                modifier = Modifier
                                    .weight(1f)
                                    .width(28.dp)
                                    .padding(vertical = 3.dp),
                                onValueChange = { updateBand(index, it) }
                            )

                            Text(
                                eqLabels[index],
                                color = EQMuted,
                                fontSize = 9.sp
                            )

                            Text(
                                if (bandEnabled[index]) "ON" else "OFF",
                                color = if (bandEnabled[index]) EQGreen else EQMuted,
                                fontSize = 8.sp,
                                modifier = Modifier.clickable {
                                    toggleBand(index)
                                }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .background(EQPanel, RoundedCornerShape(9.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("PREAMP", color = EQGold, fontSize = 10.sp)
                Text("%.1f dB".format(preamp), color = EQGreen, fontSize = 11.sp)

                Slider(
                    value = preamp,
                    onValueChange = { changePreamp(it) },
                    valueRange = -12f..6f,
                    modifier = Modifier
                        .weight(1f)
                        .width(34.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = EQGold,
                        activeTrackColor = EQCopper,
                        inactiveTrackColor = EQBlack
                    )
                )
                Text("OUTPUT", color = EQMuted, fontSize = 8.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            EffectPanel(
                title = "BASS BOOST",
                value = bass,
                enabled = bassEnabled,
                onEnabled = {
                    bassEnabled = it
                    audioEffects?.setBassEnabled(it)
                    if (it) audioEffects?.setBassBoost((bass * 10).roundToInt())
                    savePower()
                },
                onValue = { changeBass(it) },
                modifier = Modifier.weight(1f)
            )

            EffectPanel(
                title = "VIRTUALIZER",
                value = virtualizer,
                enabled = virtualizerEnabled,
                onEnabled = {
                    virtualizerEnabled = it
                    audioEffects?.setVirtualizerEnabled(it)
                    if (it) audioEffects?.setVirtualizer((virtualizer * 10).roundToInt())
                    savePower()
                },
                onValue = { changeVirtualizer(it) },
                modifier = Modifier.weight(1f)
            )

            EffectPanel(
                title = "ROOM REVERB",
                value = reverb,
                enabled = reverbEnabled,
                onEnabled = {
                    reverbEnabled = it
                    audioEffects?.setReverbEnabled(it)
                    if (it) audioEffects?.setReverbStrength(reverb.roundToInt())
                    savePower()
                },
                onValue = { changeReverb(it) },
                modifier = Modifier.weight(1f)
            )

            Column(
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight()
                    .background(EQPanel, RoundedCornerShape(9.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MASTER VOLUME", color = EQGold, fontSize = 9.sp)
                    Text(
                        "${volume.roundToInt()}%",
                        color = EQGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = volume,
                    onValueChange = { changeVolume(it) },
                    valueRange = 0f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = EQGold,
                        activeTrackColor = EQCopper,
                        inactiveTrackColor = EQBlack
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("VOLUME EFFECT", color = EQMuted, fontSize = 8.sp)
                    Switch(
                        checked = volumeEnabled,
                        onCheckedChange = {
                            volumeEnabled = it
                            audioEffects?.setVolumeEnabled(it)
                            savePower()
                        },
                        modifier = Modifier.height(28.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EQGreen,
                            checkedTrackColor = EQCopper
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .width(76.dp)
                    .fillMaxHeight()
                    .background(EQPanel, RoundedCornerShape(9.dp))
                    .clickable {
                        levels.indices.forEach { index ->
                            levels[index] = 0f
                            bandEnabled[index] = true
                            processor.setBandGain(index, 0f)
                            prefs.edit()
                                .putFloat("ten_eq_$index", 0f)
                                .putBoolean("ten_band_enabled_$index", true)
                                .apply()
                        }

                        preamp = 0f
                        processor.setPreamp(0f)
                        preset = "Normal"
                        prefs.edit()
                            .putFloat("eq_preamp", 0f)
                            .putString("preset", "Normal")
                            .apply()
                    }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("↺", color = EQGold, fontSize = 25.sp)
                Text(
                    "RESET",
                    color = EQGold,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("SOUND", color = EQMuted, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun EffectPanel(
    title: String,
    value: Float,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    onValue: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(EQPanel, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = EQGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (enabled) "ON" else "OFF",
                color = if (enabled) EQGreen else EQMuted,
                fontSize = 8.sp,
                modifier = Modifier.clickable { onEnabled(!enabled) }
            )
        }

        Text(
            "${value.roundToInt()}%",
            color = EQGreen,
            fontSize = 11.sp
        )

        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = 0f..100f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = EQGold,
                activeTrackColor = EQCopper,
                inactiveTrackColor = EQBlack,
                disabledThumbColor = EQMuted
            )
        )
    }
}

@Composable
private fun VerticalBandFader(
    value: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    Canvas(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput

            fun update(y: Float) {
    val usable = size.height.toFloat().coerceAtLeast(1f)
    val fraction = (1f - (y / usable)).coerceIn(0f, 1f)
    onValueChange(-12f + fraction * 24f)
            }

            detectTapGestures { update(it.y) }
        }.pointerInput(enabled) {
            if (!enabled) return@pointerInput

            detectDragGestures { change, _ ->
    change.consume()
    val height = size.height.toFloat().coerceAtLeast(1f)
    val fraction = (
        1f - change.position.y / height
    ).coerceIn(0f, 1f)
    onValueChange(-12f + fraction * 24f)
            }
        }
    ) {
        val centerX = size.width / 2f
        val top = 5f
        val bottom = size.height - 5f
        val centerY = (top + bottom) / 2f
        val fraction = ((value + 12f) / 24f).coerceIn(0f, 1f)
        val thumbY = bottom - fraction * (bottom - top)

        drawLine(
            color = EQMuted.copy(alpha = 0.6f),
            start = Offset(centerX, top),
            end = Offset(centerX, bottom),
            strokeWidth = 5f
        )

        drawLine(
            color = EQCopper,
            start = Offset(centerX, centerY),
            end = Offset(centerX, thumbY),
            strokeWidth = 5f
        )

        drawLine(
            color = if (enabled) EQGold else EQMuted,
            start = Offset(centerX - 9f, thumbY),
            end = Offset(centerX + 9f, thumbY),
            strokeWidth = 5f
        )

        drawCircle(
            color = if (enabled) EQGold else EQMuted,
            radius = 6f,
            center = Offset(centerX, thumbY)
        )
    }
}

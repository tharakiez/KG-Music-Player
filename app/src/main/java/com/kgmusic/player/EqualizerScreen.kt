
package com.kgmusic.player

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private val EQBlack = Color(0xFF090909)
private val EQPanel = Color(0xFF191715)
private val EQMetal = Color(0xFF302820)
private val EQCopper = Color(0xFFB87333)
private val EQGold = Color(0xFFE5B76C)
private val EQMuted = Color(0xFF9B8B7A)
private val EQGreen = Color(0xFF70D69A)

private enum class SoundMode { EQ, VOLUME }

private val bandLabels = listOf(
    "31", "62", "125", "250", "500",
    "1k", "2k", "4k", "8k", "16k"
)

private val soundPresets = mapOf(
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

    val preferences = remember(context) {
        context.getSharedPreferences(
            "kg_sound_settings",
            Context.MODE_PRIVATE
        )
    }

    val processor = PlaybackService.tenBandEqualizer

    var mode by remember { mutableStateOf(SoundMode.EQ) }

    var eqEnabled by remember {
        mutableStateOf(preferences.getBoolean("eq_enabled", true))
    }
    var volumeEnabled by remember {
        mutableStateOf(preferences.getBoolean("volume_enabled", true))
    }
    var bassEnabled by remember {
        mutableStateOf(preferences.getBoolean("bass_enabled", true))
    }
    var virtualizerEnabled by remember {
        mutableStateOf(preferences.getBoolean("virtualizer_enabled", true))
    }
    var reverbEnabled by remember {
        mutableStateOf(preferences.getBoolean("reverb_enabled", false))
    }

    // Keep old five-band settings when migrating to ten bands.
    val levels = remember {
        mutableStateListOf<Float>().apply {
            repeat(10) { index ->
                val oldValue = if (index % 2 == 1) {
                    preferences.getFloat("eq_$index", 0f)
                } else {
                    0f
                }

                add(
                    preferences.getFloat(
                        "ten_eq_$index",
                        oldValue
                    ).coerceIn(-12f, 12f)
                )
            }
        }
    }

    val bandEnabled = remember {
        mutableStateListOf<Boolean>().apply {
            repeat(10) { index ->
                val oldValue = if (index % 2 == 1) {
                    preferences.getBoolean(
                        "band_${index / 2}",
                        true
                    )
                } else {
                    true
                }

                add(
                    preferences.getBoolean(
                        "ten_band_enabled_$index",
                        oldValue
                    )
                )
            }
        }
    }

    var preamp by remember {
        mutableFloatStateOf(
            preferences.getFloat("eq_preamp", 0f)
                .coerceIn(-12f, 6f)
        )
    }

    var bass by remember {
        mutableFloatStateOf(preferences.getFloat("bass", 0f))
    }
    var virtualizer by remember {
        mutableFloatStateOf(preferences.getFloat("virtualizer", 0f))
    }
    var reverb by remember {
        mutableFloatStateOf(
            preferences.getFloat("reverb", 35f).coerceIn(0f, 100f)
        )
    }
    var volume by remember {
        mutableIntStateOf(
            ((preferences.all["volume"] as? Number)?.toInt() ?: 100)
                .coerceIn(0, 200)
        )
    }

    var pendingVolume by remember { mutableIntStateOf(volume) }
    var showVolumeWarning by remember { mutableStateOf(false) }

    var preset by remember {
        mutableStateOf(
            preferences.getString("preset", "Normal") ?: "Normal"
        )
    }

    fun savePowerSettings() {
        preferences.edit()
            .putBoolean("eq_enabled", eqEnabled)
            .putBoolean("volume_enabled", volumeEnabled)
            .putBoolean("bass_enabled", bassEnabled)
            .putBoolean("virtualizer_enabled", virtualizerEnabled)
            .putBoolean("reverb_enabled", reverbEnabled)
            .apply()
    }

    fun saveBand(index: Int) {
        preferences.edit()
            .putFloat("ten_eq_$index", levels[index])
            .putBoolean(
                "ten_band_enabled_$index",
                bandEnabled[index]
            )
            .putString("preset", preset)
            .apply()
    }

    fun updateBand(index: Int, value: Float) {
        levels[index] = value.coerceIn(-12f, 12f)
        preset = "Custom"

        processor.setBandGain(
            index,
            if (bandEnabled[index]) levels[index] else 0f
        )

        saveBand(index)
    }

    fun toggleBand(index: Int) {
        bandEnabled[index] = !bandEnabled[index]

        processor.setBandGain(
            index,
            if (bandEnabled[index]) levels[index] else 0f
        )

        saveBand(index)
    }

    fun applyPreset(name: String) {
        val values = soundPresets[name] ?: return
        preset = name

        values.forEachIndexed { index, value ->
            levels[index] = value

            processor.setBandGain(
                index,
                if (bandEnabled[index]) value else 0f
            )

            preferences.edit()
                .putFloat("ten_eq_$index", value)
                .apply()
        }

        preferences.edit().putString("preset", name).apply()
    }

    fun changePreamp(value: Float) {
        preamp = value.coerceIn(-12f, 6f)
        processor.setPreamp(preamp)
        preferences.edit().putFloat("eq_preamp", preamp).apply()
    }

    fun changeBass(value: Float) {
        bass = value.coerceIn(0f, 100f)

        if (bassEnabled) {
            audioEffects?.setBassBoost((bass * 10).toInt())
        }

        preferences.edit().putFloat("bass", bass).apply()
    }

    fun changeVirtualizer(value: Float) {
        virtualizer = value.coerceIn(0f, 100f)

        if (virtualizerEnabled) {
            audioEffects?.setVirtualizer((virtualizer * 10).toInt())
        }

        preferences.edit().putFloat("virtualizer", virtualizer).apply()
    }

    fun changeReverb(value: Float) {
        reverb = value.coerceIn(0f, 100f)

        if (reverbEnabled) {
            audioEffects?.setReverbStrength(reverb.toInt())
        }

        preferences.edit().putFloat("reverb", reverb).apply()
    }

    fun applyVolume(value: Int) {
        volume = value.coerceIn(0, 200)
        audioEffects?.setVolumeLevel(volume)
        preferences.edit().putInt("volume", volume).apply()
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

        audioEffects?.let { effects ->
            effects.setVolumeEnabled(volumeEnabled)
            effects.setBassEnabled(bassEnabled)
            effects.setVirtualizerEnabled(virtualizerEnabled)
            effects.setReverbStrength(reverb.toInt())
            effects.setReverbEnabled(reverbEnabled)
            effects.setBassBoost(if (bassEnabled) (bass * 10).toInt() else 0)
            effects.setVirtualizer(
                if (virtualizerEnabled) (virtualizer * 10).toInt() else 0
            )
            effects.setVolumeLevel(volume)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EQBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("← BACK", color = EQGold, fontWeight = FontWeight.Bold)
            }

            Text("♫", color = EQGold, fontSize = 25.sp)
        }

        Text(
            "KG SOUND CONSOLE",
            color = EQGold,
            fontSize = 23.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )

        Text(
            "10-BAND PROFESSIONAL AUDIO CONTROL",
            color = EQMuted,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ModeCard(
                title = "EQUALIZER",
                active = eqEnabled,
                selected = mode == SoundMode.EQ,
                modifier = Modifier.weight(1f),
                onSelect = { mode = SoundMode.EQ },
                onPower = {
                    eqEnabled = !eqEnabled
                    processor.setEnabled(eqEnabled)
                    savePowerSettings()
                }
            )

            ModeCard(
                title = "VOLUME",
                active = volumeEnabled,
                selected = mode == SoundMode.VOLUME,
                modifier = Modifier.weight(1f),
                onSelect = { mode = SoundMode.VOLUME },
                onPower = {
                    volumeEnabled = !volumeEnabled
                    audioEffects?.setVolumeEnabled(volumeEnabled)
                    savePowerSettings()
                }
            )
        }

        Spacer(Modifier.height(14.dp))

        if (mode == SoundMode.EQ) {
            ConsolePanel("SOUND PRESETS") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    soundPresets.keys.forEach { name ->
                        Surface(
                            onClick = { applyPreset(name) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (preset == name) EQCopper else EQMetal,
                            border = BorderStroke(
                                1.dp,
                                if (preset == name) EQGold else EQCopper.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                name.uppercase(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    horizontal = 11.dp,
                                    vertical = 10.dp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            ConsolePanel("10-BAND GRAPHIC EQUALIZER") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("31 Hz", color = EQMuted, fontSize = 9.sp)
                    Text("GAIN  -12 dB TO +12 dB", color = EQMuted, fontSize = 9.sp)
                    Text("16 kHz", color = EQMuted, fontSize = 9.sp)
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    levels.forEachIndexed { index, level ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "%+.0f".format(level),
                                color = EQGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )

                            VerticalFader(
                                value = level,
                                enabled = bandEnabled[index],
                                onValueChange = { updateBand(index, it) }
                            )

                            Text(
                                bandLabels[index],
                                color = EQMuted,
                                fontSize = 9.sp
                            )

                            Spacer(Modifier.height(5.dp))

                            Surface(
                                onClick = { toggleBand(index) },
                                shape = RoundedCornerShape(5.dp),
                                color = if (bandEnabled[index]) EQCopper else EQMetal
                            ) {
                                Text(
                                    if (bandEnabled[index]) "ON" else "OFF",
                                    color = Color.White,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(
                                        horizontal = 4.dp,
                                        vertical = 5.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            ConsolePanel("PREAMP / OUTPUT HEADROOM") {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PREAMP", color = EQMuted, fontSize = 10.sp)
                    Text(
                        "%.1f dB".format(preamp),
                        color = EQGold,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = preamp,
                    onValueChange = { changePreamp(it) },
                    valueRange = -12f..6f,
                    colors = SliderDefaults.colors(
                        thumbColor = EQGold,
                        activeTrackColor = EQCopper,
                        inactiveTrackColor = EQMetal
                    )
                )

                Text(
                    "Lower the preamp when boosting several bands to reduce clipping.",
                    color = EQMuted,
                    fontSize = 9.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                KnobPanel(
                    title = "BASS BOOST",
                    value = bass,
                    enabled = bassEnabled,
                    onPower = {
                        bassEnabled = !bassEnabled
                        audioEffects?.setBassEnabled(bassEnabled)
                        if (bassEnabled) {
                            audioEffects?.setBassBoost((bass * 10).toInt())
                        }
                        savePowerSettings()
                    },
                    onValueChange = { changeBass(it) },
                    modifier = Modifier.weight(1f)
                )

                KnobPanel(
                    title = "VIRTUALIZER",
                    value = virtualizer,
                    enabled = virtualizerEnabled,
                    onPower = {
                        virtualizerEnabled = !virtualizerEnabled
                        audioEffects?.setVirtualizerEnabled(virtualizerEnabled)
                        if (virtualizerEnabled) {
                            audioEffects?.setVirtualizer((virtualizer * 10).toInt())
                        }
                        savePowerSettings()
                    },
                    onValueChange = { changeVirtualizer(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            ConsolePanel("ROOM REVERB") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "SPATIAL AMBIENCE",
                            color = EQGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            "Adds a room-like acoustic tail",
                            color = EQMuted,
                            fontSize = 9.sp
                        )
                    }

                    PowerButton(
                        enabled = reverbEnabled,
                        onClick = {
                            reverbEnabled = !reverbEnabled
                            audioEffects?.setReverbEnabled(reverbEnabled)
                            if (reverbEnabled) {
                                audioEffects?.setReverbStrength(reverb.toInt())
                            }
                            savePowerSettings()
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("STRENGTH", color = EQMuted, fontSize = 10.sp)
                    Text(
                        "${reverb.toInt()}%",
                        color = EQGold,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = reverb,
                    onValueChange = { changeReverb(it) },
                    enabled = reverbEnabled,
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = EQGold,
                        activeTrackColor = EQCopper,
                        inactiveTrackColor = EQMetal
                    )
                )
            }

        } else {
            ConsolePanel("MASTER VOLUME CONTROL") {
                Text(
                    if (volume == 0) "MUTED" else "$volume%",
                    color = EQGold,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    if (volumeEnabled) "● SIGNAL ACTIVE" else "● OUTPUT OFF",
                    color = if (volumeEnabled) EQGreen else EQMuted,
                    fontSize = 10.sp
                )

                Slider(
                    value = volume.toFloat(),
                    onValueChange = { value ->
                        val newValue = value.toInt()
                        if (newValue <= 100) {
                            applyVolume(newValue)
                        } else {
                            pendingVolume = newValue
                            showVolumeWarning = true
                        }
                    },
                    valueRange = 0f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = EQGold,
                        activeTrackColor = EQCopper,
                        inactiveTrackColor = EQMetal
                    )
                )

                Text(
                    "Higher output levels can cause distortion and may damage hearing.",
                    color = EQMuted,
                    fontSize = 9.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            ConsolePanel("BASS BOOST") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("BASS LEVEL", color = EQMuted, fontSize = 10.sp)
                    Text("${bass.toInt()}%", color = EQGold)
                }

                Slider(
                    value = bass,
                    onValueChange = { changeBass(it) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = EQGold,
                        activeTrackColor = EQCopper
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                applyPreset("Normal")
                changePreamp(0f)
                changeBass(0f)
                changeVirtualizer(0f)
                changeReverb(0f)
                applyVolume(100)

                bandEnabled.indices.forEach { index ->
                    bandEnabled[index] = true
                    processor.setBandGain(index, 0f)
                    saveBand(index)
                }

                eqEnabled = true
                volumeEnabled = true
                bassEnabled = true
                virtualizerEnabled = true
                reverbEnabled = false

                processor.setEnabled(true)
                audioEffects?.setVolumeEnabled(true)
                audioEffects?.setBassEnabled(true)
                audioEffects?.setVirtualizerEnabled(true)
                audioEffects?.setReverbEnabled(false)

                savePowerSettings()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(9.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EQCopper
            )
        ) {
            Text(
                "RESET SOUND CONSOLE",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))
    }

    if (showVolumeWarning) {
        AlertDialog(
            onDismissRequest = { showVolumeWarning = false },
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
                    "You selected $pendingVolume% volume. " +
                        "High output can damage hearing and cause distortion. " +
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
                    Text("YES, PROCEED", color = EQGold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showVolumeWarning = false }
                ) {
                    Text("NO, CANCEL", color = EQMuted)
                }
            }
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    active: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onPower: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EQPanel),
        border = BorderStroke(
            1.dp,
            if (selected) EQGold else EQCopper.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = EQGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(7.dp))

            PowerButton(enabled = active, onClick = onPower)

            Text(
                if (selected) "SELECTED" else "TAP TO SELECT",
                color = EQMuted,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun PowerButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) EQCopper else EQMetal
    ) {
        Text(
            if (enabled) "● ON" else "○ OFF",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 7.dp
            )
        )
    }
}

@Composable
private fun ConsolePanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EQPanel),
        border = BorderStroke(1.dp, EQCopper.copy(alpha = 0.65f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                color = EQGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun VerticalFader(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Canvas(
        modifier = Modifier
            .width(25.dp)
            .height(132.dp)
            .pointerInput(enabled) {
                fun update(y: Float) {
                    if (!enabled) return

                    val top = 5f
                    val bottom = size.height - 5f
                    val progress = (
                        1f - (y - top) / (bottom - top)
                    ).coerceIn(0f, 1f)

                    onValueChange(-12f + progress * 24f)
                }

                detectTapGestures { update(it.y) }
            }
            .pointerInput(enabled) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (!enabled) return@detectDragGestures

                    val top = 5f
                    val bottom = size.height - 5f
                    val progress = (
                        1f - (change.position.y - top) / (bottom - top)
                    ).coerceIn(0f, 1f)

                    onValueChange(-12f + progress * 24f)
                }
            }
    ) {
        val centerX = size.width / 2f
        val top = 5f
        val bottom = size.height - 5f
        val progress = ((value + 12f) / 24f).coerceIn(0f, 1f)
        val thumbY = bottom - progress * (bottom - top)

        drawLine(
            EQMetal,
            Offset(centerX, top),
            Offset(centerX, bottom),
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawLine(
            if (enabled) EQCopper else EQMuted,
            Offset(centerX, thumbY),
            Offset(centerX, bottom),
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawRoundRect(
            color = if (enabled) EQGold else EQMuted,
            topLeft = Offset(centerX - 8.dp.toPx(), thumbY - 5.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                16.dp.toPx(),
                10.dp.toPx()
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
    }
}

@Composable
private fun KnobPanel(
    title: String,
    value: Float,
    enabled: Boolean,
    onPower: () -> Unit,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    ConsolePanel(title) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RotaryKnob(
                value = value,
                enabled = enabled,
                onValueChange = onValueChange,
                modifier = Modifier.size(105.dp)
            )

            Text(
                "${value.toInt()}%",
                color = EQGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(7.dp))
            PowerButton(enabled = enabled, onClick = onPower)
        }
    }
}

@Composable
private fun RotaryKnob(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestValue = rememberUpdatedState(value)
    val latestChange = rememberUpdatedState(onValueChange)
    val progress = (value / 100f).coerceIn(0f, 1f)

    Box(
        modifier = modifier.pointerInput(enabled) {
            detectDragGestures { change, dragAmount ->
                change.consume()

                if (enabled) {
                    latestChange.value(
                        (latestValue.value - dragAmount.y * 0.8f)
                            .coerceIn(0f, 100f)
                    )
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.37f
            val stroke = 6.dp.toPx()

            drawCircle(EQMetal, radius + 8.dp.toPx(), center)
            drawCircle(EQCopper, radius, center)
            drawCircle(EQPanel, radius * 0.78f, center)

            drawArc(
                color = EQMuted.copy(alpha = 0.4f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(
                    center.x - radius - 4.dp.toPx(),
                    center.y - radius - 4.dp.toPx()
                ),
                size = androidx.compose.ui.geometry.Size(
                    (radius + 4.dp.toPx()) * 2,
                    (radius + 4.dp.toPx()) * 2
                ),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )

            drawArc(
                color = if (enabled) EQGreen else EQMuted,
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                topLeft = Offset(
                    center.x - radius - 4.dp.toPx(),
                    center.y - radius - 4.dp.toPx()
                ),
                size = androidx.compose.ui.geometry.Size(
                    (radius + 4.dp.toPx()) * 2,
                    (radius + 4.dp.toPx()) * 2
                ),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )

            val angle = Math.toRadians(135.0 + 270.0 * progress)

            drawLine(
                EQGold,
                Offset(
                    center.x + cos(angle).toFloat() * radius * 0.12f,
                    center.y + sin(angle).toFloat() * radius * 0.12f
                ),
                Offset(
                    center.x + cos(angle).toFloat() * radius * 0.65f,
                    center.y + sin(angle).toFloat() * radius * 0.65f
                ),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawCircle(EQGold, 4.dp.toPx(), center)
        }
    }
}

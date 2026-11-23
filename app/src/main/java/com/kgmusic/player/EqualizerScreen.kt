
package com.kgmusic.player

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private val EQBlack = Color(0xFF090706)
private val EQPanel = Color(0xFF17120F)
private val EQMetal = Color(0xFF30251D)
private val EQCopper = Color(0xFFB87333)
private val EQGold = Color(0xFFFFD27A)
private val EQGreen = Color(0xFF55F28A)
private val EQMuted = Color(0xFFB3A18D)

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
            preferences.getFloat("eq_preamp", 0f).coerceIn(-12f, 6f)
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
            .putBoolean("ten_band_enabled_$index", bandEnabled[index])
            .putString("preset", preset)
            .apply()
    }

    fun updateBand(index: Int, value: Float) {
        levels[index] = value.coerceIn(-12f, 12f)
        preset = "Custom"

        processor.setBandGain(
            index,
            if (eqEnabled && bandEnabled[index]) levels[index] else 0f
        )

        saveBand(index)
    }

    fun toggleBand(index: Int) {
        bandEnabled[index] = !bandEnabled[index]

        processor.setBandGain(
            index,
            if (eqEnabled && bandEnabled[index]) levels[index] else 0f
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
                if (eqEnabled && bandEnabled[index]) value else 0f
            )
        }

        val editor = preferences.edit()
            .putString("preset", name)

        values.forEachIndexed { index, value ->
            editor.putFloat("ten_eq_$index", value)
        }

        editor.apply()
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
                if (eqEnabled && bandEnabled[index]) value else 0f
            )
        }

        audioEffects?.let { effects ->
            effects.setVolumeEnabled(volumeEnabled)
            effects.setBassEnabled(bassEnabled)
            effects.setVirtualizerEnabled(virtualizerEnabled)
            effects.setReverbEnabled(reverbEnabled)
            effects.setReverbStrength(reverb.toInt())
            effects.setBassBoost(if (bassEnabled) (bass * 10).toInt() else 0)
            effects.setVirtualizer(
                if (virtualizerEnabled) (virtualizer * 10).toInt() else 0
            )
            effects.setVolumeLevel(volume)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(EQBlack)
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        val compact = maxHeight < 390.dp
        val headerHeight = if (compact) 34.dp else 40.dp
        val presetHeight = if (compact) 47.dp else 53.dp
        val eqHeight = if (compact) 132.dp else 155.dp
        val effectsHeight = if (compact) 83.dp else 99.dp
        val footerHeight = if (compact) 43.dp else 49.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            // HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .background(
                        EQPanel,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 7.dp)
                ) {
                    Text(
                        "← BACK",
                        color = EQGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "KG SOUND CONSOLE",
                        color = EQGold,
                        fontSize = if (compact) 17.sp else 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "10-BAND PROFESSIONAL AUDIO CONTROL",
                        color = EQMuted,
                        fontSize = 7.sp,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                Text("▥", color = EQGold, fontSize = 24.sp)
            }

            // PRESETS
            ConsolePanel(
                title = "◉  SOUND PRESETS",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(presetHeight)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    soundPresets.keys.forEach { name ->
                        Surface(
                            onClick = { applyPreset(name) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(7.dp),
                            color = if (preset == name) EQCopper else EQMetal,
                            border = BorderStroke(
                                1.dp,
                                if (preset == name) EQGold else EQCopper.copy(alpha = 0.5f)
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name,
                                    color = Color.White,
                                    fontSize = if (compact) 8.sp else 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // EQUALIZER AND PREAMP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(eqHeight),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ConsolePanel(
                    title = "10-BAND GRAPHIC EQUALIZER",
                    modifier = Modifier.weight(5.7f).fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (eqEnabled) "● EQ ON" else "○ EQ OFF",
                            color = if (eqEnabled) EQGreen else EQMuted,
                            fontSize = 8.sp
                        )
                        Text(
                            "31 Hz TO 16 kHz",
                            color = EQMuted,
                            fontSize = 8.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        levels.forEachIndexed { index, level ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "%+.1f".format(level),
                                    color = EQGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                VerticalFader(
                                    value = level,
                                    enabled = eqEnabled && bandEnabled[index],
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(0.75f),
                                    onValueChange = {
                                        updateBand(index, it)
                                    }
                                )

                                Text(
                                    bandLabels[index],
                                    color = Color.White,
                                    fontSize = 8.sp
                                )

                                Surface(
                                    onClick = { toggleBand(index) },
                                    modifier = Modifier.padding(top = 2.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (bandEnabled[index]) {
                                        Color(0xFF075522)
                                    } else {
                                        EQMetal
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (bandEnabled[index]) EQGreen else EQMuted
                                    )
                                ) {
                                    Text(
                                        if (bandEnabled[index]) "ON" else "OFF",
                                        color = Color.White,
                                        fontSize = 7.sp,
                                        modifier = Modifier.padding(
                                            horizontal = 5.dp,
                                            vertical = 1.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                ConsolePanel(
                    title = "PREAMP",
                    modifier = Modifier.weight(0.9f).fillMaxHeight()
                ) {
                    Text(
                        "%.1f dB".format(preamp),
                        color = EQGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    VerticalFader(
                        value = preamp,
                        enabled = true,
                        minValue = -12f,
                        maxValue = 6f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onValueChange = { changePreamp(it) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("-12", color = EQMuted, fontSize = 7.sp)
                        Text("+6", color = EQMuted, fontSize = 7.sp)
                    }
                }
            }

            // EFFECTS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(effectsHeight),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                KnobPanel(
                    title = "BASS BOOST",
                    subtitle = "Deep, Powerful Bass",
                    value = bass,
                    enabled = bassEnabled,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    compact = compact,
                    onPower = {
                        bassEnabled = !bassEnabled
                        audioEffects?.setBassEnabled(bassEnabled)
                        if (bassEnabled) {
                            audioEffects?.setBassBoost((bass * 10).toInt())
                        }
                        savePowerSettings()
                    },
                    onValueChange = { changeBass(it) }
                )

                KnobPanel(
                    title = "VIRTUALIZER",
                    subtitle = "Surround Sound Effect",
                    value = virtualizer,
                    enabled = virtualizerEnabled,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    compact = compact,
                    onPower = {
                        virtualizerEnabled = !virtualizerEnabled
                        audioEffects?.setVirtualizerEnabled(virtualizerEnabled)
                        if (virtualizerEnabled) {
                            audioEffects?.setVirtualizer((virtualizer * 10).toInt())
                        }
                        savePowerSettings()
                    },
                    onValueChange = { changeVirtualizer(it) }
                )

                KnobPanel(
                    title = "ROOM REVERB",
                    subtitle = "Natural Room Ambience",
                    value = reverb,
                    enabled = reverbEnabled,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    compact = compact,
                    onPower = {
                        reverbEnabled = !reverbEnabled
                        audioEffects?.setReverbEnabled(reverbEnabled)
                        if (reverbEnabled) {
                            audioEffects?.setReverbStrength(reverb.toInt())
                        }
                        savePowerSettings()
                    },
                    onValueChange = { changeReverb(it) }
                )
            }

            // MASTER VOLUME AND RESET
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(footerHeight),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ConsolePanel(
                    title = "♫  MASTER VOLUME CONTROL",
                    modifier = Modifier.weight(5f).fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            enabled = volumeEnabled,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = EQGold,
                                activeTrackColor = EQGreen,
                                inactiveTrackColor = EQMetal
                            )
                        )

                        Text(
                            "${volume}%",
                            color = EQGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )

                        Text(
                            if (volumeEnabled) "● ACTIVE" else "● OFF",
                            color = if (volumeEnabled) EQGreen else EQMuted,
                            fontSize = 8.sp
                        )

                        PowerButton(
                            enabled = volumeEnabled,
                            compact = true,
                            onClick = {
                                volumeEnabled = !volumeEnabled
                                audioEffects?.setVolumeEnabled(volumeEnabled)
                                savePowerSettings()
                            }
                        )
                    }
                }

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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EQCopper
                    ),
                    contentPadding = PaddingValues(3.dp)
                ) {
                    Text(
                        "↻\nRESET",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
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
                    "You selected $pendingVolume%. High output can cause " +
                        "distortion and may damage hearing. Proceed?",
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
                    Text("CANCEL", color = EQMuted)
                }
            }
        )
    }
}

@Composable
private fun ConsolePanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = EQPanel),
        border = BorderStroke(1.dp, EQCopper.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 4.dp)
        ) {
            Text(
                title,
                color = EQGold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun PowerButton(
    enabled: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) Color(0xFF075522) else EQMetal,
        border = BorderStroke(
            1.dp,
            if (enabled) EQGreen else EQMuted
        )
    ) {
        Text(
            if (enabled) "● ON" else "○ OFF",
            color = Color.White,
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 9.dp,
                vertical = 3.dp
            )
        )
    }
}

@Composable
private fun VerticalFader(
    value: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    minValue: Float = -12f,
    maxValue: Float = 12f,
    onValueChange: (Float) -> Unit
) {
    Canvas(
        modifier = modifier
            .widthIn(min = 12.dp)
            .pointerInput(enabled, minValue, maxValue) {
                fun update(y: Float) {
                    if (!enabled) return

                    val progress = (
                        1f - y / size.height
                    ).coerceIn(0f, 1f)

                    onValueChange(
                        minValue + progress * (maxValue - minValue)
                    )
                }

                detectTapGestures { update(it.y) }
            }
            .pointerInput(enabled, minValue, maxValue) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (!enabled) return@detectDragGestures

                    val progress = (
                        1f - change.position.y / size.height
                    ).coerceIn(0f, 1f)

                    onValueChange(
                        minValue + progress * (maxValue - minValue)
                    )
                }
            }
    ) {
        val centerX = size.width / 2f
        val top = 4.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        val progress = (
            (value - minValue) / (maxValue - minValue)
        ).coerceIn(0f, 1f)

        val thumbY = bottom - progress * (bottom - top)

        drawLine(
            EQMetal,
            Offset(centerX, top),
            Offset(centerX, bottom),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawLine(
            if (enabled) EQGreen else EQMuted,
            Offset(centerX, thumbY),
            Offset(centerX, bottom),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawCircle(
            EQGold,
            radius = 6.dp.toPx(),
            center = Offset(centerX, thumbY)
        )

        drawCircle(
            EQCopper,
            radius = 3.dp.toPx(),
            center = Offset(centerX, thumbY)
        )
    }
}

@Composable
private fun KnobPanel(
    title: String,
    subtitle: String,
    value: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean,
    onPower: () -> Unit,
    onValueChange: (Float) -> Unit
) {
    ConsolePanel(
        title = title,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RotaryKnob(
                value = value,
                enabled = enabled,
                modifier = Modifier.size(if (compact) 45.dp else 57.dp),
                onValueChange = onValueChange
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "${value.toInt()}%",
                    color = EQGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                PowerButton(
                    enabled = enabled,
                    compact = true,
                    onClick = onPower
                )

                if (!compact) {
                    Text(
                        subtitle,
                        color = EQMuted,
                        fontSize = 7.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun RotaryKnob(
    value: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
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
                        (latestValue.value - dragAmount.y * 1.2f)
                            .coerceIn(0f, 100f)
                    )
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.34f
            val stroke = 4.dp.toPx()

            drawCircle(EQMetal, radius + 7.dp.toPx(), center)
            drawCircle(EQCopper, radius, center)
            drawCircle(EQPanel, radius * 0.78f, center)

            drawArc(
                color = EQMuted.copy(alpha = 0.4f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(
                    center.x - radius - 3.dp.toPx(),
                    center.y - radius - 3.dp.toPx()
                ),
                size = Size(
                    (radius + 3.dp.toPx()) * 2,
                    (radius + 3.dp.toPx()) * 2
                ),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )

            drawArc(
                color = if (enabled) EQGreen else EQMuted,
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                topLeft = Offset(
                    center.x - radius - 3.dp.toPx(),
                    center.y - radius - 3.dp.toPx()
                ),
                size = Size(
                    (radius + 3.dp.toPx()) * 2,
                    (radius + 3.dp.toPx()) * 2
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
                    center.x + cos(angle).toFloat() * radius * 0.68f,
                    center.y + sin(angle).toFloat() * radius * 0.68f
                ),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawCircle(EQGold, 3.dp.toPx(), center)
        }
    }
}

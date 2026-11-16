
package com.kgmusic.player

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.sin

private val EQBlack = Color(0xFF090909)
private val EQPanel = Color(0xFF191715)
private val EQMetal = Color(0xFF302820)
private val EQCopper = Color(0xFFB87333)
private val EQGold = Color(0xFFE5B76C)
private val EQMuted = Color(0xFF9B8B7A)
private val EQGreen = Color(0xFF70D69A)

private enum class SoundMode {
    EQ, VOLUME
}

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

    val bandIndexes = listOf(1, 3, 5, 7, 9)
    val bandNames = listOf("60 Hz", "250 Hz", "1 kHz", "4 kHz", "16 kHz")

    val presets = listOf(
        "Normal", "Lounge", "Jazz", "Rap",
        "Rock", "Pop", "Classical", "Dance", "Bass"
    )

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

    val levels = remember {
        mutableStateListOf<Float>().apply {
            bandIndexes.forEach { index ->
                add(preferences.getFloat("eq_$index", 0f))
            }
        }
    }

    val bandEnabled = remember {
        mutableStateListOf<Boolean>().apply {
            repeat(5) { index ->
                add(preferences.getBoolean("band_$index", true))
            }
        }
    }

    var bass by remember {
        mutableFloatStateOf(preferences.getFloat("bass", 0f))
    }

    var virtualizer by remember {
        mutableFloatStateOf(preferences.getFloat("virtualizer", 0f))
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
            .apply()
    }

    fun saveBand(index: Int) {
        preferences.edit()
            .putFloat("eq_${bandIndexes[index]}", levels[index])
            .putBoolean("band_$index", bandEnabled[index])
            .putString("preset", preset)
            .apply()
    }

    fun updateBand(index: Int, value: Float) {
        levels[index] = value.coerceIn(-12f, 12f)
        preset = "Custom"

        if (bandEnabled[index]) {
            audioEffects?.setEqualizerBand(
                bandIndexes[index],
                (levels[index] * 100).toInt()
            )
        }

        saveBand(index)
    }

    fun toggleBand(index: Int) {
        bandEnabled[index] = !bandEnabled[index]

        audioEffects?.setEqualizerBand(
            bandIndexes[index],
            if (bandEnabled[index]) {
                (levels[index] * 100).toInt()
            } else {
                0
            }
        )

        saveBand(index)
    }

    fun applyPreset(name: String) {
        val values = when (name) {
            "Lounge" -> listOf(3f, 2f, 0f, 2f, 3f)
            "Jazz" -> listOf(3f, 2f, -1f, 2f, 4f)
            "Rap" -> listOf(5f, 3f, 0f, 2f, 3f)
            "Rock" -> listOf(5f, 2f, -2f, 3f, 4f)
            "Pop" -> listOf(-1f, 2f, 4f, 2f, 1f)
            "Classical" -> listOf(3f, 2f, 0f, 2f, 3f)
            "Dance" -> listOf(5f, 4f, 1f, 3f, 4f)
            "Bass" -> listOf(8f, 5f, 2f, 0f, 0f)
            else -> List(5) { 0f }
        }

        preset = name

        values.forEachIndexed { index, value ->
            levels[index] = value

            audioEffects?.setEqualizerBand(
                bandIndexes[index],
                if (bandEnabled[index]) {
                    (value * 100).toInt()
                } else {
                    0
                }
            )
        }

        preferences.edit().apply {
            values.forEachIndexed { index, value ->
                putFloat("eq_${bandIndexes[index]}", value)
            }
            putString("preset", name)
            apply()
        }
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

        preferences.edit()
            .putFloat("virtualizer", virtualizer)
            .apply()
    }

    fun applyVolume(value: Int) {
        volume = value.coerceIn(0, 200)
        audioEffects?.setVolumeLevel(volume)

        preferences.edit().putInt("volume", volume).apply()
    }

    LaunchedEffect(audioEffects) {
        audioEffects?.let { effects ->
            effects.setEqualizerEnabled(eqEnabled)
            effects.setVolumeEnabled(volumeEnabled)
            effects.setBassEnabled(bassEnabled)
            effects.setVirtualizerEnabled(virtualizerEnabled)

            bandIndexes.forEachIndexed { index, band ->
                effects.setEqualizerBand(
                    band,
                    if (bandEnabled[index]) {
                        (levels[index] * 100).toInt()
                    } else {
                        0
                    }
                )
            }

            effects.setBassBoost(
                if (bassEnabled) (bass * 10).toInt() else 0
            )

            effects.setVirtualizer(
                if (virtualizerEnabled) {
                    (virtualizer * 10).toInt()
                } else {
                    0
                }
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
        TextButton(onClick = onBack) {
            Text("← BACK", color = EQGold)
        }

        Text(
            "KG SOUND CONSOLE",
            color = EQGold,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )

        Text(
            "PROFESSIONAL AUDIO CONTROL",
            color = EQMuted,
            fontSize = 9.sp,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeCard(
                title = "EQUALIZER",
                active = eqEnabled,
                selected = mode == SoundMode.EQ,
                modifier = Modifier.weight(1f),
                onSelect = { mode = SoundMode.EQ },
                onPower = {
                    eqEnabled = !eqEnabled
                    audioEffects?.setEqualizerEnabled(eqEnabled)
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

        Spacer(Modifier.height(16.dp))

        if (mode == SoundMode.EQ) {

            ConsolePanel(title = "SOUND PRESETS") {
                Row(
                    modifier = Modifier.horizontalScroll(
                        rememberScrollState()
                    ),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    presets.forEach { name ->
                        Surface(
                            onClick = { applyPreset(name) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (preset == name) EQCopper else EQMetal
                        ) {
                            Text(
                                name.uppercase(),
                                color = if (preset == name) Color.White else EQGold,
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

            ConsolePanel(title = "5-BAND GRAPHIC EQUALIZER") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bandNames.forEachIndexed { index, name ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "${levels[index].toInt()}",
                                color = EQGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            VerticalFader(
                                value = levels[index],
                                enabled = bandEnabled[index],
                                onValueChange = { updateBand(index, it) }
                            )

                            Text(
                                name,
                                color = EQMuted,
                                fontSize = 9.sp
                            )

                            Spacer(Modifier.height(5.dp))

                            Surface(
                                onClick = { toggleBand(index) },
                                shape = RoundedCornerShape(5.dp),
                                color = if (bandEnabled[index]) {
                                    EQCopper
                                } else {
                                    EQMetal
                                }
                            ) {
                                Text(
                                    if (bandEnabled[index]) "ON" else "OFF",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(
                                        horizontal = 9.dp,
                                        vertical = 5.dp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "GAIN CONTROL  •  -12 dB TO +12 dB",
                    color = EQMuted,
                    fontSize = 9.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                            audioEffects?.setVirtualizer(
                                (virtualizer * 10).toInt()
                            )
                        }

                        savePowerSettings()
                    },
                    onValueChange = { changeVirtualizer(it) },
                    modifier = Modifier.weight(1f)
                )
            }

        } else {

            ConsolePanel(title = "MASTER VOLUME CONTROL") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "OUTPUT LEVEL",
                            color = EQMuted,
                            fontSize = 10.sp
                        )

                        Text(
                            if (volume == 0) "MUTED" else "$volume%",
                            color = EQGold,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Text(
                        if (volumeEnabled) "SIGNAL ACTIVE" else "OUTPUT OFF",
                        color = if (volumeEnabled) EQGreen else EQMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "EXTRA BASS",
                    color = EQGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    RotaryKnob(
                        value = bass,
                        enabled = true,
                        onValueChange = { changeBass(it) },
                        modifier = Modifier.size(160.dp)
                    )
                }

                Text(
                    "${bass.toInt()}% BASS",
                    color = EQMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(Modifier.height(12.dp))

            ConsolePanel(title = "VOLUME LEVEL SELECTOR") {
                val volumeOptions = listOf(
                    "MUTE" to 0,
                    "30%" to 30,
                    "60%" to 60,
                    "90%" to 90,
                    "120%" to 120,
                    "150%" to 150,
                    "MAX" to 200
                )

                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    volumeOptions.chunked(3).forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            group.forEach { option ->
                                val selected = volume == option.second

                                Surface(
                                    onClick = {
                                        if (option.second > 100) {
                                            pendingVolume = option.second
                                            showVolumeWarning = true
                                        } else {
                                            applyVolume(option.second)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (selected) EQCopper else EQMetal,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) {
                                            EQGold
                                        } else {
                                            EQCopper.copy(alpha = 0.5f)
                                        }
                                    )
                                ) {
                                    Text(
                                        option.first,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 15.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }

                            if (group.size < 3) {
                                repeat(3 - group.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Boost levels above 100% require confirmation.",
                color = EQMuted,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                applyPreset("Normal")
                changeBass(0f)
                changeVirtualizer(0f)
                applyVolume(100)

                bandEnabled.indices.forEach {
                    bandEnabled[it] = true
                    saveBand(it)
                }

                eqEnabled = true
                volumeEnabled = true
                bassEnabled = true
                virtualizerEnabled = true

                audioEffects?.setEqualizerEnabled(true)
                audioEffects?.setVolumeEnabled(true)
                audioEffects?.setBassEnabled(true)
                audioEffects?.setVirtualizerEnabled(true)

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
                    }
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
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) EQGold else EQCopper.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = EQGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(7.dp))

            Surface(
                onClick = onPower,
                shape = RoundedCornerShape(20.dp),
                color = if (active) EQCopper else EQMetal
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 7.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "●",
                        color = if (active) EQGreen else EQMuted,
                        fontSize = 10.sp
                    )

                    Spacer(Modifier.width(6.dp))

                    Text(
                        if (active) "ON" else "OFF",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                if (selected) "SELECTED" else "TAP TO SELECT",
                color = EQMuted,
                fontSize = 8.sp
            )
        }
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
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            EQCopper.copy(alpha = 0.65f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                color = EQGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(12.dp))
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
    val minValue = -12f
    val maxValue = 12f

    Canvas(
        modifier = Modifier
            .width(46.dp)
            .height(142.dp)
            .pointerInput(enabled) {
                fun update(y: Float) {
                    if (!enabled) return

                    val top = 10f
                    val bottom = size.height - 10f
                    val progress = (1f - ((y - top) / (bottom - top)))
                        .coerceIn(0f, 1f)

                    onValueChange(
                        minValue + progress * (maxValue - minValue)
                    )
                }

                detectTapGestures { update(it.y) }
            }
            .pointerInput(enabled) {
                detectDragGestures { change, _ ->
                    change.consume()

                    if (enabled) {
                        val top = 10f
                        val bottom = size.height - 10f

                        val progress = (
                            1f - ((change.position.y - top) / (bottom - top))
                        ).coerceIn(0f, 1f)

                        onValueChange(
                            minValue + progress * (maxValue - minValue)
                        )
                    }
                }
            }
    ) {
        val centerX = size.width / 2f
        val top = 10f
        val bottom = size.height - 10f

        val progress = ((value - minValue) / (maxValue - minValue))
            .coerceIn(0f, 1f)

        val thumbY = bottom - progress * (bottom - top)

        drawLine(
            EQMetal,
            Offset(centerX, top),
            Offset(centerX, bottom),
            strokeWidth = 8.dp.toPx(),
            cap = StrokeCap.Round
        )

        for (i in 0..12) {
            val y = top + (bottom - top) * i / 12f

            drawLine(
                EQMuted.copy(alpha = 0.65f),
                Offset(centerX - 13.dp.toPx(), y),
                Offset(centerX - 8.dp.toPx(), y),
                strokeWidth = 1.dp.toPx()
            )
        }

        drawLine(
            if (enabled) EQCopper else EQMuted,
            Offset(centerX, thumbY),
            Offset(centerX, bottom),
            strokeWidth = 8.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawRoundRect(
            color = if (enabled) EQGold else EQMuted,
            topLeft = Offset(
                centerX - 12.dp.toPx(),
                thumbY - 5.dp.toPx()
            ),
            size = androidx.compose.ui.geometry.Size(
                24.dp.toPx(),
                10.dp.toPx()
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                3.dp.toPx()
            )
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
    ConsolePanel(title = title) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RotaryKnob(
                value = value,
                enabled = enabled,
                onValueChange = onValueChange,
                modifier = Modifier.size(112.dp)
            )

            Text(
                "${value.toInt()}%",
                color = EQGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(7.dp))

            Surface(
                onClick = onPower,
                color = if (enabled) EQCopper else EQMetal,
                shape = RoundedCornerShape(7.dp)
            ) {
                Text(
                    if (enabled) "ON" else "OFF",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        horizontal = 15.dp,
                        vertical = 7.dp
                    )
                )
            }
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
    val progress = (value / 100f).coerceIn(0f, 1f)

    val latestValue = rememberUpdatedState(value)
    val latestOnValueChange = rememberUpdatedState(onValueChange)

    Box(
        modifier = modifier
            .pointerInput(enabled) {
                detectDragGestures { change, dragAmount ->
                    change.consume()

                    if (enabled) {
                        latestOnValueChange.value(
                            (
                                latestValue.value -
                                    dragAmount.y * 0.8f
                            ).coerceIn(0f, 100f)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(
                size.width / 2f,
                size.height / 2f
            )

            val radius = size.minDimension * 0.39f
            val stroke = 7.dp.toPx()

            drawCircle(
                color = EQMetal,
                radius = radius + 9.dp.toPx(),
                center = center
            )

            drawCircle(
                color = EQCopper,
                radius = radius,
                center = center
            )

            drawCircle(
                color = EQPanel,
                radius = radius * 0.78f,
                center = center
            )

            drawArc(
                color = EQMuted.copy(alpha = 0.35f),
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
                style = Stroke(
                    stroke,
                    cap = StrokeCap.Round
                )
            )

            drawArc(
                color = if (enabled) EQGold else EQMuted,
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
                style = Stroke(
                    stroke,
                    cap = StrokeCap.Round
                )
            )

            for (i in 0..10) {
                val angle = Math.toRadians(135.0 + i * 27.0)
                val inner = radius + 13.dp.toPx()
                val outer = inner + 5.dp.toPx()

                drawLine(
                    EQGold.copy(alpha = 0.8f),
                    Offset(
                        center.x + cos(angle).toFloat() * inner,
                        center.y + sin(angle).toFloat() * inner
                    ),
                    Offset(
                        center.x + cos(angle).toFloat() * outer,
                        center.y + sin(angle).toFloat() * outer
                    ),
                    strokeWidth = 2.dp.toPx()
                )
            }

            val angle = Math.toRadians(135.0 + 270.0 * progress)

            drawLine(
                EQGold,
                Offset(
                    center.x + cos(angle).toFloat() * radius * 0.15f,
                    center.y + sin(angle).toFloat() * radius * 0.15f
                ),
                Offset(
                    center.x + cos(angle).toFloat() * radius * 0.65f,
                    center.y + sin(angle).toFloat() * radius * 0.65f
                ),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawCircle(
                EQGold,
                radius = 4.dp.toPx(),
                center = center
            )
        }
    }
}


package com.kgmusic.player

import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlin.math.log10

class AudioEffects(audioSessionId: Int) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var virtualizer: Virtualizer? = null

    val frequencies = listOf(
        31, 62, 125, 250, 500,
        1000, 2000, 4000, 8000, 16000
    )

    init {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (_: Exception) {
            equalizer = null
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }
        } catch (_: Exception) {
            bassBoost = null
        }

        try {
            loudnessEnhancer =
                LoudnessEnhancer(audioSessionId).apply {
                    enabled = true
                }
        } catch (_: Exception) {
            loudnessEnhancer = null
        }

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (_: Exception) {
            virtualizer = null
        }
    }

    fun getAvailableBands(): Int {
        return try {
            equalizer?.numberOfBands?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
        } catch (_: Exception) {
        }
    }

    fun setVolumeEnabled(enabled: Boolean) {
        try {
            loudnessEnhancer?.enabled = enabled
        } catch (_: Exception) {
        }
    }

    fun setEqualizerBand(index: Int, level: Int) {
        try {
            val eq = equalizer ?: return
            val range = eq.bandLevelRange

            val safeLevel = level.coerceIn(
                range[0].toInt(),
                range[1].toInt()
            )

            val frequency = frequencies.getOrNull(index) ?: return
            val bandCount = eq.numberOfBands.toInt()

            if (bandCount <= 0) return

            var closestBand = 0
            var smallestDifference = Int.MAX_VALUE

            for (band in 0 until bandCount) {
                val centerFrequency =
                    eq.getCenterFreq(band.toShort()) / 1000

                val difference =
                    kotlin.math.abs(centerFrequency - frequency)

                if (difference < smallestDifference) {
                    smallestDifference = difference
                    closestBand = band
                }
            }

            eq.setBandLevel(
                closestBand.toShort(),
                safeLevel.toShort()
            )
        } catch (_: Exception) {
        }
    }

    fun setBassBoost(strength: Int) {
        try {
            bassBoost?.setStrength(
                strength.coerceIn(0, 1000).toShort()
            )
        } catch (_: Exception) {
        }
    }

    fun setBassEnabled(enabled: Boolean) {
        try {
            bassBoost?.enabled = enabled
        } catch (_: Exception) {
        }
    }

    fun setVirtualizer(strength: Int) {
        try {
            virtualizer?.setStrength(
                strength.coerceIn(0, 1000).toShort()
            )
        } catch (_: Exception) {
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        try {
            virtualizer?.enabled = enabled
        } catch (_: Exception) {
        }
    }

    // Retained for compatibility with the existing EQ screen.
    fun setVolumeBoost(percent: Int) {
        setVolumeLevel(percent)
    }

    // Supports Mute, reduced volume, normal volume and boosted volume.
    // The screen must request confirmation before applying values above 100%.
    fun setVolumeLevel(percent: Int) {
        try {
            val safePercent = percent.coerceIn(0, 200)

            val gain = if (safePercent == 0) {
                -6000
            } else {
                (
                    2000.0 * log10(safePercent / 100.0)
                ).toInt().coerceIn(-6000, 6000)
            }

            loudnessEnhancer?.setTargetGain(gain)
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }

        try {
            bassBoost?.release()
        } catch (_: Exception) {
        }

        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {
        }

        try {
            virtualizer?.release()
        } catch (_: Exception) {
        }

        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
        virtualizer = null
    }
}

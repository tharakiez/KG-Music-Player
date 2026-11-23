
package com.kgmusic.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(UnstableApi::class)
class TenBandEqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        val BAND_FREQUENCIES = intArrayOf(
            31, 62, 125, 250, 500,
            1000, 2000, 4000, 8000, 16000
        )

        private const val MIN_GAIN_DB = -12f
        private const val MAX_GAIN_DB = 12f
        private const val FILTER_Q = 1.0
    }

    private data class Coefficients(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    ) {
        companion object {
            val IDENTITY = Coefficients(
                1.0, 0.0, 0.0, 0.0, 0.0
            )
        }
    }

    private class FilterState {
        var z1 = 0.0
        var z2 = 0.0

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }

        fun process(input: Double, c: Coefficients): Double {
            val output = c.b0 * input + z1

            z1 = c.b1 * input - c.a1 * output + z2
            z2 = c.b2 * input - c.a2 * output

            return output
        }
    }

    @Volatile
    private var bandGains = FloatArray(10)

    @Volatile
    private var preampDb = 0f

    @Volatile
    private var equalizerEnabled = true

    @Volatile
    private var sampleRateHz = 0

    private var channelCount = 0

    @Volatile
    private var coefficients =
        Array(10) { Coefficients.IDENTITY }

    private var filterStates =
        Array(10) { emptyArray<FilterState>() }

    fun setEnabled(enabled: Boolean) {
        equalizerEnabled = enabled
    }

    fun setBandGain(index: Int, gainDb: Float) {
        if (index !in BAND_FREQUENCIES.indices) return

        synchronized(this) {
            val updated = bandGains.clone()
            updated[index] = gainDb.coerceIn(
                MIN_GAIN_DB,
                MAX_GAIN_DB
            )
            bandGains = updated
            rebuildCoefficients()
        }
    }

    fun setBandGains(gains: FloatArray) {
        synchronized(this) {
            val updated = FloatArray(10)

            for (i in updated.indices) {
                updated[i] = (gains.getOrNull(i) ?: 0f)
                    .coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            }

            bandGains = updated
            rebuildCoefficients()
        }
    }

    fun getBandGains(): FloatArray {
        return bandGains.clone()
    }

    fun setPreamp(gainDb: Float) {
        preampDb = gainDb.coerceIn(-12f, 6f)
    }

    fun getPreamp(): Float {
        return preampDb
    }

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        val supported =
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT

        if (!supported || inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.channelCount <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount

        filterStates = Array(10) {
            Array(channelCount) { FilterState() }
        }

        rebuildCoefficients()

        return inputAudioFormat
    }

    private fun rebuildCoefficients() {
        val rate = sampleRateHz
        if (rate <= 0) return

        val gains = bandGains

        coefficients = Array(10) { index ->
            createPeakingFilter(
                frequency = BAND_FREQUENCIES[index].toDouble()
                    .coerceAtMost(rate * 0.45),
                sampleRate = rate.toDouble(),
                gainDb = gains[index].toDouble(),
                q = FILTER_Q
            )
        }
    }

    private fun createPeakingFilter(
        frequency: Double,
        sampleRate: Double,
        gainDb: Double,
        q: Double
    ): Coefficients {
        if (kotlin.math.abs(gainDb) < 0.001) {
            return Coefficients.IDENTITY
        }

        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosOmega
        val b2 = 1.0 - alpha * a

        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosOmega
        val a2 = 1.0 - alpha / a

        return Coefficients(
            b0 / a0,
            b1 / a0,
            b2 / a0,
            a1 / a0,
            a2 / a0
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val output = replaceOutputBuffer(inputBuffer.remaining())
            .order(ByteOrder.nativeOrder())

        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val format = inputAudioFormat
        val filters = coefficients
        val enabled = equalizerEnabled

        val gain = 10.0.pow(preampDb.toDouble() / 20.0)

        var channel = 0

        if (format.encoding == C.ENCODING_PCM_16BIT) {
            while (input.remaining() >= 2) {
                val raw = input.short.toDouble() / 32768.0

                var sample = raw * gain

                if (enabled) {
                    for (band in 0 until 10) {
                        sample = filterStates[band][channel]
                            .process(sample, filters[band])
                    }
                }

                val result = (sample.coerceIn(-1.0, 0.999969)
                    * 32768.0).roundToInt()
                    .coerceIn(-32768, 32767)

                output.putShort(result.toShort())

                channel++
                if (channel >= channelCount) channel = 0
            }
        } else if (format.encoding == C.ENCODING_PCM_FLOAT) {
            while (input.remaining() >= 4) {
                val raw = input.float.toDouble()

                var sample = raw * gain

                if (enabled) {
                    for (band in 0 until 10) {
                        sample = filterStates[band][channel]
                            .process(sample, filters[band])
                    }
                }

                output.putFloat(
                    sample.coerceIn(-1.0, 1.0).toFloat()
                )

                channel++
                if (channel >= channelCount) channel = 0
            }
        }

        output.flip()
    }
}

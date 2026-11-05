package com.example.privatizare.ui.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sunetele jocului, sintetizate (fără fișiere), ca în web (web/js/audio.js).
 * Oscilatorul cu anvelopă exponențială și zgomotul filtrat cu biquad (Q = 1, ca în Web Audio).
 */
object SoundSynth {

    const val SAMPLE_RATE = 44100

    enum class Wave { SINE, TRIANGLE, SQUARE, SAWTOOTH }

    /** Un sunet = mai multe piese (ton sau zgomot), fiecare cu propriul start. */
    private sealed class Part {
        abstract val startSec: Double
        data class Tone(
            override val startSec: Double, val freq: Double, val dur: Double, val wave: Wave,
            val vol: Double, val endFreq: Double? = null,
        ) : Part()
        data class Noise(
            override val startSec: Double, val dur: Double, val vol: Double,
            val cutoff: Double, val lowpass: Boolean,
        ) : Part()
    }

    private fun tone(at: Double, freq: Double, dur: Double, wave: Wave, vol: Double, endFreq: Double? = null) =
        Part.Tone(at, freq, dur, wave, vol, endFreq)

    private fun noise(dur: Double, vol: Double, cutoff: Double, lowpass: Boolean) =
        Part.Noise(0.0, dur, vol, cutoff, lowpass)

    /** Aruncarea zarului. */
    fun dice(): ShortArray = render(listOf(noise(0.28, 0.25, 2400.0, lowpass = false)))

    /** Un pas al pionului. */
    fun step(): ShortArray = render(listOf(tone(0.0, 620.0, 0.05, Wave.SINE, 0.06)))

    /** Aterizare (nefolosit în web, păstrat pentru paritate). */
    fun land(): ShortArray = render(listOf(tone(0.0, 160.0, 0.14, Wave.SINE, 0.22, endFreq = 90.0)))

    /** Monedă: două tonuri, al doilea la 70 ms. */
    fun coin(): ShortArray = render(listOf(
        tone(0.0, 880.0, 0.09, Wave.TRIANGLE, 0.12),
        tone(0.07, 1320.0, 0.12, Wave.TRIANGLE, 0.10),
    ))

    /** Cartonaș tras: zgomot grav. */
    fun card(): ShortArray = render(listOf(noise(0.35, 0.12, 900.0, lowpass = true)))

    /** Cumpărare: trei tonuri urcătoare (0, 90 ms, 180 ms). */
    fun buy(): ShortArray = render(listOf(
        tone(0.0, 523.0, 0.08, Wave.SQUARE, 0.07),
        tone(0.09, 784.0, 0.08, Wave.SQUARE, 0.07),
        tone(0.18, 1046.0, 0.16, Wave.SQUARE, 0.07),
    ))

    /** Datorie: alarmă cu tonul coborând. */
    fun alert(): ShortArray = render(listOf(tone(0.0, 180.0, 0.22, Wave.SAWTOOTH, 0.09, endFreq = 140.0)))

    /** Durata unui sunet, în secunde (pentru a-l opri la timp). */
    fun durationSec(samples: ShortArray): Double = samples.size.toDouble() / SAMPLE_RATE

    private fun render(parts: List<Part>, random: Random = Random.Default): ShortArray {
        val total = parts.maxOf { (it.startSec + durOf(it)) * SAMPLE_RATE }.toInt() + SAMPLE_RATE / 50
        val mix = DoubleArray(total)
        for (p in parts) {
            when (p) {
                is Part.Tone -> addTone(mix, p)
                is Part.Noise -> addNoise(mix, p, random)
            }
        }
        return ShortArray(total) { i ->
            (mix[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun durOf(p: Part): Double = when (p) {
        is Part.Tone -> p.dur
        is Part.Noise -> p.dur
    }

    /** Oscilator cu frecvență (opțional) care coboară/urcă exponențial și anvelopă exponențială spre 0.0008. */
    private fun addTone(mix: DoubleArray, p: Part.Tone) {
        val start = (p.startSec * SAMPLE_RATE).toInt()
        val n = (p.dur * SAMPLE_RATE).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= mix.size) break
            val t = i.toDouble() / SAMPLE_RATE
            val k = t / p.dur
            val freq = p.endFreq?.let { p.freq * (it / p.freq).pow(k) } ?: p.freq
            phase += 2 * PI * freq / SAMPLE_RATE
            val gain = p.vol * (0.0008 / p.vol).pow(k)
            mix[idx] += wave(p.wave, phase) * gain
        }
    }

    private fun wave(w: Wave, phase: Double): Double = when (w) {
        Wave.SINE -> sin(phase)
        Wave.TRIANGLE -> 2 / PI * kotlin.math.asin(sin(phase))
        Wave.SQUARE -> if (sin(phase) >= 0) 1.0 else -1.0
        Wave.SAWTOOTH -> {
            val cycles = phase / (2 * PI)
            2 * (cycles - kotlin.math.floor(cycles + 0.5))
        }
    }

    /** Zgomot alb cu decădere liniară, prin filtrul biquad (lowpass sau bandpass, Q = 1), gain = vol. */
    private fun addNoise(mix: DoubleArray, p: Part.Noise, random: Random) {
        val n = (p.dur * SAMPLE_RATE).toInt()
        val raw = DoubleArray(n) { i -> (random.nextDouble() * 2 - 1) * (1 - i.toDouble() / n) }
        val f = biquad(p.lowpass, p.cutoff, SAMPLE_RATE.toDouble(), q = 1.0)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        val start = (p.startSec * SAMPLE_RATE).toInt()
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= mix.size) break
            val x = raw[i]
            val y = f.b0 * x + f.b1 * x1 + f.b2 * x2 - f.a1 * y1 - f.a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            mix[idx] += y * p.vol
        }
    }

    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    /** Coeficienții RBJ (Audio EQ Cookbook), normalizați la a0 = 1. */
    private fun biquad(lowpass: Boolean, freq: Double, sr: Double, q: Double): Biquad {
        val w0 = 2 * PI * freq / sr
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        val a0 = 1 + alpha
        return if (lowpass) {
            Biquad(
                b0 = (1 - c) / 2 / a0, b1 = (1 - c) / a0, b2 = (1 - c) / 2 / a0,
                a1 = -2 * c / a0, a2 = (1 - alpha) / a0,
            )
        } else {
            Biquad(
                b0 = alpha / a0, b1 = 0.0, b2 = -alpha / a0,
                a1 = -2 * c / a0, a2 = (1 - alpha) / a0,
            )
        }
    }

    /** Amplitudinea maximă a unui sunet, pentru teste. */
    fun peak(samples: ShortArray): Int = samples.maxOfOrNull { abs(it.toInt()) } ?: 0
}

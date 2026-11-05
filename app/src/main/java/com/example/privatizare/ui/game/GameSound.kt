package com.example.privatizare.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/**
 * Redarea sunetelor jocului (sintetizate în [SoundSynth]), ca în web: zar, pas, monedă, cartonaș,
 * cumpărare, alertă. Setarea „sunet pornit/oprit” rămâne salvată între sesiuni.
 */
object GameSound {
    private const val PREFS = "sunet"
    private const val KEY = "on"

    @Volatile
    var enabled: Boolean = true
        private set

    private val main = Handler(Looper.getMainLooper())

    /** Citește setarea salvată; apelat o dată la pornirea aplicației. */
    fun init(context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    /** Pornește sau oprește sunetul și salvează alegerea. Întoarce noua stare. */
    fun toggle(context: Context): Boolean {
        enabled = !enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
        return enabled
    }

    fun dice() = play(SoundSynth.dice())
    fun step() = play(SoundSynth.step())
    fun land() = play(SoundSynth.land())
    fun coin() = play(SoundSynth.coin())
    fun card() = play(SoundSynth.card())
    fun buy() = play(SoundSynth.buy())
    fun alert() = play(SoundSynth.alert())

    /** Redă un sunet scurt, pe firul de audio, fără să blocheze interfața. */
    private fun play(samples: ShortArray) {
        if (!enabled || samples.isEmpty()) return
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SoundSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return
        runCatching {
            track.write(samples, 0, samples.size)
            track.play()
        }.onFailure {
            track.release()
            return
        }
        // eliberăm resursa după ce sunetul s-a terminat
        val ms = (SoundSynth.durationSec(samples) * 1000).toLong() + 50L
        main.postDelayed({ runCatching { track.release() } }, ms)
    }
}

package com.example.privatizare.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundAndMoneyTest {

    private val sr = SoundSynth.SAMPLE_RATE

    @Test
    fun pasulEsteScurtSiAudibil() {
        val s = SoundSynth.step()
        assertTrue(s.size >= (0.05 * sr).toInt())
        assertTrue(s.size < (0.2 * sr).toInt())
        assertTrue(SoundSynth.peak(s) > 0)
    }

    @Test
    fun monedaAreDouaToniDecalate() {
        val s = SoundSynth.coin()
        // al doilea ton începe la 70 ms și durează 120 ms
        assertTrue(s.size >= ((0.07 + 0.12) * sr).toInt())
    }

    @Test
    fun cumparareAreTreiToniUrcatoare() {
        val s = SoundSynth.buy()
        assertTrue(s.size >= ((0.18 + 0.16) * sr).toInt())
    }

    @Test
    fun niciunSunetNuDepasesteAmplitudinea() {
        for (s in listOf(SoundSynth.dice(), SoundSynth.card(), SoundSynth.alert(), SoundSynth.buy())) {
            assertTrue(SoundSynth.peak(s) <= Short.MAX_VALUE.toInt())
            assertTrue(SoundSynth.peak(s) > 0)
        }
    }

    @Test
    fun sumeleSeAfiseazaCuSpatiuLaMiiCa() {
        assertEquals("0", ecuText(0))
        assertEquals("999", ecuText(999))
        assertEquals("1 200", ecuText(1200))
        assertEquals("1 000 000", ecuText(1_000_000))
        assertEquals("-6 000", ecuText(-6000))
    }
}

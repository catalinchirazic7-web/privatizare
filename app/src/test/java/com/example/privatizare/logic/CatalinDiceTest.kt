package com.example.privatizare.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** CatalinRobot: zarul cu noroc (best-of-two cu șansa CATALIN_LUCK) și distribuția pasului. */
class CatalinDiceTest {

    /** Probabilitatea feței k cu zarul cu noroc: (1-p)/6 + p·(2k-1)/36. */
    private fun expectedFace(k: Int) = (1 - CATALIN_LUCK) / 6 + CATALIN_LUCK * (2 * k - 1) / 36.0

    @Test
    fun `fara noroc rollBotDie da aceleasi fete ca rollDie`() {
        val a = Random(7)
        val b = Random(7)
        repeat(1000) { assertEquals(rollDie(b), rollBotDie(a, 0.0)) }
    }

    @Test
    fun `distributia fetelor cu noroc se potriveste cu formula`() {
        val rng = Random(12345)
        val n = 200_000
        val counts = IntArray(7)
        repeat(n) { counts[rollBotDie(rng, CATALIN_LUCK)]++ }
        var mean = 0.0
        for (k in 1..6) {
            val freq = counts[k].toDouble() / n
            assertTrue("fața $k: $freq vs ${expectedFace(k)}", kotlin.math.abs(freq - expectedFace(k)) < 0.005)
            mean += k * freq
        }
        assertTrue("media $mean", kotlin.math.abs(mean - 3.743) < 0.02)
    }

    @Test
    fun `distributia pasului robotului are suma 1 pentru unul si doua zaruri`() {
        val one = BotAI.botStepDistribution(1)
        assertTrue(kotlin.math.abs(one.values.sum() - 1.0) < 1e-9)
        assertTrue(kotlin.math.abs((one[6] ?: 0.0) - expectedFace(6)) < 1e-9)
        val two = BotAI.botStepDistribution(2)
        assertTrue(kotlin.math.abs(two.values.sum() - 1.0) < 1e-9)
        assertEquals(2, two.keys.min())
        assertEquals(12, two.keys.max())
    }
}

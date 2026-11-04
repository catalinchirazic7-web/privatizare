package com.example.privatizare.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashStacksTest {

    private fun sum(counts: Map<Long, Int>): Long = counts.entries.sumOf { it.key * it.value }

    @Test
    fun fărăBani_nuAreBancnote() {
        assertTrue(billCounts(0L).isEmpty())
        assertTrue(billCounts(-5L).isEmpty())
    }

    @Test
    fun sumaSeAcoperaExact() {
        assertEquals(1350L, sum(billCounts(1350L)))
        assertEquals(250_000L, sum(billCounts(250_000L)))
    }

    @Test
    fun laSumaMareApareToateValorile() {
        val counts = billCounts(250_000L)
        for (d in BILL_DENOMINATIONS) {
            assertTrue("lipsește $d", (counts[d] ?: 0) > 0)
        }
    }

    @Test
    fun restulSubCincizeciNuSeDeseneaza() {
        assertEquals(mapOf(50L to 1), billCounts(75L))
    }
}

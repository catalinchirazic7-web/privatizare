package com.example.privatizare.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StartingDistributionTest {

    @Test
    fun tiraj_totalEste500000() {
        assertEquals(500_000L, BILL_SUPPLY.entries.sumOf { it.key * it.value })
    }

    @Test
    fun doiJucatori_250000FiecareBancaZero() {
        val d = distributeStartingMoney(2)
        assertEquals(250_000L, d.playerMoney)
        assertEquals(0L, d.bankMoney)
        assertEquals(40, d.billsPerPlayer[50L])
        assertEquals(40, d.billsPerPlayer[2000L])
    }

    @Test
    fun treiJucatori_165100FiecareBancaPrimeste4700() {
        val d = distributeStartingMoney(3)
        assertEquals(165_100L, d.playerMoney)
        assertEquals(4_700L, d.bankMoney)
        // Restul: 2 bancnote de 50, 1 de 100, 1 de 500, 2 de 2000
        assertEquals(2, d.bankBills[50L])
        assertEquals(1, d.bankBills[100L])
        assertEquals(1, d.bankBills[500L])
        assertEquals(2, d.bankBills[2000L])
    }

    @Test
    fun patruJucatori_125000FiecareBancaZero() {
        val d = distributeStartingMoney(4)
        assertEquals(125_000L, d.playerMoney)
        assertEquals(0L, d.bankMoney)
    }

    @Test
    fun invariant_banciiSiJucatoriiAcopera500000LaOriceNumar() {
        for (n in 2..4) {
            val d = distributeStartingMoney(n)
            assertEquals(500_000L, n * d.playerMoney + d.bankMoney)
        }
    }

    @Test
    fun sumaAleasa_fiecareJucatorPrimesteExactSuma_siTiraiulSeConserva() {
        for (n in 2..4) {
            var money = STARTING_MONEY_STEP
            while (money <= maxCustomStartingMoney(n)) {
                val d = distributeStartingMoney(n, money)
                assertEquals(money, d.playerMoney)
                assertEquals(TOTAL_SUPPLY, n * d.playerMoney + d.bankMoney)
                assertTrue(d.bankBills.values.all { it >= 0 })
                money += STARTING_MONEY_STEP
            }
        }
    }

    @Test
    fun capul_doiJucatori250000_treiJucatori150000_patruJucatori100000() {
        assertEquals(250_000L, maxCustomStartingMoney(2))
        assertEquals(150_000L, maxCustomStartingMoney(3))
        assertEquals(100_000L, maxCustomStartingMoney(4))
    }

    @Test
    fun sumaPesteCap_saugaSauPrea_sauNuEPasDeLaStep_sunaRespinsa() {
        assertThrows(IllegalArgumentException::class.java) { distributeStartingMoney(2, 300_000L) }
        assertThrows(IllegalArgumentException::class.java) { distributeStartingMoney(4, 150_000L) }
        assertThrows(IllegalArgumentException::class.java) { distributeStartingMoney(2, 75_000L) }
        assertThrows(IllegalArgumentException::class.java) { distributeStartingMoney(2, 0L) }
    }

    @Test
    fun numarInvalidDeJucatoriAruncaExceptie() {
        assertThrows(IllegalArgumentException::class.java) { distributeStartingMoney(1) }
        assertThrows(IllegalArgumentException::class.java) { distributeStartingMoney(5) }
    }
}

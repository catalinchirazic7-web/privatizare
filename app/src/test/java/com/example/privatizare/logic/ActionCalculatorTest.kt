package com.example.privatizare.logic

import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.PropertyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionCalculatorTest {

    // POLI din actions.json: titlu 1000, taxa 200; anexe 2200/350, 3000/800, 5500/1250
    private val poli = PropertyAction(
        id = "poli",
        name = "POLI S.R.L.",
        titlePrice = 1000,
        titleTax = 200,
        annexes = listOf(
            Annex(1, 2200, 350),
            Annex(2, 3000, 800),
            Annex(3, 5500, 1250),
        ),
    )

    @Test
    fun doarTitluDaTaxaTitlului() {
        assertEquals(200L, ActionCalculator.calculateRent(poli, 0))
    }

    @Test
    fun titluPlusAnexa1() {
        assertEquals(550L, ActionCalculator.calculateRent(poli, 1))
    }

    @Test
    fun titluPlusAnexa1Si2() {
        assertEquals(1350L, ActionCalculator.calculateRent(poli, 2))
    }

    @Test
    fun toateAnexele() {
        assertEquals(2600L, ActionCalculator.calculateRent(poli, 3))
    }

    @Test
    fun nivelInvalidAruncaExceptie() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionCalculator.calculateRent(poli, 4)
        }
    }

    @Test
    fun urmatoareaAnexaEsteIndexUnuiPlusNivel() {
        assertEquals(2200L, ActionCalculator.nextAnnex(poli, 0)?.price)
        assertEquals(5500L, ActionCalculator.nextAnnex(poli, 2)?.price)
        assertNull(ActionCalculator.nextAnnex(poli, 3))
    }

    @Test
    fun rascumparareLaOptzecideDinPret() {
        assertEquals(800L, ActionCalculator.sellBackValue(1000))
        assertEquals(4400L, ActionCalculator.sellBackValue(5500))
    }
}

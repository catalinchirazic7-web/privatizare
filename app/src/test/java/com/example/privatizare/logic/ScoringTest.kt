package com.example.privatizare.logic

import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {

    private val poli = PropertyAction(
        id = "poli", name = "POLI", titlePrice = 2500, titleTax = 350,
        annexes = listOf(Annex(1, 2200, 800)),
    )
    private val actions = mapOf("poli" to poli)

    @Test
    fun valoareaAcțiunii_titluPlusAnexaLa80() {
        // titlu 2500 -> 2000, anexa 2200 -> 1760: total 3760
        val p = Player("p", "p", PawnColor.ROSU, 0, ownedProperties = mapOf("poli" to 1))
        assertEquals(3760L, Scoring.holdingsValue(p, actions))
    }

    @Test
    fun clasamentulSeFaceDupaBaniPlusActiuni() {
        val bogat = Player("b", "b", PawnColor.ROSU, 1000, ownedProperties = mapOf("poli" to 1))  // 1000 + 3760 = 4760
        val sarac = Player("s", "s", PawnColor.ALBASTRU, 5000)                                       // 5000
        assertEquals(4760L, Scoring.netWorth(bogat, actions))
        assertEquals(listOf("s", "b"), Scoring.ranking(listOf(bogat, sarac), actions).map { it.id })
    }
}

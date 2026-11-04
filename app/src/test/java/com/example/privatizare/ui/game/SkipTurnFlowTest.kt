package com.example.privatizare.ui.game

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casa de Odihnă: jucătorul trimis acolo ratează exact două aruncări consecutive. */
class SkipTurnFlowTest {

    // 3 = CASA DE ODIHNĂ, restul START (fără efect)
    private val catalog = GameRepository.Catalog(
        board = List(37) { i -> if (i == 2) Tile.CasaDeOdihna(i) else Tile.Start(i) },
        actions = emptyMap<String, PropertyAction>(),
        loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
        telex = listOf(TelexCard("t", "test", emptyList())),
        positions = List(37) { 0.5f to 0.5f },
    )

    private fun vm(): GameViewModel = GameViewModel(catalog).also {
        it.startGame(listOf(PlayerSetup("Ana", PawnColor.ROSU), PlayerSetup("Bogdan", PawnColor.ALBASTRU)))
    }

    @Test
    fun trimisLaCasaDeOdihnaRataExactDouaArunciConsecutive() {
        val vm = vm()

        // Tura 1 a Anei: aterizează la Casa de Odihnă
        vm.roll(3)
        assertEquals(2, vm.game!!.players[0].position)
        assertEquals(2, vm.game!!.players[0].skipTurns)
        vm.passTurn()

        // Tura 1 a lui Bogdan
        vm.roll(1)
        vm.passTurn()

        // Tura 2 a Anei: sare o tură; zarul e blocat, [TERMINĂ TURA] e activ, bannerul arată 1
        assertEquals(0, vm.currentIndex)
        assertEquals(1, vm.game!!.players[0].skipTurns)
        assertTrue(vm.skipBanner!!.contains("Ture rămase de sărit: 1"))
        assertTrue(vm.turnAwaitingEnd)
        assertNull(vm.activeDialog)
        vm.roll(5) // aruncarea ratată nr. 1
        assertEquals(2, vm.game!!.players[0].position)
        vm.passTurn()

        // Tura 1 a lui Bogdan
        vm.roll(1)
        vm.passTurn()

        // Tura 3 a Anei: a doua tură ratată, bannerul arată 0
        assertEquals(0, vm.currentIndex)
        assertEquals(0, vm.game!!.players[0].skipTurns)
        assertTrue(vm.skipBanner!!.contains("Ture rămase de sărit: 0"))
        vm.roll(5) // aruncarea ratată nr. 2
        assertEquals(2, vm.game!!.players[0].position)
        vm.passTurn()

        // Bogdan joacă, apoi Ana se poate deplasa din nou
        vm.roll(1)
        vm.passTurn()
        assertEquals(0, vm.currentIndex)
        assertNull(vm.skipBanner)
        vm.roll(2) // acum zarul funcționează: 2 + 2 = 4
        assertEquals(4, vm.game!!.players[0].position)
    }

    @Test
    fun turaDeSaritNuDeplaseazaSiNuPlateste() {
        val vm = vm()
        vm.roll(3)
        val moneyBefore = vm.game!!.players[0].money
        vm.passTurn()
        vm.roll(1)
        vm.passTurn()
        // Ana sare: nici mutare, nici plată
        assertEquals(2, vm.game!!.players[0].position)
        assertEquals(moneyBefore, vm.game!!.players[0].money)
    }
}

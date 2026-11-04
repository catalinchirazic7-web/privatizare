package com.example.privatizare.ui.game

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fluxul complet al turei: cartonașe, cumpărare de anexe din panou și [TERMINĂ TURA]. */
class GameTurnFlowTest {

    private val poli = PropertyAction(
        id = "poli", name = "POLI", titlePrice = 1000, titleTax = 200,
        annexes = listOf(Annex(1, 2200, 350), Annex(2, 3000, 800), Annex(3, 5500, 1250)),
    )

    // 1 = LOTO, 2 = POLI, restul START (fără efect)
    private val catalog = GameRepository.Catalog(
        board = List(37) { i ->
            when (i) {
                0 -> Tile.Loto(i)
                1 -> Tile.Property(i, "poli")
                else -> Tile.Start(i)
            }
        },
        actions = mapOf("poli" to poli),
        loto = listOf(LotoCard("w", LotoKind.WIN, 500, "Câștigați suma de 500 ECU")),
        telex = listOf(TelexCard("t", "test", emptyList())),
        positions = List(37) { 0.5f to 0.5f },
    )

    private fun vm(): GameViewModel = GameViewModel(catalog).also {
        it.startGame(listOf(PlayerSetup("Ana", PawnColor.ROSU), PlayerSetup("Bogdan", PawnColor.ALBASTRU)))
    }

    @Test
    fun cartonasulBlocheazaAvansareaPanaLaConfirmare() {
        val vm = vm()
        vm.roll(1) // Ana aterizează pe LOTO: pachetul pulsează, cartonașul nu e tras încă
        assertTrue(vm.activeDialog is TurnDialog.DrawPending)
        assertEquals(250_000L, vm.game!!.players[0].money)
        vm.drawCard() // atinge pachetul: cartonașul apare și efectul se aplică
        assertTrue(vm.activeDialog is TurnDialog.CardPending)
        assertEquals(250_500L, vm.game!!.players[0].money) // câștigul s-a aplicat

        vm.roll(3) // zarul e blocat
        vm.passTurn() // [TERMINĂ TURA] nu merge cât cartonașul e deschis
        assertEquals(0, vm.currentIndex)
        assertTrue(vm.activeDialog is TurnDialog.CardPending)

        vm.dismissCard() // „Am înțeles”: tura rămâne deschisă, dar nu trece încă
        assertNull(vm.activeDialog)
        assertEquals(0, vm.currentIndex)
        assertTrue(vm.turnAwaitingEnd)
    }

    @Test
    fun cumparareaAnexeiDinPanouCatTimpTuraEsteDeschisa() {
        val vm = vm()
        vm.roll(2) // Ana aterizează pe POLI liber
        vm.acceptOffer() // cumpără titlul: 250000 - 1000
        assertEquals(249_000L, vm.game!!.players[0].money)
        assertTrue(vm.turnAwaitingEnd)
        assertEquals(0, vm.currentIndex)

        vm.buyNextAnnex("poli") // anexa 1: 2200
        assertEquals(1, vm.game!!.players[0].ownedProperties["poli"])
        assertEquals(246_800L, vm.game!!.players[0].money)

        vm.buyNextAnnex("poli") // anexa 2: 3000
        assertEquals(2, vm.game!!.players[0].ownedProperties["poli"])
        assertEquals(243_800L, vm.game!!.players[0].money)

        assertEquals(0, vm.currentIndex) // tura încă e a lui Ana
    }

    @Test
    fun treceLaUrmatorulJucatorDoarDupaTerminaTura() {
        val vm = vm()
        vm.roll(3) // START, fără dialog
        assertTrue(vm.turnAwaitingEnd)
        assertNull(vm.activeDialog)
        assertEquals(0, vm.currentIndex)

        vm.roll(1) // zarul e blocat până la încheierea turei
        assertEquals(2, vm.game!!.players[0].position)

        vm.passTurn()
        assertEquals(1, vm.currentIndex)
        assertFalse(vm.turnAwaitingEnd)

        vm.roll(1) // acum jucă Bogdan
        assertEquals(0, vm.game!!.players[1].position)
    }

    @Test
    fun cumparareaAnexeiNuEPermisaFaraTuraDeschisa() {
        val vm = vm()
        vm.roll(2)
        vm.acceptOffer()
        vm.passTurn() // Ana a încheiat tura
        val before = vm.game!!.players[0].money
        vm.buyNextAnnex("poli") // nu mai e tura lui Ana deschisă
        assertEquals(before, vm.game!!.players[0].money)
    }
}

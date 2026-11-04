package com.example.privatizare.ui.game

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Partida acceptă 2, 3 sau 4 jucători (oameni sau roboți), cu pioni diferiți. */
class FourPlayersTest {

    private val catalog = GameRepository.Catalog(
        board = List(37) { Tile.Start(it) },
        actions = emptyMap(),
        loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
        telex = listOf(TelexCard("t", "test", emptyList())),
        positions = List(37) { 0.5f to 0.5f },
    )

    private fun setup(n: Int, bots: Set<Int> = emptySet()) =
        (0 until n).map { PlayerSetup("J${it + 1}", PawnColor.entries[it], isBot = it in bots) }

    @Test
    fun patruJucatoriPrimescBaniEgaliSiRotesc() {
        val vm = GameViewModel(catalog)
        vm.startGame(setup(4))
        assertEquals(4, vm.game!!.players.size)
        assertEquals(setOf(125_000L), vm.game!!.players.map { it.money }.toSet())
        repeat(4) { vm.roll(1); vm.passTurn() }
        assertEquals(0, vm.currentIndex)                       // după 4 ture se revine la primul
        assertEquals(listOf(0, 0, 0, 0), vm.game!!.players.map { it.position })
    }

    @Test
    fun treiJucatoriRestulBaniiLaBanca() {
        val vm = GameViewModel(catalog)
        vm.startGame(setup(3))
        assertEquals(165_100L, vm.game!!.players[0].money)
        assertEquals(4_700L, vm.game!!.bankMoney)
    }

    @Test
    fun patruJucatoriDintreCareTreiRoboti() {
        val vm = GameViewModel(catalog)
        vm.startGame(setup(4, bots = setOf(1, 2, 3)))
        assertEquals(listOf(false, true, true, true), vm.game!!.players.map { it.isBot })
    }

    @Test
    fun maximPatruJucatoriSiCuloriDiferite() {
        val vm = GameViewModel(catalog)
        assertThrows(IllegalArgumentException::class.java) {
            vm.startGame((0 until 5).map { PlayerSetup("J$it", PawnColor.entries[it]) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            vm.startGame(listOf(PlayerSetup("A", PawnColor.ROSU), PlayerSetup("B", PawnColor.ROSU)))
        }
    }
}

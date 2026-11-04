package com.example.privatizare.multiplayer

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import com.example.privatizare.ui.game.GameViewModel
import com.example.privatizare.ui.game.TurnDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Camera de joc cap-coadă: gazdă și oaspete pe VM-uri separate, legate printr-un backend în memorie. */
class OnlineFlowTest {

    private val poli = PropertyAction(
        id = "poli", name = "POLI", titlePrice = 1000, titleTax = 200,
        annexes = listOf(Annex(1, 2200, 350)),
    )

    private val catalog = GameRepository.Catalog(
        board = List(37) { i -> if (i == 1) Tile.Property(i, "poli") else Tile.Start(i) },
        actions = mapOf("poli" to poli),
        loto = listOf(LotoCard("l", LotoKind.NOTHING, 0, "nimic")),
        telex = listOf(TelexCard("t", "test", emptyList())),
        positions = List(37) { 0.5f to 0.5f },
    )

    private class Table(catalog: GameRepository.Catalog, die: () -> Int = { 2 }) {
        val backend = InMemoryRoomBackend()
        val hostVm = GameViewModel(catalog)
        val guestVm = GameViewModel(catalog)
        val host = OnlineController(backend, hostVm, hostDie = die, newCode = { "ABCD" })
        val guest = OnlineController(backend, guestVm)
    }

    private fun started(): Table {
        val t = Table(catalog)
        t.host.createRoom("Gazda", PawnColor.ROSU)
        t.guest.joinRoom("abcd", "Oaspete", PawnColor.ALBASTRU)   // codul se normalizează la majuscule
        t.host.startMatch()
        return t
    }

    @Test
    fun gazdaCreeazaCameraCuCodDe4Caractere() {
        val t = Table(catalog)
        t.host.createRoom("Gazda", PawnColor.ROSU)
        assertEquals("ABCD", t.host.code)
        assertTrue(t.host.isHost)
        assertEquals(RoomStage.LOBBY, t.host.info!!.stage)
        assertEquals(listOf("Gazda"), t.host.info!!.players.map { it.name })
    }

    @Test
    fun oaspeteleIntraSiPrimesteUrmatorulLoc() {
        val t = Table(catalog)
        t.host.createRoom("Gazda", PawnColor.ROSU)
        t.guest.joinRoom("ABCD", "Oaspete", PawnColor.ALBASTRU)
        assertEquals("p1", t.guest.me!!.id)
        assertEquals(RoomRole.GUEST, t.guest.me!!.role)
        assertEquals(2, t.host.info!!.players.size)     // gazda vede pe cine a intrat
    }

    @Test
    fun culoareaLuataSauCodulGresitDauEroare() {
        val t = Table(catalog)
        t.host.createRoom("Gazda", PawnColor.ROSU)
        t.guest.joinRoom("ABCD", "Oaspete", PawnColor.ROSU)
        assertEquals("Pionul este deja luat de alt jucător.", t.guest.error)
        assertFalse(t.guest.isInRoom)
        t.guest.joinRoom("ZZZZ", "Oaspete", PawnColor.ALBASTRU)
        assertEquals("Camera nu există.", t.guest.error)
        t.guest.joinRoom("AB", "Oaspete", PawnColor.ALBASTRU)
        assertEquals("Codul are 4 caractere (litere și cifre).", t.guest.error)
    }

    @Test
    fun pionulLuatApareCaIndisponibilInainteDeConectare() {
        val t = Table(catalog)
        t.host.createRoom("Gazda", PawnColor.ROSU)
        t.guest.peekRoom("ab")                                   // cod incomplet: nimic dezactivat
        assertTrue(t.guest.takenColors.isEmpty())
        t.guest.peekRoom("abcd")
        assertEquals(setOf(PawnColor.ROSU), t.guest.takenColors)
        t.guest.peekRoom("ZZZZ")                                 // cameră inexistentă: nimic dezactivat
        assertTrue(t.guest.takenColors.isEmpty())
    }

    @Test
    fun doiOaspetiNuPotAveaAceeasiCuloare() {
        val t = Table(catalog)
        t.host.createRoom("Gazda", PawnColor.ROSU)
        t.guest.joinRoom("ABCD", "Primul", PawnColor.ALBASTRU)
        val second = OnlineController(t.backend, GameViewModel(catalog))
        second.joinRoom("ABCD", "AlDoilea", PawnColor.ALBASTRU)   // culoarea oaspetelui anterior
        assertEquals("Pionul este deja luat de alt jucător.", second.error)
        assertFalse(second.isInRoom)
        assertEquals(2, t.host.info!!.players.size)
        second.joinRoom("ABCD", "AlDoilea", PawnColor.ROSU)       // culoarea gazdei
        assertEquals("Pionul este deja luat de alt jucător.", second.error)
        second.joinRoom("ABCD", "AlDoilea", PawnColor.VERDE)
        assertTrue(second.isInRoom)
        assertEquals(3, t.host.info!!.players.map { it.color }.toSet().size)
    }

    @Test
    fun gazdaCompleteazaLocurileCuRoboti() {
        val t = Table(catalog)
        t.host.createRoom("Gazda", PawnColor.ROSU)
        assertFalse(t.host.info!!.canStart)              // singur la masă
        t.host.addBot()
        t.host.addBot()
        t.host.addBot()
        t.host.addBot()                                   // al patrulea robot nu mai încape
        assertEquals(4, t.host.info!!.players.size)
        assertEquals(3, t.host.info!!.players.count { it.role == RoomRole.BOT })
        assertEquals(4, t.host.info!!.players.map { it.color }.toSet().size)   // culori diferite
        assertTrue(t.host.info!!.canStart)
    }

    @Test
    fun masaPlinaNuMaiPrimesteOaspeti() {
        val t = Table(catalog)
        t.host.createRoom("Gazda", PawnColor.ROSU)
        repeat(3) { t.host.addBot() }
        t.guest.joinRoom("ABCD", "Oaspete", PawnColor.PORTOCALIU)
        assertEquals("Masa este plină.", t.guest.error)
    }

    @Test
    fun doarGazdaPoatePornimeciul() {
        val t = Table(catalog)
        t.host.createRoom("Gazda", PawnColor.ROSU)
        t.guest.joinRoom("ABCD", "Oaspete", PawnColor.ALBASTRU)
        t.guest.startMatch()
        assertFalse(t.guest.inGame)
        t.host.startMatch()
        assertTrue(t.host.inGame)
        assertTrue(t.guest.inGame)                        // oaspetele intră singur când stadiul devine PLAYING
        assertEquals(RoomStage.PLAYING, t.host.info!!.stage)
    }

    @Test
    fun oaspeteleVedeAcelasiJocCaGazda() {
        val t = started()
        assertEquals(listOf("Gazda", "Oaspete"), t.guestVm.game!!.players.map { it.name })
        assertEquals(t.hostVm.game, t.guestVm.game)
        assertTrue(t.guestVm.isGuest)
        assertFalse(t.hostVm.isGuest)
        assertEquals("p1", t.guestVm.myPlayerId)
    }

    @Test
    fun oaspeteleNuPoateActionaCandNuEsteRandul() {
        val t = started()                                 // e rândul gazdei (p0)
        t.guestVm.roll(3)                                 // trimite o intenție
        t.host.publishNow()
        assertEquals(-1, t.hostVm.game!!.players[1].position)   // gazda a respins-o
        assertEquals(0, t.hostVm.currentIndex)
    }

    @Test
    fun turaOaspetelui_zar_cumparare_sfarsit_tura_prin_intentii() {
        val t = started()
        // tura gazdei
        t.hostVm.roll(1)
        t.host.publishNow()
        t.hostVm.endTurn()
        t.host.publishNow()
        assertEquals(1, t.guestVm.currentIndex)           // oaspetele a primit starea: e rândul lui

        // oaspetele aruncă: gazda trage zarul (2) și îl mută pe POLI
        t.guestVm.roll(6)
        assertEquals(1, t.hostVm.game!!.players[1].position)
        assertEquals(1, t.guestVm.game!!.players[1].position)
        assertTrue(t.guestVm.activeDialog is TurnDialog.BuyDecisionPending)

        // oaspetele cumpără titlul
        t.guestVm.acceptOffer()
        assertEquals(0, t.hostVm.game!!.players[1].ownedProperties["poli"])
        assertEquals(0, t.guestVm.game!!.players[1].ownedProperties["poli"])
        assertEquals(t.hostVm.game!!.players[1].money, t.guestVm.game!!.players[1].money)

        // cumpără anexa din panou, apoi încheie tura
        t.guestVm.buyNextAnnex("poli")
        assertEquals(1, t.hostVm.game!!.players[1].ownedProperties["poli"])
        t.guestVm.endTurn()
        assertEquals(0, t.hostVm.currentIndex)
        assertEquals(0, t.guestVm.currentIndex)
    }

    @Test
    fun chiriaSePlatestePrinIntentie() {
        val t = started()
        // gazda cumpără POLI și încheie tura
        t.hostVm.roll(2); t.host.publishNow()
        t.hostVm.acceptOffer(); t.host.publishNow()
        t.hostVm.endTurn(); t.host.publishNow()
        // oaspetele cade pe POLI și plătește chiria
        t.guestVm.roll(1)
        assertTrue(t.guestVm.activeDialog is TurnDialog.RentDuePending)
        val before = t.hostVm.game!!.players[1].money
        t.guestVm.payRent()
        assertEquals(before - 200, t.hostVm.game!!.players[1].money)
        assertEquals(t.hostVm.game, t.guestVm.game)
    }

    @Test
    fun oaspeteleNuEsteBlocatDeRoboti() {
        val t = Table(catalog)
        t.host.createRoom("Gazda", PawnColor.ROSU)
        t.host.addBot()
        t.host.startMatch()
        assertTrue(t.hostVm.game!!.players[1].isBot)
        assertNotNull(t.host.info)
        assertNull(t.guest.code)
    }

    @Test
    fun intentieStricataEsteIgnorata() {
        assertNull(IntentCodec.decode("nu e json"))
        assertNull(IntentCodec.decode("{\"p\":\"p1\",\"t\":\"necunoscut\"}"))
    }
}

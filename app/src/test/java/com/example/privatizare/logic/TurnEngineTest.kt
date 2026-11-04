package com.example.privatizare.logic

import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.CardEffect
import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TurnEngineTest {

    private val poli = PropertyAction(
        id = "poli", name = "POLI", titlePrice = 1000, titleTax = 200,
        annexes = listOf(Annex(1, 2200, 350), Annex(2, 3000, 800), Annex(3, 5500, 1250)),
    )

    // 38 de căsuțe: POLI 2, BANCA 5, LOTO 7, TELEX 15, RECHEMAT 18, CASA 30; restul START (fără efect)
    private val dura = PropertyAction(
        id = "dura", name = "DURA", titlePrice = 2500, titleTax = 350,
        annexes = listOf(Annex(1, 2000, 600)),
    )

    private val board: List<Tile> = List(37) { i ->
        when (i) {
            2 -> Tile.Property(i, "poli")
            9 -> Tile.Property(i, "dura")
            5 -> Tile.Bank(i)
            7 -> Tile.Loto(i)
            15 -> Tile.Telex(i)
            18 -> Tile.RechematUrgent(i)
            30 -> Tile.CasaDeOdihna(i)
            else -> Tile.Start(i)
        }
    }

    private val nothingLoto = listOf(LotoCard("l1", LotoKind.NOTHING, 0, "nimic"))

    private fun engine(telex: List<TelexCard> = listOf(TelexCard("t0", "test", emptyList()))) = TurnEngine(
        board = board,
        actions = mapOf("poli" to poli, "dura" to dura),
        loto = nothingLoto,
        telex = telex,
        random = Random(1),
    )

    private fun game(vararg players: Player) = GameState(players = players.toList(), bankMoney = 500_000)

    private fun player(id: String, money: Long = 5000, pos: Int = 0, skip: Int = 0) =
        Player(id, id, PawnColor.ROSU, money, pos, skipTurns = skip)

    @Test
    fun unSingurZarIntervalul1La6() {
        val e = engine()
        val s = game(player("A"))
        for (bad in listOf(listOf(2, 3), listOf(7), listOf(0), emptyList<Int>())) {
            assertThrows(IllegalArgumentException::class.java) { e.playTurn(s, 0, bad) }
        }
        for (ok in 1..6) {
            e.playTurn(s, 0, listOf(ok)) // nu aruncă
        }
    }

    @Test
    fun trecereaPrinStartAduceBonus() {
        val s = game(player("A", money = 0, pos = 36))
        val r = engine().playTurn(s, 0, listOf(3))
        assertEquals(2, r.state.players[0].position)
        assertEquals(3000L, r.state.players[0].money)
        assertNotNull(r.offer)
    }

    @Test
    fun achizitiaDeAnexaNuDezvaluiePretulInIstoric() {
        val e = engine()
        val s = game(player("A", money = 5000).copy(ownedProperties = mapOf("poli" to 0)))
        val offer = BuyOffer("A", "poli", BuyOption.AnnexOption(poli.annexes[0]))
        val after = e.applyBuy(s, offer)
        val last = after.history.last()
        assertTrue(last.contains("investit într-o anexă la POLI"))
        assertFalse(last.contains("2200"))
    }

    @Test
    fun aterizareaDupaTurCompletAduceBonusulOData() {
        // 36 + 1 = 37 -> tur complet: bonus exact 3000, o singură dată, apoi căsuța 0
        val r = engine().playTurn(game(player("A", money = 0, pos = 36)), 0, listOf(1))
        assertEquals(0, r.state.players[0].position)
        assertEquals(3000L, r.state.players[0].money)
    }

    @Test
    fun mutareTelexPesteStartDaExactUnBonus() {
        // Căsuța 35 e TELEX cu avansare de 4 căsuțe: 32 + 3 = 35 -> 35 + 4 = 39 -> 2, peste START
        val telexBoard: List<Tile> = List(37) { i -> if (i == 35) Tile.Telex(i) else Tile.Start(i) }
        val e = TurnEngine(
            board = telexBoard,
            actions = emptyMap(),
            loto = nothingLoto,
            telex = listOf(TelexCard("adv", "avans", listOf(CardEffect.Move(4)))),
            random = Random(1),
        )
        val r = e.drawCard(e.playTurn(game(player("A", money = 0, pos = 32)), 0, listOf(3)).state, 0, "TELEX")
        // Pionul stă pe TELEX cât se vede cartonașul; mutarea se aplică după confirmare
        assertEquals(35, r.state.players[0].position)
        assertEquals(4, r.pendingMove)
        assertEquals(0L, r.state.players[0].money)
        val moved = e.moveBy(r.state, 0, r.pendingMove!!)
        assertEquals(2, moved.state.players[0].position)
        assertEquals(3000L, moved.state.players[0].money)
    }

    @Test
    fun rechematUrgentMergeLaStartFaraBonus() {
        val s = game(player("A", money = 0, pos = 15))
        val r = engine().playTurn(s, 0, listOf(3))
        assertEquals(-1, r.state.players[0].position)   // pe săgeata de START
        assertEquals(0L, r.state.players[0].money)
    }

    @Test
    fun testRefuzCumparareTitluAchitaTaxaLaBanca() {
        val e = engine()
        val s = game(player("A", money = 5000))
        val r = e.playTurn(s, 0, listOf(2)) // POLI liber: ofertă de titlu
        assertTrue(r.offer!!.option is BuyOption.Title)

        val d = e.declineOffer(r.state, r.offer!!)
        assertNull(d.debt)
        assertEquals(4800L, d.state.players[0].money) // taxa titlului POLI = 200
        assertEquals(500_200L, d.state.bankMoney)
        assertNull(d.state.ownerOf("poli")) // proprietatea rămâne liberă
    }

    @Test
    fun refuzFaraFonduriDevineDatorieCatreBanca() {
        val e = engine()
        val s = game(player("A", money = 100))
        val r = e.playTurn(s, 0, listOf(2))
        val d = e.declineOffer(r.state, r.offer!!)
        assertNotNull(d.debt)
        assertEquals(200L, d.debt!!.amount)
        assertNull(d.debt!!.creditorId) // datorie către Bancă
        assertEquals(100L, d.state.players[0].money)
    }

    @Test
    fun casaDeOdihnaDaDouaTuriDeSkip() {
        val e = engine()
        var s = game(player("A", pos = 26))
        s = e.playTurn(s, 0, listOf(4)).state
        assertEquals(2, s.players[0].skipTurns)
        s = e.playTurn(s, 0, listOf(6)).state
        assertEquals(1, s.players[0].skipTurns)
        assertEquals(30, s.players[0].position)
        s = e.playTurn(s, 0, listOf(6)).state
        assertEquals(0, s.players[0].skipTurns)
        assertEquals(30, s.players[0].position)
    }

    @Test
    fun cumparaTitluApoiPlataChirieiLaAltJucator() {
        val e = engine()
        var s = game(player("A"), player("B"))

        val r1 = e.playTurn(s, 0, listOf(2))
        val offer = r1.offer
        assertNotNull(offer)
        assertTrue(offer!!.option is BuyOption.Title)
        s = e.applyBuy(r1.state, offer)
        assertEquals(4000L, s.players[0].money)
        assertEquals(501_000L, s.bankMoney)
        assertEquals(0, s.players[0].ownedProperties["poli"])

        // B aterizează pe POLI: plătește chiria (doar titlu = 200) lui A
        val r2 = e.playTurn(s, 1, listOf(2))
        assertNull(r2.offer)
        assertNotNull(r2.rentDue)
        assertEquals(5000L, r2.state.players[1].money) // nimic plătit încă
        val paid = e.payRent(r2.state, r2.rentDue!!)
        assertEquals(4200L, paid.state.players[0].money)
        val r2paid = paid
        assertEquals(4800L, r2paid.state.players[1].money)
    }

    @Test
    fun anexaSeCumparaDoarDeProprietarInOrdine() {
        val e = engine()
        var s = game(player("A", pos = 0), player("B"))
        val r1 = e.playTurn(s, 0, listOf(2))
        s = e.applyBuy(r1.state, r1.offer!!)

        // A deține titlul POLI; aterizează pe POLI din poziția 0 cu zarul 2 -> oferta pentru anexa 1
        val r2 = e.playTurn(
            GameState(players = listOf(player("A", pos = 0).copy(ownedProperties = mapOf("poli" to 0)), player("B")), bankMoney = 501_000),
            0, listOf(2),
        )
        val offer = r2.offer
        assertNotNull(offer)
        assertTrue(offer!!.option is BuyOption.AnnexOption)
        assertEquals(1, (offer.option as BuyOption.AnnexOption).annex.index)
    }

    @Test
    fun teleInProprietateaUnuiAltJucatorPlatesteProprietarul() {
        val telex = listOf(TelexCard("t1", "plată proprietar", listOf(CardEffect.PayOwnerOrBank(500, "poli"))))
        val e = engine(telex)
        // B vine pe TELEX (15), A deține POLI
        val s = GameState(
            players = listOf(
                player("A").copy(ownedProperties = mapOf("poli" to 0)),
                player("B", pos = 12),
            ),
            bankMoney = 500_000,
        )
        val landed = e.playTurn(s, 1, listOf(3))
        assertEquals(5000L, landed.state.players[0].money)   // nimic nu se plătește până nu tragi
        val r = e.drawCard(landed.state, 1, "TELEX")
        assertEquals(5500L, r.state.players[0].money)
        assertEquals(4500L, r.state.players[1].money)
    }

    // A deține POLI (titlu + anexa 1, chirie 550). B are 300 și DURA cu anexa 1.
    private fun insolventScenario(bDura: Map<String, Int>, bMoney: Long) = GameState(
        players = listOf(
            player("A").copy(ownedProperties = mapOf("poli" to 1)),
            player("B", money = bMoney, pos = 0).copy(ownedProperties = bDura),
        ),
        bankMoney = 500_000,
    )

    @Test
    fun plataAutomataDepasesteSoldulDaDatorie() {
        val e = engine()
        val r = e.playTurn(insolventScenario(mapOf("dura" to 1), 300), 1, listOf(2))
        assertNotNull(r.rentDue)
        val pay = e.payRent(r.state, r.rentDue!!)
        assertNotNull(pay.debt)
        assertEquals(550L, pay.debt!!.amount)
        assertEquals("B", pay.debt!!.debtorId)
        assertEquals("A", pay.debt!!.creditorId)
        assertEquals(300L, pay.state.players[1].money) // nimic nu s-a debitat
    }

    @Test
    fun lichidareLaOptzeciDinPretAcopera() {
        val e = engine()
        val r0 = e.playTurn(insolventScenario(mapOf("dura" to 1), 300), 1, listOf(2))
        val r = e.payRent(r0.state, r0.rentDue!!)
        val res = e.settleDebt(r.state, r.debt!!, listOf(Sale("dura", 1)))
        // Anexa DURA 2000 -> 80% = 1600; 300 + 1600 = 1900; plătește 550 lui A
        assertNull(res.pending)
        assertFalse(res.bankrupt)
        assertEquals(1350L, res.state.players[1].money)
        assertEquals(5550L, res.state.players[0].money)
        assertEquals(498_400L, res.state.bankMoney)
        assertEquals(0, res.state.players[1].ownedProperties["dura"]) // anexa vândută, titlul rămâne
    }

    @Test
    fun falimentDupaVanzareaTuturorActiunilor() {
        val e = engine()
        // B are doar titlul DURA (2500 -> vânzare 2000), 300 lichizi, datorie 5000 către A
        val s = insolventScenario(mapOf("dura" to 0), 300)
        val debt = Debt("B", 5000, "A", "test faliment")
        val res = e.settleDebt(s, debt, listOf(Sale("dura", null)))
        assertTrue(res.bankrupt)
        assertNull(res.pending)
        assertEquals(1, res.state.players.size)
        assertEquals("A", res.state.players[0].id)
        // A primește restul: 5000 (inițial) + 2300 (rest după vânzare)
        assertEquals(7300L, res.state.players[0].money)
    }

    @Test
    fun falimentFaraNimicDeVandut() {
        val e = engine()
        val s = insolventScenario(emptyMap(), 300)
        val res = e.settleDebt(s, Debt("B", 550, "A", "test"), emptyList())
        assertTrue(res.bankrupt)
        assertEquals(1, res.state.players.size)
        assertEquals(5300L, res.state.players[0].money)
    }

    @Test
    fun vanzareaIntregiiActiuni_titluPlusAnexe_scoateProprietateaDePeTabla() {
        val e = engine()
        val s = insolventScenario(mapOf("dura" to 1), 300)
        val res = e.settleDebt(s, Debt("B", 3000, "A", "test"), listOf(Sale("dura", null)))
        // DURA: anexa 2000 -> 1600 + titlul 2500 -> 2000 = 3600; 300 + 3600 = 3900, plătește 3000 lui A
        assertNull(res.pending)
        assertFalse(res.bankrupt)
        val b = res.state.players.first { it.id == "B" }
        assertNull(b.ownedProperties["dura"])
        assertEquals(900L, b.money)
        assertEquals(8000L, res.state.players.first { it.id == "A" }.money) // 5000 de start + 3000
    }

    @Test
    fun laCasaDeOdihna_iesePrimulCareAAjunsPrimul() {
        val e = engine()
        // ambii au terminat cele 2 ture; A a ajuns primul (houseSince 1), B al doilea (2)
        val a = player("A", pos = 30).copy(houseSince = 1)
        val b = player("B", pos = 30).copy(houseSince = 2)
        val s = game(a, b)
        assertFalse(e.isWaitingAtHouse(s, 0))
        assertTrue(e.isWaitingAtHouse(s, 1))
        // B așteaptă: zarul nu-l mută
        val rB = e.playTurn(s, 1, listOf(3))
        assertEquals(30, rB.state.players[1].position)
        // A pleacă: 30 + 3 = 33
        val rA = e.playTurn(s, 0, listOf(3))
        assertEquals(33, rA.state.players[0].position)
    }
}

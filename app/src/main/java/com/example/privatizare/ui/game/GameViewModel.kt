package com.example.privatizare.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.Tile
import com.example.privatizare.logic.BotAI
import com.example.privatizare.logic.GameRules
import com.example.privatizare.multiplayer.CardSnap
import com.example.privatizare.multiplayer.DebtSnap
import com.example.privatizare.multiplayer.GameEvent
import com.example.privatizare.multiplayer.GameSnapshot
import com.example.privatizare.multiplayer.OfferSnap
import com.example.privatizare.multiplayer.RentSnap
import com.example.privatizare.logic.BuyOffer
import com.example.privatizare.logic.BuyOption
import com.example.privatizare.logic.Debt
import com.example.privatizare.logic.RentDue
import com.example.privatizare.logic.Sale
import com.example.privatizare.logic.CardOutcome
import com.example.privatizare.logic.Scoring
import com.example.privatizare.logic.ShownCard
import com.example.privatizare.logic.TurnEngine
import com.example.privatizare.logic.distributeStartingMoney

/** Setarea unui jucător din ecranul de configurare. */
data class PlayerSetup(val name: String, val color: PawnColor, val isBot: Boolean = false)

/** Dialogul activ al turei. Cât timp există unul nerezolvat, zarul e blocat și tura nu trece. */
sealed class TurnDialog {
    data class BuyDecisionPending(val offer: BuyOffer, val property: PropertyAction) : TurnDialog() {
        val titlePrice: Long get() = property.titlePrice
        val titleTax: Long get() = property.titleTax
    }

    data class BankFeePending(val offer: BuyOffer, val property: PropertyAction) : TurnDialog() {
        val taxAmount: Long get() = property.titleTax
    }

    data class RentDuePending(val due: RentDue, val property: PropertyAction, val owner: Player) : TurnDialog() {
        val rentAmount: Long get() = due.amount
    }

    data class CardPending(val card: ShownCard) : TurnDialog()

    /** Jucătorul a ajuns pe LOTO/TELEX și trebuie să tragă (atinge pachetul de pe tablă). Nu are fereastră proprie. */
    data class DrawPending(val kind: String) : TurnDialog()

    data class DebtPending(val debt: Debt) : TurnDialog()

    /** Predarea dispozitivului debitorului înainte să vadă lista de active de vândut. */
    data class LiquidationHandover(val debtorName: String) : TurnDialog()

    /** Predarea telefonului către următorul jucător. Zarul e blocat până la „Sunt gata”. */
    data class HandoverPending(val playerName: String) : TurnDialog()
}

/**
 * Gestionează o partidă: rulează [TurnEngine] și expune starea către UI.
 * [currentIndex] indică jucătorul la rând în [GameState.players].
 */
class GameViewModel(
    private val catalog: GameRepository.Catalog,
    /** Zarul robotului. Injectabil ca testele să fie deterministe. */
    private val botDie: () -> Int = { (1..6).random() },
) : ViewModel() {

    private val engine = TurnEngine(catalog.board, catalog.actions, catalog.loto, catalog.telex)

    // --- Mod online (gazdă autoritară) ---
    /** Id-ul jucătorului de pe acest telefon; null în modul local (pass-and-play). */
    var myPlayerId: String? = null
        private set
    private var intentSender: ((GameEvent) -> Unit)? = null

    val isOnline: Boolean get() = myPlayerId != null

    /** Oaspete: nu rulează motorul, doar trimite intenții și afișează starea primită de la gazdă. */
    val isGuest: Boolean get() = intentSender != null

    fun enterOnlineHost(myId: String) {
        myPlayerId = myId
        intentSender = null
    }

    fun enterOnlineGuest(myId: String, sender: (GameEvent) -> Unit) {
        myPlayerId = myId
        intentSender = sender
    }

    fun leaveOnline() {
        myPlayerId = null
        intentSender = null
    }

    /** Pe oaspete: trimite intenția către gazdă și returnează true. Pe gazdă/local: false, acțiunea rulează local. */
    private fun guestSend(build: (String) -> GameEvent): Boolean {
        val sender = intentSender ?: return false
        val id = myPlayerId ?: return true
        sender(build(id))
        return true
    }

    private fun tileOf(propertyId: String): Int =
        catalog.board.indexOfFirst { it is Tile.Property && it.propertyId == propertyId }

    /** Starea oficială, de publicat (gazdă). Null înainte de începerea partidei. */
    fun snapshot(): GameSnapshot? {
        val g = game ?: return null
        fun offerSnap(o: BuyOffer?) = o?.let {
            OfferSnap(it.playerId, it.propertyId, (it.option as? BuyOption.AnnexOption)?.annex?.index)
        }
        return GameSnapshot(
            state = g,
            currentIndex = currentIndex,
            lastDice = lastDice,
            winnerName = winnerName,
            turnAwaitingEnd = turnAwaitingEnd,
            skipBanner = skipBanner,
            offer = offerSnap(offer),
            feeOffer = offerSnap(feeOffer),
            rentDue = rentDue?.let { RentSnap(it.payerId, it.ownerId, it.propertyId, it.amount) },
            debt = debt?.let { DebtSnap(it.debtorId, it.amount, it.creditorId, it.reason) },
            card = shownCard?.let { CardSnap(it.kind, it.text, it.outcome.name, it.amount, it.destination, it.cardId) },
            pendingDraw = pendingDraw,
            diceCount = diceCount,
            boardLayout = boardLayout,
        )
    }

    /** Oaspete: înlocuiește starea locală cu cea oficială primită de la gazdă. */
    fun applySnapshot(s: GameSnapshot) {
        fun toOffer(o: OfferSnap?): BuyOffer? = o?.let {
            val prop = catalog.actions[it.propertyId] ?: return null
            val option = if (it.annexIndex == null) {
                BuyOption.Title(prop.titlePrice)
            } else {
                BuyOption.AnnexOption(prop.annexes.first { a -> a.index == it.annexIndex })
            }
            BuyOffer(it.playerId, it.propertyId, option)
        }
        game = s.state
        diceCount = s.diceCount
        boardLayout = s.boardLayout
        currentIndex = s.currentIndex
        if (s.lastDice != lastDice) rollNonce++
        lastDice = s.lastDice
        winnerName = s.winnerName
        turnAwaitingEnd = s.turnAwaitingEnd
        skipBanner = s.skipBanner
        offer = toOffer(s.offer)
        feeOffer = toOffer(s.feeOffer)
        rentDue = s.rentDue?.let { RentDue(it.payerId, it.ownerId, it.propertyId, it.amount) }
        debt = s.debt?.let { Debt(it.debtorId, it.amount, it.creditorId, it.reason) }
        pendingDraw = s.pendingDraw
        shownCard = s.card?.let {
            ShownCard(
                it.kind, it.text,
                runCatching { CardOutcome.valueOf(it.outcome) }.getOrDefault(CardOutcome.NEUTRAL),
                it.amount, it.destination, it.cardId,
            )
        }
        handoverTo = null
        debtAcknowledged = null
    }

    var game by mutableStateOf<GameState?>(null)
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var lastDice by mutableStateOf<List<Int>>(emptyList())
    /** Un zar (implicit) sau două zaruri: modul de joc ales în configurare. */
    var diceCount by mutableStateOf(1)
        private set
    /** Tabla partidei: "old" (veche) sau "new" (nouă). */
    var boardLayout by mutableStateOf("old")
        private set

    /**
     * Crește la fiecare aruncare reală de zar. Zarul din interfață se animă doar când se schimbă
     * acest număr — altfel s-ar re-învârti la orice intrare nouă în istoric (cumpărare, chirie etc.).
     */
    var rollNonce by mutableIntStateOf(0)
        private set
    var winnerName by mutableStateOf<String?>(null)
        private set

    private var offer by mutableStateOf<BuyOffer?>(null)
    private var feeOffer by mutableStateOf<BuyOffer?>(null)
    private var rentDue by mutableStateOf<RentDue?>(null)
    private var debt by mutableStateOf<Debt?>(null)
    private var shownCard by mutableStateOf<ShownCard?>(null)

    /** Mutare de aplicat după confirmarea cartonașului TELEX (doar pe gazdă / local). */
    private var pendingMove: Int? = null

    /** Pachetul din care trebuie tras ('LOTO' / 'TELEX'). */
    var pendingDraw by mutableStateOf<String?>(null)
        private set

    /** Tura e deschisă după acțiunea căsuței: zarul e blocat, iar jucătorul închide tura cu [endTurn]. */
    var turnAwaitingEnd by mutableStateOf(false)
        private set

    /** Bannerul afișat când jucătorul la rând stă la Casa de Odihnă. Null în restul timpului. */
    var skipBanner by mutableStateOf<String?>(null)
        private set

    /** Datoria pentru care debitorul a confirmat predarea dispozitivului. */
    private var debtAcknowledged by mutableStateOf<Debt?>(null)

    /** Jucătorul către care se predă ecranul; null când nu e nevoie de predare. */
    private var handoverTo by mutableStateOf<String?>(null)

    val currentPlayer: Player?
        get() = game?.players?.getOrNull(currentIndex)

    /** Dialogul activ, sau null dacă tura poate continua. */
    val activeDialog: TurnDialog?
        get() {
            val g = game ?: return null
            debt?.let { d ->
                if (!isOnline && debtAcknowledged != d) {
                    val name = g.players.firstOrNull { it.id == d.debtorId }?.name ?: return null
                    return TurnDialog.LiquidationHandover(name)
                }
                return TurnDialog.DebtPending(d)
            }
            offer?.let { o -> return TurnDialog.BuyDecisionPending(o, catalog.actions.getValue(o.propertyId)) }
            feeOffer?.let { o -> return TurnDialog.BankFeePending(o, catalog.actions.getValue(o.propertyId)) }
            rentDue?.let { r ->
                val owner = g.players.firstOrNull { it.id == r.ownerId } ?: return null
                return TurnDialog.RentDuePending(r, catalog.actions.getValue(r.propertyId), owner)
            }
            shownCard?.let { return TurnDialog.CardPending(it) }
            pendingDraw?.let { return TurnDialog.DrawPending(it) }
            handoverTo?.let { id ->
                val name = g.players.firstOrNull { it.id == id }?.name ?: return null
                return TurnDialog.HandoverPending(name)
            }
            return null
        }

    /** Proprietatea de pe căsuța pe care stă jucătorul, dacă există. */
    fun landedPropertyId(playerId: String): String? {
        val p = game?.players?.firstOrNull { it.id == playerId } ?: return null
        return (catalog.board.getOrNull(p.position) as? Tile.Property)?.propertyId
    }

    /** Proprietatea de pe o căsuță după index, dacă e proprietate. */
    fun propertyAtTile(index: Int): String? =
        (catalog.board.getOrNull(index) as? Tile.Property)?.propertyId

    /** Oferta de cumpărare activă, dacă există. */
    val pendingOffer: BuyOffer? get() = offer

    /** Acțiunile jucătorului pentru dialogul de lichidare și panou. */
    fun sellableFor(playerId: String): List<Sale> {
        val p = game?.players?.firstOrNull { it.id == playerId } ?: return emptyList()
        // anexa de sus, și întreaga acțiune (titlu + anexe), ca jucătorul să poată alege
        return p.ownedProperties.flatMap { (propId, level) ->
            if (level > 0) listOf(Sale(propId, level), Sale(propId, null)) else listOf(Sale(propId, null))
        }
    }

    /** Timpul limită al partidei, în milisecunde de la epoca Unix; null = fără limită. */
    var deadlineMs by mutableStateOf<Long?>(null)
        private set
    /** Clasamentul de la final (text pe rând), când partida s-a încheiat după timp. */
    var finalRanking by mutableStateOf<List<String>>(emptyList())
        private set

    /** Sfârșitul timpului: câștigă jucătorul cu cel mai mare scor (bani plus acțiuni la 80%). */
    fun timeUp() {
        val g = game ?: return
        if (winnerName != null) return
        val ranked = Scoring.ranking(g.players, catalog.actions)
        finalRanking = ranked.map { "${it.name}: ${Scoring.netWorth(it, catalog.actions)} ECU" }
        winnerName = ranked.first().name
        deadlineMs = null
    }

    /** Reia o partidă salvată. [leftMs] = timpul rămas, null dacă partida nu avea limită. */
    fun restore(s: GameSnapshot, leftMs: Long?) {
        applySnapshot(s)
        deadlineMs = leftMs?.let { System.currentTimeMillis() + it }
    }

    /** Timpul rămas în ms, sau null dacă partida nu are limită. */
    fun timeLeftMs(): Long? = deadlineMs?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) }

    /** [moneyPerPlayer] = suma de start aleasă în configurare; null = împărțire egală a tirajului (maxim). */
    fun startGame(
        setup: List<PlayerSetup>,
        moneyPerPlayer: Long? = null,
        timeLimitMinutes: Int? = null,
        dice: Int = 1,
        layout: String = "old",
    ) {
        require(dice == 1 || dice == 2) { "Un zar sau două" }
        diceCount = dice
        boardLayout = if (layout == "new") "new" else "old"
        deadlineMs = timeLimitMinutes?.let { System.currentTimeMillis() + it * 60_000L }
        finalRanking = emptyList()
        require(setup.size in 2..4) { "Între 2 și 4 jucători" }
        require(setup.map { it.color }.toSet().size == setup.size) { "Culori duplicate" }
        require(setup.any { !it.isBot }) { "Cel puțin un jucător trebuie să fie om" }
        val dist = if (moneyPerPlayer == null) distributeStartingMoney(setup.size)
            else distributeStartingMoney(setup.size, moneyPerPlayer)
        val players = setup.mapIndexed { i, s ->
            Player(id = "p$i", name = s.name, color = s.color, money = dist.playerMoney, isBot = s.isBot, position = GameRules.START_POSITION)
        }
        game = GameState(players = players, bankMoney = dist.bankMoney)
        currentIndex = 0
        offer = null
        feeOffer = null
        rentDue = null
        debt = null
        shownCard = null
        pendingMove = null
        pendingDraw = null
        turnAwaitingEnd = false
        winnerName = null
        lastDice = emptyList()
        skipBanner = null
        handoverTo = null
        debtAcknowledged = null
        beginTurnIfSkipping()
    }

    /** [SUNT GATA] la predarea pentru lichidare: se deschide lista de active. */
    fun acknowledgeLiquidation() {
        debtAcknowledged = debt
    }

    /** [Sunt gata] din ecranul de predare: jucătorul la rând preia telefonul. */
    fun acknowledgeHandover() {
        if (handoverTo == null) return
        handoverTo = null
    }

    private fun prepareHandover() {
        if (isOnline) return   // fiecare are telefonul lui: nu e nevoie de predare
        handoverTo = game?.players?.getOrNull(currentIndex)?.id
    }

    /**
     * La începutul turei, dacă jucătorul la rând stă la Casa de Odihnă, îi scade o tură fără zar.
     * Tura rămâne deschisă: zarul e blocat, iar [TERMINĂ TURA] predă rândul.
     */
    private fun beginTurnIfSkipping() {
        val g = game ?: return
        val p = g.players.getOrNull(currentIndex) ?: return
        if (p.skipTurns <= 0) {
            // a terminat ture, dar altul a ajuns înaintea lui la Casa de Odihnă: așteaptă rândul de ieșire
            if (engine.isWaitingAtHouse(g, currentIndex)) {
                skipBanner = "${p.name} așteaptă la Casa de Odihnă: iese primul cel care a ajuns înaintea lui"
                turnAwaitingEnd = true
            }
            return
        }
        val result = engine.skipTurn(g, currentIndex)
        game = result.state
        val left = p.skipTurns - 1
        skipBanner = "${p.name} staționează la Casa de Odihnă! Ture rămase de sărit: $left"
        turnAwaitingEnd = true
    }

    /** Aruncă zarul și mută pionul. Blocat cât timp există un dialog activ. */
    fun roll(die: Int) = rollDice(listOf(die))

    /** Aruncă unul sau două zaruri (suma lor e pasul); online rămâne la un zar. */
    fun rollDice(dice: List<Int>) {
        if (guestSend { GameEvent.RollDiceEvent(it, dice.first()) }) return
        val g = game ?: return
        if (activeDialog != null || turnAwaitingEnd || winnerName != null) return
        val result = engine.playTurn(g, currentIndex, dice)
        game = result.state
        lastDice = dice
        rollNonce++
        offer = result.offer
        rentDue = result.rentDue
        debt = result.debt
        debtAcknowledged = null
        shownCard = result.card
        pendingMove = result.pendingMove
        pendingDraw = result.pendingDraw
        turnAwaitingEnd = true
        checkWinner()
    }

    /** [DA, CUMPĂR]: scade prețul și acordă proprietatea. */
    fun acceptOffer() {
        val o = offer ?: return
        if (guestSend { GameEvent.BuyPropertyEvent(it, tileOf(o.propertyId)) }) return
        val g = game ?: return
        game = engine.applyBuy(g, o)
        offer = null
        turnAwaitingEnd = true
        checkWinner()
    }

    /** [NU] la titlu deschide balonul cu taxa; [NU] la anexă nu are taxă. */
    fun refuseOffer() {
        val o = offer ?: return
        if (guestSend { GameEvent.DeclinePropertyEvent(it, tileOf(o.propertyId)) }) return
        offer = null
        if (o.option is BuyOption.Title) {
            feeOffer = o
        } else {
            turnAwaitingEnd = true
        }
    }

    /** [OK] din balonul de taxă: transferul către Bancă se execută acum. */
    fun acknowledgeBankFee() {
        if (guestSend { GameEvent.AcknowledgeEvent(it) }) return
        val o = feeOffer ?: return
        val g = game ?: return
        val result = engine.declineOffer(g, o)
        game = result.state
        feeOffer = null
        debt = result.debt
        debtAcknowledged = null
        turnAwaitingEnd = true
        checkWinner()
    }

    /** [PLĂTEȘTE] chiria. Dacă lipsesc fonduri, datoria deschide dialogul de lichidare. */
    fun payRent() {
        if (guestSend { GameEvent.PayRentEvent(it) }) return
        val r = rentDue ?: return
        val g = game ?: return
        val result = engine.payRent(g, r)
        game = result.state
        rentDue = null
        debt = result.debt
        debtAcknowledged = null
        turnAwaitingEnd = true
        checkWinner()
    }

    /**
     * Un singur pas al robotului la rând: confirmă ecranele de predare, decide dialogurile și aruncă zarul
     * sau încheie tura. Returnează false dacă jucătorul la rând nu e robot.
     * Apelantul așteaptă o secundă între pași (vizual).
     */
    fun botNextAction(): Boolean {
        if (isGuest) return false
        val p = currentPlayer ?: return false
        if (!p.isBot || winnerName != null) return false
        when (val d = activeDialog) {
            is TurnDialog.HandoverPending -> acknowledgeHandover()
            is TurnDialog.LiquidationHandover -> acknowledgeLiquidation()
            is TurnDialog.DebtPending -> settleDebt(
                BotAI.liquidationSales(p.id, d.debt.amount, game ?: return false, catalog.board, catalog.actions),
            )
            is TurnDialog.BuyDecisionPending -> {
                val accept = BotAI.decideBuy(
                    game ?: return false, p.id, catalog.board, catalog.actions,
                    d.offer.propertyId, d.offer.option,
                )
                if (accept) acceptOffer() else refuseOffer()
            }
            is TurnDialog.BankFeePending -> acknowledgeBankFee()
            is TurnDialog.RentDuePending -> payRent()
            is TurnDialog.CardPending -> dismissCard()
            is TurnDialog.DrawPending -> drawCard()
            null -> if (turnAwaitingEnd) endTurn() else rollDice(List(diceCount) { botDie() })
        }
        return true
    }

    /** Trage manual cartonașul din pachetul pe care a ajuns jucătorul (atingerea pachetului de pe tablă). */
    fun drawCard() {
        if (guestSend { GameEvent.DrawCardEvent(it) }) return
        val kind = pendingDraw ?: return
        val g = game ?: return
        val r = engine.drawCard(g, currentIndex, kind)
        game = r.state
        pendingDraw = null
        shownCard = r.card
        pendingMove = r.pendingMove
        debt = r.debt
        turnAwaitingEnd = true
        checkWinner()
    }

    /** Confirmarea cartonașului LOTO/TELEX. */
    fun dismissCard() {
        if (guestSend { GameEvent.AcknowledgeEvent(it) }) return
        if (shownCard == null) return
        shownCard = null
        val steps = pendingMove
        pendingMove = null
        if (steps != null) {
            val g = game ?: return
            val r = engine.moveBy(g, currentIndex, steps)
            game = r.state
            offer = r.offer
            rentDue = r.rentDue
            debt = r.debt
            shownCard = r.card
            pendingMove = r.pendingMove
            pendingDraw = r.pendingDraw
        }
        turnAwaitingEnd = true
        checkWinner()
    }

    /**
     * Cumpără următoarea anexă a unei proprietăți deținute. Nu încheie tura: e o cumpărare din panou.
     * Anexele se cumpără în ordine, iar motorul verifică fondurile și proprietarul.
     */
    fun buyNextAnnex(propertyId: String) {
        if (guestSend { GameEvent.BuyAnnexEvent(it, tileOf(propertyId)) }) return
        val g = game ?: return
        val owner = g.ownerOf(propertyId) ?: return
        val level = owner.ownedProperties.getValue(propertyId)
        val prop = catalog.actions.getValue(propertyId)
        val annex = prop.annexes.firstOrNull { it.index == level + 1 } ?: return
        if (!turnAwaitingEnd || activeDialog != null) return
        game = engine.applyBuy(g, BuyOffer(owner.id, propertyId, BuyOption.AnnexOption(annex)))
    }

    /** Aplică vânzările alese pentru datorie. Dacă datoria e stinsă, tura se încheie. */
    fun settleDebt(sales: List<Sale>) {
        if (isGuest) {
            if (sales.isEmpty()) guestSend { GameEvent.BankruptEvent(it) }
            else sales.forEach { sale -> guestSend { GameEvent.SellEvent(it, sale.propertyId, sale.annexIndex) } }
            return
        }
        val d = debt ?: return
        val g = game ?: return
        val res = engine.settleDebt(g, d, sales)
        game = res.state
        if (res.pending == null) {
            debt = null
            debtAcknowledged = null
            if (res.bankrupt) {
                // Jucătorul a fost eliminat: următorul jucător e deja la index și nu mai există tură de încheiat
                val size = game?.players?.size ?: 0
                if (size > 0) currentIndex %= size
                turnAwaitingEnd = false
                skipBanner = null
                prepareHandover()
                beginTurnIfSkipping()
            } else {
                turnAwaitingEnd = true
            }
        } else {
            debt = res.pending
        }
        checkWinner()
    }

    /**
     * [TERMINĂ TURA]: trece la jucătorul următor și reactivează zarul.
     * Nu are efect cât timp există un dialog nerezolvat sau tura nu s-a jucat încă.
     */
    fun endTurn() {
        if (guestSend { GameEvent.EndTurnEvent(it) }) return
        if (activeDialog != null || !turnAwaitingEnd) return
        val size = game?.players?.size ?: return
        if (size == 0) return
        currentIndex = (currentIndex + 1) % size
        turnAwaitingEnd = false
        skipBanner = null
        prepareHandover()
        beginTurnIfSkipping()
    }

    private fun checkWinner() {
        val players = game?.players ?: return
        if (players.size == 1) winnerName = players[0].name
    }
}

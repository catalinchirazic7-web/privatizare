package com.example.privatizare.logic

import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.CardEffect
import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.PropertyStats
import com.example.privatizare.data.model.Tile
import com.example.privatizare.data.model.TelexCard
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

sealed class BuyOption {
    data class Title(val price: Long) : BuyOption()
    data class AnnexOption(val annex: Annex) : BuyOption()
}

/** Oferta de cumpărare făcută jucătorului după aterizare. Jucătorul decide prin [TurnEngine.applyBuy]. */
data class BuyOffer(val playerId: String, val propertyId: String, val option: BuyOption)

/** Datorie care nu poate fi plătită din bani lichizi. [creditorId] = null înseamnă Banca. */
data class Debt(val debtorId: String, val amount: Long, val creditorId: String?, val reason: String)

/** Vânzare de acțiune înapoi la Bancă. Titlul se vinde doar după ce anexele sunt vândute. */
data class Sale(val propertyId: String, val annexIndex: Int?)

/** [pending] = datoria încă nestinsă; [bankrupt] = jucătorul a fost eliminat din joc. */
data class SettleResult(val state: GameState, val pending: Debt?, val bankrupt: Boolean)

data class TurnResult(
    val state: GameState,
    val offer: BuyOffer?,
    val events: List<String>,
    val debt: Debt? = null,
    val rentDue: RentDue? = null,
    val card: ShownCard? = null,
    /** Mutare cerută de un cartonaș TELEX (+ înainte, − înapoi), aplicată după confirmarea cartonașului. */
    val pendingMove: Int? = null,
    /** Pachetul din care jucătorul trebuie să tragă ('LOTO' / 'TELEX'); tragerea se face prin [TurnEngine.drawCard]. */
    val pendingDraw: String? = null,
)

/** Chirie datorată după aterizarea pe o proprietate a altui jucător. Plata e explicită, prin [TurnEngine.payRent]. */
data class RentDue(val payerId: String, val ownerId: String, val propertyId: String, val amount: Long)

/** Cartonașul tras (LOTO sau TELEX), afișat jucătorului ca o carte de joc. */
data class ShownCard(
    val kind: String,
    val text: String,
    val outcome: CardOutcome = CardOutcome.NEUTRAL,
    val amount: Long? = null,
    /** Linia „Banii se achită către: …” (doar TELEX cu plată), rezolvată în momentul tragerii. */
    val destination: String? = null,
    /** Id-ul cartonașului din pachet (pentru găsirea imaginii lui). */
    val cardId: String? = null,
)

private class DebtException(val debt: Debt) : RuntimeException()

/**
 * Motorul turei. Nu avansează ordinea jucătorilor: apelantul o face.
 * Pentru o mutare, apelează [playTurn]. Pentru cumpărare, apelează [applyBuy].
 */
class TurnEngine(
    private val board: List<Tile>,
    private val actions: Map<String, PropertyAction>,
    loto: List<LotoCard>,
    telex: List<TelexCard>,
    private val random: Random = SecureRandom().asKotlinRandom(),
) {
    private val lotoDeck = CardDeck(loto, random)
    private val telexDeck = CardDeck(telex, random)

    fun playTurn(state: GameState, playerIndex: Int, dice: List<Int>): TurnResult {
        require(dice.size in 1..2 && dice.all { it in 1..6 }) { "Unul sau două zaruri, valori 1..6: $dice" }
        val ctx = Ctx(state)
        val player = ctx.players[playerIndex]

        if (player.skipTurns > 0) {
            ctx.players[playerIndex] = player.copy(skipTurns = player.skipTurns - 1)
            ctx.log("${player.name} stă o tură")
            return ctx.result(state)
        }
        if (isWaitingAtHouse(state, playerIndex)) {
            ctx.log("${player.name} așteaptă la Casa de Odihnă: iese primul cel care a ajuns înaintea lui")
            return ctx.result(state)
        }

        val raw = player.position + dice.sum()
        if (raw >= board.size) ctx.bankPays(playerIndex, GameRules.START_BONUS, "trecere prin START")
        ctx.moveTo(playerIndex, raw % board.size)
        ctx.log("${player.name} a dat ${dice.joinToString("+")}")
        try {
            resolveLanding(ctx, playerIndex, depth = 0)
        } catch (e: DebtException) {
            ctx.pending = e.debt
            ctx.log("Fonduri insuficiente: ${e.debt.debtorId} datorează ${e.debt.amount} (${e.debt.reason})")
        }
        return ctx.result(state)
    }

    /**
     * Casa de Odihnă: cine a ajuns primul iese primul. Un jucător cu skipTurns == 0, aflat pe aceeași căsuță,
     * așteaptă dacă un alt jucător de pe ea a ajuns înaintea lui ([Player.houseSince] mai mic).
     */
    fun isWaitingAtHouse(state: GameState, playerIndex: Int): Boolean {
        val p = state.players[playerIndex]
        if (p.skipTurns > 0) return false
        if (p.position !in board.indices || board[p.position] !is Tile.CasaDeOdihna) return false
        return state.players.any { q ->
            q.id != p.id && q.position == p.position && q.houseSince < p.houseSince
        }
    }

    /**
     * Tura unui jucător care stă la Casa de Odihnă: scade o tură, fără zar, fără mutare și fără plăți.
     * Se apelează la începutul turei, când [GameState.players] la [playerIndex] are skipTurns > 0.
     */
    fun skipTurn(state: GameState, playerIndex: Int): TurnResult {
        val ctx = Ctx(state)
        val p = ctx.players[playerIndex]
        require(p.skipTurns > 0) { "${p.name} nu are ture de sărit" }
        ctx.players[playerIndex] = p.copy(skipTurns = p.skipTurns - 1)
        ctx.log("${p.name} staționează la Casa de Odihnă; ture rămase de sărit: ${p.skipTurns - 1}")
        return ctx.result(state)
    }

    /**
     * Mutarea cerută de un cartonaș (după ce jucătorul l-a confirmat): [steps] > 0 înainte, < 0 înapoi.
     * Trecerea peste START înainte aduce bonusul; apoi se rezolvă căsuța pe care ajunge pionul
     * (cumpărare, chirie, alt cartonaș etc.), ca la o aruncare normală.
     */
    fun moveBy(state: GameState, playerIndex: Int, steps: Int): TurnResult {
        val ctx = Ctx(state)
        val player = ctx.players[playerIndex]
        val raw = player.position + steps
        if (steps > 0 && raw >= board.size) ctx.bankPays(playerIndex, GameRules.START_BONUS, "trecere prin START")
        ctx.moveTo(playerIndex, ((raw % board.size) + board.size) % board.size)
        try {
            resolveLanding(ctx, playerIndex, depth = 1)
        } catch (e: DebtException) {
            ctx.pending = e.debt
            ctx.log("Fonduri insuficiente: ${e.debt.debtorId} datorează ${e.debt.amount} (${e.debt.reason})")
        }
        return ctx.result(state)
    }

    /**
     * Tragerea manuală a unui cartonaș din pachetul [kind] ('LOTO' / 'TELEX'): îl extrage, aplică efectele
     * (încasări, plăți, tură pierdută) și îl returnează în [TurnResult.card]; o eventuală mutare rămâne în
     * [TurnResult.pendingMove] și se aplică după confirmare.
     */
    fun drawCard(state: GameState, playerIndex: Int, kind: String): TurnResult {
        val ctx = Ctx(state)
        val player = ctx.players[playerIndex]
        // CatalinRobot: în majoritatea cazurilor scoate cartonașul cel mai bun pentru el din teanc
        val chooses = player.catalin && random.nextDouble() < BotAI.CATALIN_CONTROL
        try {
            when (kind) {
                "LOTO" -> {
                    val card = if (chooses) lotoDeck.drawBest(::lotoValue) else lotoDeck.draw()
                    ctx.log("${player.name} a tras din pachetul LOTO: ${card.text}")
                    ctx.card = lotoResult(card).let { ShownCard("LOTO", card.text, it.outcome, it.amount, cardId = card.id) }
                    when (card.kind) {
                        LotoKind.WIN -> ctx.bankPays(playerIndex, card.amount, "LOTO")
                        LotoKind.LOSE -> ctx.playerPaysBank(playerIndex, card.amount, "LOTO")
                        LotoKind.NOTHING -> Unit
                    }
                }
                "TELEX" -> {
                    val card = if (chooses) telexDeck.drawBest(::telexValue) else telexDeck.draw()
                    ctx.log("${player.name} a tras din pachetul TELEX: ${card.text}")
                    val effects = effectiveEffects(ctx, playerIndex, card)
                    ctx.card = telexResult(effects).let {
                        ShownCard("TELEX", card.text, it.outcome, it.amount, destinationLine(ctx, playerIndex, card), card.id)
                    }
                    effects.forEach { applyEffect(ctx, playerIndex, it, depth = 0) }
                }
                else -> throw IllegalArgumentException("Pachet necunoscut: $kind")
            }
        } catch (e: DebtException) {
            ctx.pending = e.debt
            ctx.log("Fonduri insuficiente: ${e.debt.debtorId} datorează ${e.debt.amount} (${e.debt.reason})")
        }
        return ctx.result(state)
    }

    /** Plata explicită a chiriei. Dacă jucătorul nu are fonduri, rezultă o [Debt] (lichidare). */
    fun payRent(state: GameState, due: RentDue): TurnResult {
        val ctx = Ctx(state)
        val payer = ctx.indexOf(due.payerId)
        val owner = ctx.indexOf(due.ownerId)
        require(payer >= 0 && owner >= 0) { "Jucător necunoscut în chirie" }
        val prop = actions.getValue(due.propertyId)
        try {
            ctx.transfer(payer, owner, due.amount, "chirie ${prop.name}")
            ctx.recordCollected(due.propertyId, due.amount)
        } catch (e: DebtException) {
            ctx.pending = e.debt
            ctx.log("Fonduri insuficiente pentru chirie: datorie de ${e.debt.amount}")
        }
        return ctx.result(state)
    }

    /**
     * Refuzul cumpărării unui titlu liber: jucătorul plătește băncii taxa titlului (titleTax).
     * Proprietatea rămâne la Bancă. Dacă nu are bani, rezultă o [Debt] către Bancă.
     */
    fun declineOffer(state: GameState, offer: BuyOffer): TurnResult {
        require(offer.option is BuyOption.Title) { "Doar titlurile libere pot fi refuzate cu taxă" }
        val prop = actions.getValue(offer.propertyId)
        val ctx = Ctx(state)
        val idx = ctx.indexOf(offer.playerId)
        require(idx >= 0) { "Jucător necunoscut: ${offer.playerId}" }
        ctx.log("${ctx.players[idx].name} a refuzat cumpărarea ${prop.name}")
        try {
            ctx.playerPaysBank(idx, prop.titleTax, "taxa titlului ${prop.name}")
        } catch (e: DebtException) {
            ctx.pending = e.debt
            ctx.log("Fonduri insuficiente pentru taxa de refuz: datorie de ${e.debt.amount} către Bancă")
        }
        return ctx.result(state)
    }

    /**
     * Stinge o datorie prin vânzări la Bancă (80% din valoarea inițială).
     * Dacă după vânzări soldul nu acoperă datoria și jucătorul nu mai deține nimic, jucătorul falimentează:
     * restul de bani merge la creditor, iar jucătorul e eliminat din joc.
     */
    fun settleDebt(state: GameState, debt: Debt, sales: List<Sale>): SettleResult {
        val events = mutableListOf<String>()
        var players = state.players.toMutableList()
        var bank = state.bankMoney

        for (sale in sales) {
            val idx = players.indexOfFirst { it.id == debt.debtorId }
            require(idx >= 0) { "Jucătorul ${debt.debtorId} nu mai este în joc" }
            val player = players[idx]
            val prop = actions.getValue(sale.propertyId)
            val level = player.ownedProperties[prop.id]
                ?: throw IllegalStateException("${player.name} nu deține ${prop.name}")
            val value: Long
            val newOwned: Map<String, Int>
            if (sale.annexIndex != null) {
                require(sale.annexIndex == level) { "Anexele se vând în ordine inversă (nivel $level)" }
                value = ActionCalculator.sellBackValue(prop.annexes.first { it.index == level }.price)
                newOwned = player.ownedProperties + (prop.id to level - 1)
                events += "Jucătorul ${player.name} a lichidat o anexă la ${prop.name}."
            } else {
                // întreaga acțiune: titlul și toate anexele deținute, fiecare la 80% din valoarea inițială
                value = ActionCalculator.wholeSaleValue(prop, level)
                newOwned = player.ownedProperties - prop.id
                val what = if (level == 0) "titlul" else "întreaga acțiune (titlu + $level anexe)"
                events += "${player.name} a vândut $what ${prop.name} la Bancă (+$value)"
            }
            // a vândut din propriile anexe (sau din acțiune cu anexe): nu mai cumpără anexe până își reface rezerva
            players[idx] = player.copy(
                money = player.money + value, ownedProperties = newOwned,
                annexRecovery = player.annexRecovery || level > 0,
            )
            bank -= value
        }

        val idx = players.indexOfFirst { it.id == debt.debtorId }
        val debtor = players[idx]
        if (debtor.money >= debt.amount) {
            val creditorIdx = debt.creditorId?.let { id -> players.indexOfFirst { it.id == id } }
            players[idx] = debtor.copy(money = debtor.money - debt.amount)
            if (creditorIdx != null && creditorIdx >= 0) {
                players[creditorIdx] = players[creditorIdx].copy(money = players[creditorIdx].money + debt.amount)
            } else {
                bank += debt.amount
            }
            events += "${debtor.name} a achitat ${debt.amount} (${debt.reason})"
            return SettleResult(
                state.copy(players = players, bankMoney = bank, history = state.history + events),
                pending = null, bankrupt = false,
            )
        }

        if (debtor.ownedProperties.isNotEmpty()) {
            return SettleResult(
                state.copy(players = players, bankMoney = bank, history = state.history + events),
                pending = debt, bankrupt = false,
            )
        }

        // Faliment: nimic de vândut, soldul merge la creditor, jucătorul iese din joc
        val remaining = debtor.money
        val creditorIdx = debt.creditorId?.let { id -> players.indexOfFirst { it.id == id } }
        if (creditorIdx != null && creditorIdx >= 0) {
            players[creditorIdx] = players[creditorIdx].copy(money = players[creditorIdx].money + remaining)
        } else {
            bank += remaining
        }
        players.removeAt(idx)
        events += "FALIMENT: ${debtor.name} a fost eliminat; restul de $remaining merge la ${debt.creditorId ?: "Bancă"}"
        return SettleResult(
            state.copy(players = players, bankMoney = bank, history = state.history + events),
            pending = null, bankrupt = true,
        )
    }

    /** Aplică oferta acceptată de jucător. Aruncă excepție dacă nu e validă. */
    fun applyBuy(state: GameState, offer: BuyOffer): GameState {
        val idx = state.players.indexOfFirst { it.id == offer.playerId }
        require(idx >= 0) { "Jucător necunoscut: ${offer.playerId}" }
        val prop = actions.getValue(offer.propertyId)
        val buyer = state.players[idx]
        val players = state.players.toMutableList()
        var bank = state.bankMoney
        val event: String

        when (val opt = offer.option) {
            is BuyOption.Title -> {
                require(state.ownerOf(prop.id) == null) { "${prop.name} are deja proprietar" }
                require(buyer.money >= opt.price) { "Fonduri insuficiente pentru titlu" }
                players[idx] = buyer.copy(
                    money = buyer.money - opt.price,
                    ownedProperties = buyer.ownedProperties + (prop.id to 0),
                )
                bank += opt.price
                event = "${buyer.name} a cumpărat titlul ${prop.name} (${opt.price})"
            }
            is BuyOption.AnnexOption -> {
                require(state.ownerOf(prop.id)?.id == buyer.id) { "Doar proprietarul titlului poate cumpăra anexe" }
                val level = buyer.ownedProperties.getValue(prop.id)
                require(opt.annex.index == level + 1) { "Anexa trebuie cumpărată în ordine" }
                require(buyer.money >= opt.annex.price) { "Fonduri insuficiente pentru anexă" }
                players[idx] = buyer.copy(
                    money = buyer.money - opt.annex.price,
                    ownedProperties = buyer.ownedProperties + (prop.id to level + 1),
                )
                bank += opt.annex.price
                event = "Jucătorul ${buyer.name} a investit într-o anexă la ${prop.name}."
            }
        }
        return state.copy(
            players = players,
            bankMoney = bank,
            history = state.history + event,
        )
    }

    private fun resolveLanding(ctx: Ctx, idx: Int, depth: Int) {
        if (depth > MAX_CHAIN) return
        val player = ctx.players[idx]
        when (val tile = board[player.position]) {
            is Tile.Start -> Unit
            is Tile.Property -> landOnProperty(ctx, idx, tile.propertyId)
            is Tile.Loto -> ctx.pendingDraw = "LOTO"     // jucătorul trage manual, atingând pachetul
            is Tile.Telex -> ctx.pendingDraw = "TELEX"
            is Tile.Bank -> ctx.bankPays(idx, GameRules.BANK_INTEREST, "dobândă de la BANCA")
            is Tile.BankDividend -> ctx.bankPays(idx, GameRules.DIVIDEND, "dividende de la BANCA")
            is Tile.SalaConsfatuiri -> ctx.playerPaysBank(idx, GameRules.SALA_CONSFATUIRI_FEE, "SALA CONSFĂTUIRI")
            is Tile.CasaDeOdihna -> {
                ctx.setSkip(idx, GameRules.CASA_ODIHNA_SKIP_TURNS, "CASA DE ODIHNĂ")
                ctx.markHouseArrival(idx)
            }
            is Tile.RechematUrgent -> {
                ctx.log("${player.name} este rechemat urgent la START (fără bonus)")
                ctx.moveTo(idx, GameRules.START_POSITION)
            }
        }
    }

    private fun landOnProperty(ctx: Ctx, idx: Int, propertyId: String) {
        val prop = actions[propertyId] ?: return
        val buyer = ctx.players[idx]
        val owner = ctx.ownerOf(propertyId)
        when {
            owner == null -> ctx.offer = BuyOffer(buyer.id, propertyId, BuyOption.Title(prop.titlePrice))
            owner.id == buyer.id -> {
                val annex = ActionCalculator.nextAnnex(prop, owner.ownedProperties.getValue(propertyId))
                if (annex != null) ctx.offer = BuyOffer(buyer.id, propertyId, BuyOption.AnnexOption(annex))
            }
            else -> {
                val level = owner.ownedProperties.getValue(propertyId)
                val rent = ActionCalculator.calculateRent(prop, level)
                ctx.recordVisit(propertyId, buyer.id)
                ctx.rentDue = RentDue(buyer.id, owner.id, propertyId, rent)
            }
        }
    }

    /**
     * Scutirea casei: dacă numele companiei de pe cartonaș a fost scris cu pixul de jucător
     * ([TelexCard.handwritten]) și tocmai jucătorul care trage cartonașul deține acea companie,
     * atunci nu plătește nimic — nici băncii, nici altui jucător.
     *
     * Returnează efectele care se aplică într-adevăr: pentru un cartonaș scris cu pixul și deținut
     * de jucătorul care trage, plata către proprietar/bancă este eliminată.
     */
    private fun effectiveEffects(ctx: Ctx, idx: Int, card: TelexCard): List<CardEffect> {
        if (!card.handwritten) return card.effects
        val pay = card.effects.firstOrNull { it is CardEffect.PayOwnerOrBank } as? CardEffect.PayOwnerOrBank
            ?: return card.effects
        val owner = ctx.ownerOf(pay.propertyId) ?: return card.effects
        if (owner.id != ctx.players[idx].id) return card.effects
        val company = actions[pay.propertyId]?.name ?: pay.propertyId
        ctx.log("${ctx.players[idx].name} nu plătește nimic: $company îi aparține (scris cu pixul pe cartonaș)")
        return card.effects.filterNot { it is CardEffect.PayOwnerOrBank }
    }

    /** „Banii se achită către: …” — proprietarul companiei din cartonaș (dacă există și nu e cel care plătește), altfel banca. */
    private fun destinationLine(ctx: Ctx, idx: Int, card: TelexCard): String? {
        val pay = card.effects.firstOrNull { it is CardEffect.PayOwnerOrBank || it is CardEffect.PayBank } ?: return null
        val target = (pay as? CardEffect.PayOwnerOrBank)?.propertyId ?: return "Banii se achită către: Banca"
        val company = actions[target]?.name ?: target
        val owner = ctx.ownerOf(target)
        return when {
            owner == null -> "Banii se achită către: Banca ($company nu e privatizată)"
            owner.id == ctx.players[idx].id && card.handwritten -> "Nu plătești nimic: $company îți aparține"
            owner.id == ctx.players[idx].id -> "Banii se achită către: Banca ($company e a ta)"
            else -> "Banii se achită către: ${owner.name} (proprietar $company)"
        }
    }

    private fun applyEffect(ctx: Ctx, idx: Int, effect: CardEffect, depth: Int) {
        when (effect) {
            is CardEffect.PayBank -> ctx.playerPaysBank(idx, effect.amount, "TELEX")
            is CardEffect.PayOwnerOrBank -> {
                val owner = ctx.ownerOf(effect.propertyId)
                if (owner != null && owner.id != ctx.players[idx].id) {
                    ctx.transfer(idx, ctx.indexOf(owner.id), effect.amount, "TELEX (proprietar)")
                } else {
                    ctx.playerPaysBank(idx, effect.amount, "TELEX (bancă)")
                }
            }
            is CardEffect.ReceiveBank -> ctx.bankPays(idx, effect.amount, "TELEX")
            is CardEffect.ReceiveFromEachPlayer -> ctx.players.indices
                .filter { it != idx }
                .forEach { ctx.transfer(it, idx, effect.amount, "TELEX (contribuție)") }
            is CardEffect.Move -> {
                // Pionul rămâne pe TELEX cât se vede cartonașul; mutarea se face după confirmare, prin moveBy
                ctx.pendingMove = effect.steps
                ctx.log("${ctx.players[idx].name} va muta ${if (effect.steps > 0) "înainte" else "înapoi"} ${kotlin.math.abs(effect.steps)} căsuțe")
            }
            is CardEffect.SkipTurns -> ctx.setSkip(idx, effect.turns, "TELEX")
        }
    }

    /** Stare de lucru pentru o tură. Modificările se acumulează aici, apoi se produce un GameState nou. */
    private class Ctx(state: GameState) {
        val players: MutableList<Player> = state.players.toMutableList()
        var bank: Long = state.bankMoney
        var offer: BuyOffer? = null
        private val events = mutableListOf<String>()
        private val stats = state.stats.toMutableMap()

        /** Un jucător a aterizat pe proprietatea [propertyId] (înainte de plata chiriei). */
        fun recordVisit(propertyId: String, playerId: String) {
            val s = stats[propertyId] ?: PropertyStats()
            stats[propertyId] = s.copy(visits = s.visits + (playerId to (s.visits[playerId] ?: 0) + 1))
        }

        /** Chirie efectiv încasată de proprietar pe [propertyId]. */
        fun recordCollected(propertyId: String, amount: Long) {
            val s = stats[propertyId] ?: PropertyStats()
            stats[propertyId] = s.copy(collected = s.collected + amount)
        }

        fun log(text: String) {
            events += text
        }

        fun indexOf(playerId: String): Int = players.indexOfFirst { it.id == playerId }

        fun ownerOf(propertyId: String): Player? =
            players.firstOrNull { it.ownedProperties.containsKey(propertyId) }

        fun moveTo(idx: Int, position: Int) {
            players[idx] = players[idx].copy(position = position)
        }

        fun setSkip(idx: Int, turns: Int, reason: String) {
            players[idx] = players[idx].copy(skipTurns = turns)
            log("${players[idx].name} pierde $turns ture ($reason)")
        }

        /** Ștampilează sosirea la Casa de Odihnă: ordinea de ieșire e cea a sosirii. */
        fun markHouseArrival(idx: Int) {
            val next = (players.maxOfOrNull { it.houseSince } ?: 0) + 1
            players[idx] = players[idx].copy(houseSince = next)
        }

        fun bankPays(idx: Int, amount: Long, reason: String) {
            bank -= amount
            players[idx] = players[idx].copy(money = players[idx].money + amount)
            log("${players[idx].name} primește $amount de la bancă ($reason)")
        }

        var pending: Debt? = null
        var rentDue: RentDue? = null
        var card: ShownCard? = null
        var pendingMove: Int? = null
        var pendingDraw: String? = null

        fun playerPaysBank(idx: Int, amount: Long, reason: String) {
            if (amount == 0L) return
            if (players[idx].money < amount) {
                throw DebtException(Debt(players[idx].id, amount, null, reason))
            }
            players[idx] = players[idx].copy(money = players[idx].money - amount)
            bank += amount
            log("${players[idx].name} a achitat automat $amount către Bancă ($reason)")
        }

        fun transfer(from: Int, to: Int, amount: Long, reason: String) {
            if (from == to || amount == 0L) return
            if (players[from].money < amount) {
                throw DebtException(Debt(players[from].id, amount, players[to].id, reason))
            }
            players[from] = players[from].copy(money = players[from].money - amount)
            players[to] = players[to].copy(money = players[to].money + amount)
            log("${players[from].name} a achitat automat $amount lui ${players[to].name} ($reason)")
        }

        fun result(original: GameState): TurnResult {
            val state = original.copy(
                players = players.toList(),
                bankMoney = bank,
                history = original.history + events,
                stats = stats.toMap(),
            )
            return TurnResult(state, offer, events.toList(), pending, rentDue, card, pendingMove, pendingDraw)
        }
    }

    private companion object {
        /** Limită pentru lanțuri de efecte (cartonașe care mută pionul etc.). */
        const val MAX_CHAIN = 4
    }
}

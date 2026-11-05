package com.example.privatizare.logic

import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.Tile
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Inteligența robotului (port din web/js/core/botAI.js). Funcții pure: primesc starea, returnează decizia.
 * Mecanica reală: fără grupuri/monopoluri. Aplicația are un singur zar: pasul = valoarea zarului (1..6).
 */
object BotAI {
    const val BUY_THRESHOLD = 50
    const val MIN_SAFETY_BUFFER = 300L
    const val TRAFFIC_BONUS = 15.0
    private const val HORIZON_TURNS = 10.0
    private const val BASE_SCORE = 20.0
    private const val ROI_WEIGHT = 40.0
    private const val ANNEX_MIN_AVG_RENTS = 2.0
    private const val CASH_PRESSURE_PENALTY = 15
    private const val EXPENSIVE_ANNEX_SHARE = 0.4
    private const val PRIOR_WEIGHT_TURNS = 10.0
    private const val DICE_COUNT = 1
    /** La anexă rămâne cel puțin această fracțiune din banii de acum (la fel ca web/js/core/botAI.js). */
    const val ANNEX_KEEP = 0.25

    /** Probabilitatea sumei a două zaruri (2..12). */
    fun diceSumProb(sum: Int): Double =
        if (sum < 2 || sum > 12) 0.0 else (6 - abs(7 - sum)) / 36.0

    /** Distribuția pasului: pas -> probabilitate (un zar: 1..6). */
    fun stepDistribution(diceCount: Int = DICE_COUNT): Map<Int, Double> =
        if (diceCount == 2) (2..12).associateWith { diceSumProb(it) } else (1..6).associateWith { 1.0 / 6 }

    /** Pasul mediu: 3.5 pentru un zar, 7 pentru două. */
    fun expectedSteps(diceCount: Int = DICE_COUNT): Double = if (diceCount == 2) 7.0 else 3.5

    /** Chiria pe care o plătește cine intră pe căsuța [index] (0 dacă nu e plătibilă). */
    private fun rentOnTile(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>, index: Int,
    ): Long {
        val tile = board[index]
        if (tile !is Tile.Property) return 0L
        val owner = state.players.firstOrNull { it.id != botId && it.ownedProperties.containsKey(tile.propertyId) }
            ?: return 0L
        return ActionCalculator.calculateRent(actions.getValue(tile.propertyId), owner.ownedProperties.getValue(tile.propertyId))
    }

    /** Cea mai mare chirie pe care botul o poate plăti în raza aruncării. */
    fun maxPotentialRent(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>,
        diceCount: Int = DICE_COUNT,
    ): Long {
        val me = state.players.first { it.id == botId }
        return stepDistribution(diceCount).keys.maxOf { step ->
            rentOnTile(state, botId, board, actions, (me.position + step) % board.size)
        }
    }

    /** Rezerva minimă: max(300, cea mai mare chirie potențială * 1.25). */
    fun safetyBuffer(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>,
        diceCount: Int = DICE_COUNT,
    ): Long = maxOf(MIN_SAFETY_BUFFER, Math.round(maxPotentialRent(state, botId, board, actions, diceCount) * 1.25))

    /** Media chiriilor de bază (doar titlu). */
    fun averageBaseRent(actions: Map<String, PropertyAction>): Double =
        actions.values.map { ActionCalculator.calculateRent(it, 0).toDouble() }.average()

    /** Rata de vizită pe tură a adversarilor pe [propertyId]: istoric (state.stats) combinat cu prior-ul din zaruri. */
    fun visitRate(
        state: GameState, ownerId: String, boardSize: Int, propertyId: String, diceCount: Int = DICE_COUNT,
    ): Double {
        val opponents = state.players.count { it.id != ownerId }
        val prior = opponents * (expectedSteps(diceCount) / boardSize)
        val observed = state.stats[propertyId]?.visits?.filterKeys { it != ownerId }?.values?.sum() ?: 0
        return (observed + prior * PRIOR_WEIGHT_TURNS) / (state.turn + PRIOR_WEIGHT_TURNS)
    }

    /** Cât a încasat deja proprietarul de pe [propertyId]. */
    fun collectedSoFar(state: GameState, propertyId: String): Long = state.stats[propertyId]?.collected ?: 0L

    /** Expunerea: chiria așteptată la următoarea aruncare. */
    private fun exposure(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>, diceCount: Int,
    ): Double {
        val me = state.players.first { it.id == botId }
        return stepDistribution(diceCount).entries.sumOf { (step, prob) ->
            prob * rentOnTile(state, botId, board, actions, (me.position + step) % board.size)
        }
    }

    /** Scorul (0-100) al unei cumpărări, cu motivele. hardReject = refuz fără discuție. */
    data class Score(val score: Int, val hardReject: Boolean, val reasons: List<String>)

    fun utilityScore(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>,
        propertyId: String, option: BuyOption, diceCount: Int = DICE_COUNT,
    ): Score {
        val me = state.players.first { it.id == botId }
        val prop = actions.getValue(propertyId)
        val price = when (option) {
            is BuyOption.Title -> prop.titlePrice
            is BuyOption.AnnexOption -> option.annex.price
        }
        fun reject(why: String) = Score(0, true, listOf(why))
        if (me.money < price) return reject("fără bani")
        val after = me.money - price
        // Rezerva și pragul se aplică doar anexelor; titlul se încearcă dacă are bani.
        if (option is BuyOption.AnnexOption && after < safetyBuffer(state, botId, board, actions, diceCount)) {
            return reject("sub rezerva de siguranță")
        }
        if (option is BuyOption.AnnexOption && after < ANNEX_MIN_AVG_RENTS * averageBaseRent(actions)) {
            return reject("anexă sub 2 chirii medii în cont")
        }

        val reasons = mutableListOf<String>()
        val gainPerVisit = when (option) {
            is BuyOption.Title -> prop.titleTax
            is BuyOption.AnnexOption -> option.annex.profit
        }
        val rate = visitRate(state, botId, board.size, propertyId, diceCount)
        val roi = gainPerVisit * rate * HORIZON_TURNS / price
        var score = BASE_SCORE + ROI_WEIGHT * min(1.0, roi)
        reasons += "randament %.2f".format(roi)

        if (collectedSoFar(state, propertyId) >= price) {
            score += 10
            reasons += "a încasat deja cât costă"
        }

        val tileIndex = board.indexOfFirst { it is Tile.Property && it.propertyId == propertyId }
        val distance = (tileIndex - me.position + board.size) % board.size
        val dist = stepDistribution(diceCount)
        val reach = dist[distance] ?: 0.0
        if (reach > 0) {
            score += Math.round(TRAFFIC_BONUS * (reach / dist.values.maxOf { it })).toInt()
            reasons += "trafic ${(reach * 100).roundToInt()}%"
        }

        if (after < 2 * exposure(state, botId, board, actions, diceCount)) {
            score -= CASH_PRESSURE_PENALTY
            reasons += "lichiditate sub presiune"
        }

        if (option is BuyOption.AnnexOption && price > EXPENSIVE_ANNEX_SHARE * me.money) {
            val penalty = min(30, ((price.toDouble() / me.money - EXPENSIVE_ANNEX_SHARE) * 60).roundToInt())
            score -= penalty
            reasons += "anexă scumpă (-$penalty)"
        }

        return Score(score.roundToInt().coerceIn(0, 100), false, reasons)
    }

    /** Titlul liber: îl încearcă dacă are bani. Anexa: o cumpără dacă o poate plăti și rămâne [ANNEX_KEEP] din bani. */
    fun decideBuy(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>,
        propertyId: String, option: BuyOption, diceCount: Int = DICE_COUNT,
    ): Boolean {
        val me = state.players.first { it.id == botId }
        return when (option) {
            is BuyOption.Title -> me.money >= option.price
            is BuyOption.AnnexOption -> annexAffordable(me.money, option.annex.price)
        }
    }

    /** Anexa se cumpără cât timp, după plată, rămâne cel puțin [ANNEX_KEEP] din banii de acum. */
    fun annexAffordable(money: Long, price: Long): Boolean =
        money >= price && (money - price).toDouble() >= ANNEX_KEEP * money

    /**
     * Vânzările pentru o datorie: întreaga acțiune (titlu + anexe) odată, cea mai slabă întâi.
     * „Slabă” = venitul așteptat pe tură / valoarea de vânzare. Se oprește când datoria e acoperită.
     */
    fun liquidationSales(
        debtorId: String, debt: Long, state: GameState,
        board: List<Tile>, actions: Map<String, PropertyAction>, diceCount: Int = DICE_COUNT,
    ): List<Sale> {
        val debtor = state.players.first { it.id == debtorId }
        if (debtor.money >= debt) return emptyList()
        val ranked = debtor.ownedProperties.map { (propId, level) ->
            val prop = actions.getValue(propId)
            val income = ActionCalculator.calculateRent(prop, level) *
                visitRate(state, debtorId, board.size, propId, diceCount)
            val value = ActionCalculator.wholeSaleValue(prop, level)
            Triple(propId, value, income / value)
        }.sortedBy { it.third }

        var money = debtor.money
        val sales = mutableListOf<Sale>()
        for ((propId, value, _) in ranked) {
            if (money >= debt) break
            sales += Sale(propId, null)
            money += value
        }
        return sales
    }

    /* ---------- CatalinRobot: analiză pe orizont (port din web/js/core/catalinAI.js) ---------- */

    const val CATALIN_HORIZON = 10
    /** Șansa ca robotul să-și aleagă fața zarului (restul: aruncare cu noroc). */
    const val CATALIN_CONTROL = 0.6

    /** Distribuția pasului robotului: fiecare zar cu noroc, suma pe [diceCount] zaruri. */
    fun botStepDistribution(diceCount: Int = DICE_COUNT, luck: Double = CATALIN_LUCK): Map<Int, Double> {
        val die = (1..6).associateWith { k -> (1 - luck) / 6 + luck * (2 * k - 1) / 36.0 }
        var dist: Map<Int, Double> = mapOf(0 to 1.0)
        repeat(diceCount) {
            val next = HashMap<Int, Double>()
            for ((s, p) in dist) for ((k, q) in die) next[s + k] = (next[s + k] ?: 0.0) + p * q
            dist = next
        }
        return dist
    }

    /** Chiria așteptată pe care o plătește robotul în următoarele [horizon] ture, din lanțul de poziții. */
    fun expectedRentPaid(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>,
        diceCount: Int = DICE_COUNT, horizon: Int = CATALIN_HORIZON,
    ): Double {
        val me = state.players.first { it.id == botId }
        if (me.position !in board.indices) return 0.0
        val dist = botStepDistribution(diceCount)
        var pos = DoubleArray(board.size).also { it[me.position] = 1.0 }
        var paid = 0.0
        repeat(horizon) {
            val next = DoubleArray(board.size)
            for (i in board.indices) if (pos[i] != 0.0) for ((s, p) in dist) next[(i + s) % board.size] += pos[i] * p
            for (i in board.indices) paid += next[i] * rentOnTile(state, botId, board, actions, i)
            pos = next
        }
        return paid
    }

    /** Ture analizate exact (toate căile zarului) pentru riscul de lichiditate la anexe. */
    const val OUTLOOK_TURNS = 3

    /**
     * Toate căile zarului pe [horizon] ture: distribuția chiriilor totale plătite (total -> probabilitate)
     * și cheltuiala așteptată pe titlurile libere pe care ajunge robotul.
     */
    fun pathOutlook(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>,
        diceCount: Int = DICE_COUNT, horizon: Int = OUTLOOK_TURNS,
    ): Pair<Map<Long, Double>, Double> {
        val me = state.players.first { it.id == botId }
        if (me.position !in board.indices) return emptyMap<Long, Double>() to 0.0
        val dist = botStepDistribution(diceCount)
        var states = HashMap<Pair<Int, Long>, Double>().apply { put(me.position to 0L, 1.0) }
        var titleSpend = 0.0
        repeat(horizon) {
            val next = HashMap<Pair<Int, Long>, Double>()
            for ((key, p) in states) {
                val (pos, rent) = key
                for ((s, q) in dist) {
                    val idx = (pos + s) % board.size
                    val tile = board[idx]
                    val pp = p * q
                    if (tile is Tile.Property && me.ownedProperties[tile.propertyId] == null &&
                        state.players.none { it.id != botId && it.ownedProperties.containsKey(tile.propertyId) }
                    ) {
                        titleSpend += pp * actions.getValue(tile.propertyId).titlePrice
                    }
                    val nk = idx to rent + rentOnTile(state, botId, board, actions, idx)
                    next[nk] = (next[nk] ?: 0.0) + pp
                }
            }
            states = next
        }
        val rentDist = HashMap<Long, Double>()
        for ((key, p) in states) rentDist[key.second] = (rentDist[key.second] ?: 0.0) + p
        return rentDist to titleSpend
    }

    /** Valoarea sub care rămâne cel puțin [q] din probabilitate (ex. 0.9: rareori se plătește mai mult). */
    fun rentQuantile(rentDist: Map<Long, Double>, q: Double): Long {
        var acc = 0.0
        for ((r, p) in rentDist.toSortedMap()) {
            acc += p
            if (acc >= q) return r
        }
        return rentDist.keys.maxOrNull() ?: 0L
    }

    /** Câștigul net pe orizont: chiriile adversarilor în plus minus pierderea la revânzare (20%), și prețul. */
    fun horizonGain(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>,
        propertyId: String, option: BuyOption, diceCount: Int = DICE_COUNT, horizon: Int = CATALIN_HORIZON,
    ): Pair<Double, Long> {
        val me = state.players.first { it.id == botId }
        val prop = actions.getValue(propertyId)
        val oldLevel = me.ownedProperties[propertyId] ?: -1
        val newLevel = when (option) { is BuyOption.Title -> 0; is BuyOption.AnnexOption -> option.annex.index }
        val price = when (option) { is BuyOption.Title -> prop.titlePrice; is BuyOption.AnnexOption -> option.annex.price }
        val oldRent = if (oldLevel < 0) 0L else ActionCalculator.calculateRent(prop, oldLevel)
        val newRent = ActionCalculator.calculateRent(prop, newLevel)
        val oldSale = if (oldLevel < 0) 0L else ActionCalculator.wholeSaleValue(prop, oldLevel)
        val newSale = ActionCalculator.wholeSaleValue(prop, newLevel)
        val rate = visitRate(state, botId, board.size, propertyId, diceCount)
        val extraRent = (newRent - oldRent) * rate * horizon
        val lostValue = price - (newSale - oldSale)
        return (extraRent - lostValue) to price
    }

    /**
     * Decizia CatalinRobot. Titlul: cumpără dacă are banii. Anexa: cumpără orice pe care o poate
     * plăti, cât timp rămâne [ANNEX_KEEP] din banii de acum (vezi [annexAffordable]).
     */
    fun decideCatalin(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>,
        propertyId: String, option: BuyOption, diceCount: Int = DICE_COUNT,
    ): Boolean {
        val me = state.players.first { it.id == botId }
        return when (option) {
            is BuyOption.Title -> me.money >= actions.getValue(propertyId).titlePrice
            is BuyOption.AnnexOption -> annexAffordable(me.money, option.annex.price)
        }
    }

    /**
     * Tot ce cântărește robotul când alege o căsuță: un titlu liber pe care și-l poate permite e bun,
     * o chirie plătită unui adversar e rău, iar o căsuță a lui sau fără efect e neutră.
     */
    fun tileScore(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>, index: Int,
    ): Double {
        val tile = board[index]
        if (tile !is Tile.Property) return 0.0
        val me = state.players.first { it.id == botId }
        val rent = rentOnTile(state, botId, board, actions, index)
        val ownedByOpponent = state.players.any { it.id != botId && it.ownedProperties.containsKey(tile.propertyId) }
        if (ownedByOpponent) return -rent / 500.0
        if (me.ownedProperties.containsKey(tile.propertyId)) return 0.5
        return if (me.money >= actions.getValue(tile.propertyId).titlePrice) 3.0 else 0.5
    }

    /**
     * Zarul CatalinRobot: cu CATALIN_CONTROL își alege fața (sau suma celor două zaruri) care duce pe
     * cea mai bună căsuță; altfel aruncă cu noroc, ca un zar obișnuit.
     */
    fun catalinDice(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>,
        diceCount: Int = DICE_COUNT, random: Random = Random.Default,
    ): List<Int> {
        if (random.nextDouble() >= CATALIN_CONTROL) return List(diceCount) { rollBotDie(random, CATALIN_LUCK) }
        val me = state.players.first { it.id == botId }
        val score = { step: Int -> tileScore(state, botId, board, actions, (me.position + step) % board.size) }
        if (diceCount == 1) {
            // la egalitate: fața mai mare
            return listOf((2..6).fold(1) { best, f -> if (score(f) >= score(best)) f else best })
        }
        // la egalitate: suma mai mare
        val bestSum = (3..12).fold(2) { best, s -> if (score(s) >= score(best)) s else best }
        val lo = maxOf(1, bestSum - 6)
        val hi = minOf(6, bestSum - 1)
        val a = lo + random.nextInt(hi - lo + 1)
        return listOf(a, bestSum - a)
    }
}

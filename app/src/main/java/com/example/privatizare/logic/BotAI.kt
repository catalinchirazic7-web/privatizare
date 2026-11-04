package com.example.privatizare.logic

import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.Tile
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

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

    /** Titlul liber: îl încearcă dacă are bani. Anexa: doar dacă scorul depășește pragul și nu e refuz ferm. */
    fun decideBuy(
        state: GameState, botId: String, board: List<Tile>, actions: Map<String, PropertyAction>,
        propertyId: String, option: BuyOption, diceCount: Int = DICE_COUNT,
    ): Boolean {
        if (option is BuyOption.Title) return state.players.first { it.id == botId }.money >= option.price
        val r = utilityScore(state, botId, board, actions, propertyId, option, diceCount)
        return !r.hardReject && r.score >= BUY_THRESHOLD
    }

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
}

package com.example.privatizare.multiplayer

import com.example.privatizare.data.model.GameState
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyStats
import org.json.JSONArray
import org.json.JSONObject

data class OfferSnap(val playerId: String, val propertyId: String, val annexIndex: Int?)
data class RentSnap(val payerId: String, val ownerId: String, val propertyId: String, val amount: Long)
data class DebtSnap(val debtorId: String, val amount: Long, val creditorId: String?, val reason: String)
data class CardSnap(
    val kind: String,
    val text: String,
    val outcome: String = "NEUTRAL",
    val amount: Long? = null,
    val destination: String? = null,
    /** Id-ul cartonașului (pentru găsirea imaginii lui). */
    val cardId: String? = null,
)

/** Tot ce are nevoie un oaspete ca să afișeze jocul: starea oficială a gazdei. */
data class GameSnapshot(
    val state: GameState,
    val currentIndex: Int,
    val lastDice: List<Int>,
    val winnerName: String?,
    val turnAwaitingEnd: Boolean,
    val skipBanner: String?,
    val offer: OfferSnap?,
    val feeOffer: OfferSnap?,
    val rentDue: RentSnap?,
    val debt: DebtSnap?,
    val card: CardSnap?,
    /** Pachetul din care jucătorul la rând trebuie să tragă, dacă există. */
    val pendingDraw: String? = null,
)

/** Serializare JSON a [GameSnapshot]. Istoricul se limitează la ultimele [MAX_HISTORY] mesaje. */
object SnapshotCodec {
    const val MAX_HISTORY = 20

    fun encode(s: GameSnapshot): String = JSONObject().apply {
        put("turn", s.state.turn)
        put("bank", s.state.bankMoney)
        put("history", JSONArray(s.state.history.takeLast(MAX_HISTORY)))
        put("players", JSONArray().apply { s.state.players.forEach { put(player(it)) } })
        put("cur", s.currentIndex)
        put("dice", JSONArray(s.lastDice))
        put("winner", s.winnerName ?: JSONObject.NULL)
        put("await", s.turnAwaitingEnd)
        put("skip", s.skipBanner ?: JSONObject.NULL)
        put("offer", s.offer?.let { offer(it) } ?: JSONObject.NULL)
        put("fee", s.feeOffer?.let { offer(it) } ?: JSONObject.NULL)
        put("rent", s.rentDue?.let {
            JSONObject().put("pa", it.payerId).put("ow", it.ownerId).put("pr", it.propertyId).put("am", it.amount)
        } ?: JSONObject.NULL)
        put("debt", s.debt?.let {
            JSONObject().put("de", it.debtorId).put("am", it.amount).put("cr", it.creditorId ?: JSONObject.NULL).put("re", it.reason)
        } ?: JSONObject.NULL)
        put("draw", s.pendingDraw ?: JSONObject.NULL)
        put("st", JSONObject().apply {
            s.state.stats.forEach { (pid, st) ->
                put(pid, JSONObject().put("v", JSONObject().apply { st.visits.forEach { (k, v) -> put(k, v) } }).put("c", st.collected))
            }
        })
        put("card", s.card?.let {
            JSONObject().put("k", it.kind).put("t", it.text).put("o", it.outcome).put("a", it.amount ?: JSONObject.NULL)
                .put("d", it.destination ?: JSONObject.NULL).put("i", it.cardId ?: JSONObject.NULL)
        } ?: JSONObject.NULL)
    }.toString()

    fun decode(json: String): GameSnapshot {
        val o = JSONObject(json)
        val players = o.getJSONArray("players").let { a -> (0 until a.length()).map { player(a.getJSONObject(it)) } }
        val history = o.getJSONArray("history").let { a -> (0 until a.length()).map { a.getString(it) } }
        val dice = o.getJSONArray("dice").let { a -> (0 until a.length()).map { a.getInt(it) } }
        return GameSnapshot(
            state = GameState(
                turn = o.getInt("turn"), players = players, bankMoney = o.getLong("bank"), history = history,
                stats = o.optJSONObject("st")?.let { st ->
                    st.keys().asSequence().associateWith { pid ->
                        val e = st.getJSONObject(pid)
                        val v = e.getJSONObject("v")
                        PropertyStats(v.keys().asSequence().associateWith { v.getInt(it) }, e.getLong("c"))
                    }
                } ?: emptyMap(),
            ),
            currentIndex = o.getInt("cur"),
            lastDice = dice,
            winnerName = o.optString("winner").takeIf { !o.isNull("winner") },
            turnAwaitingEnd = o.getBoolean("await"),
            skipBanner = o.optString("skip").takeIf { !o.isNull("skip") },
            offer = o.optJSONObject("offer")?.let { offer(it) },
            feeOffer = o.optJSONObject("fee")?.let { offer(it) },
            rentDue = o.optJSONObject("rent")?.let {
                RentSnap(it.getString("pa"), it.getString("ow"), it.getString("pr"), it.getLong("am"))
            },
            debt = o.optJSONObject("debt")?.let {
                DebtSnap(it.getString("de"), it.getLong("am"), it.optString("cr").takeIf { _ -> !it.isNull("cr") }, it.getString("re"))
            },
            pendingDraw = o.optString("draw").takeIf { !o.isNull("draw") && it.isNotEmpty() },
            card = o.optJSONObject("card")?.let {
                CardSnap(
                    it.getString("k"), it.getString("t"), it.optString("o", "NEUTRAL"),
                    if (it.isNull("a")) null else it.getLong("a"),
                    if (it.isNull("d")) null else it.optString("d").takeIf { d -> d.isNotEmpty() },
                    if (it.isNull("i")) null else it.optString("i").takeIf { i -> i.isNotEmpty() },
                )
            },
        )
    }

    private fun player(p: Player) = JSONObject().apply {
        put("id", p.id); put("n", p.name); put("c", p.color.name); put("m", p.money); put("pos", p.position)
        put("sk", p.skipTurns); put("bot", p.isBot); put("hs", p.houseSince)
        put("own", JSONObject().apply { p.ownedProperties.forEach { (k, v) -> put(k, v) } })
    }

    private fun player(o: JSONObject): Player {
        val own = o.getJSONObject("own")
        return Player(
            id = o.getString("id"), name = o.getString("n"), color = PawnColor.valueOf(o.getString("c")),
            money = o.getLong("m"), position = o.getInt("pos"),
            ownedProperties = own.keys().asSequence().associateWith { own.getInt(it) },
            skipTurns = o.getInt("sk"), isBot = o.getBoolean("bot"), houseSince = o.optInt("hs", 0),
        )
    }

    private fun offer(o: OfferSnap) = JSONObject().put("pl", o.playerId).put("pr", o.propertyId).put("ax", o.annexIndex ?: JSONObject.NULL)

    private fun offer(o: JSONObject) =
        OfferSnap(o.getString("pl"), o.getString("pr"), if (o.isNull("ax")) null else o.getInt("ax"))
}

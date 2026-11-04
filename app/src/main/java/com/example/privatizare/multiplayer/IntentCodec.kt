package com.example.privatizare.multiplayer

import org.json.JSONObject

/** Serializare JSON a intențiilor trimise de oaspeți către gazdă (`rooms/{cod}/intents`). */
object IntentCodec {
    fun encode(e: GameEvent): String = JSONObject().apply {
        put("p", e.playerId)
        when (e) {
            is GameEvent.RollDiceEvent -> { put("t", "roll"); put("v", e.value) }
            is GameEvent.BuyPropertyEvent -> { put("t", "buy"); put("tile", e.tileIndex) }
            is GameEvent.DeclinePropertyEvent -> { put("t", "decline"); put("tile", e.tileIndex) }
            is GameEvent.BuyAnnexEvent -> { put("t", "annex"); put("tile", e.tileIndex) }
            is GameEvent.EndTurnEvent -> put("t", "end")
            is GameEvent.PayRentEvent -> put("t", "rent")
            is GameEvent.AcknowledgeEvent -> put("t", "ack")
            is GameEvent.SellEvent -> {
                put("t", "sell"); put("prop", e.propertyId); put("ax", e.annexIndex ?: JSONObject.NULL)
            }
            is GameEvent.BankruptEvent -> put("t", "bankrupt")
            is GameEvent.DrawCardEvent -> put("t", "draw")
        }
    }.toString()

    /** Returnează null pentru o intenție necunoscută sau stricată (gazda o ignoră). */
    fun decode(json: String): GameEvent? = try {
        val o = JSONObject(json)
        val p = o.getString("p")
        when (o.getString("t")) {
            "roll" -> GameEvent.RollDiceEvent(p, o.optInt("v", 1))
            "buy" -> GameEvent.BuyPropertyEvent(p, o.getInt("tile"))
            "decline" -> GameEvent.DeclinePropertyEvent(p, o.getInt("tile"))
            "annex" -> GameEvent.BuyAnnexEvent(p, o.getInt("tile"))
            "end" -> GameEvent.EndTurnEvent(p)
            "rent" -> GameEvent.PayRentEvent(p)
            "ack" -> GameEvent.AcknowledgeEvent(p)
            "sell" -> GameEvent.SellEvent(p, o.getString("prop"), if (o.isNull("ax")) null else o.getInt("ax"))
            "bankrupt" -> GameEvent.BankruptEvent(p)
            "draw" -> GameEvent.DrawCardEvent(p)
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

package com.example.privatizare.logic

import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction

/**
 * Scor pentru finalul cu timp limită: banii jucătorului plus valoarea acțiunilor pe care le deține,
 * calculată la prețul de răscumpărare al Băncii (80%), adică suma pe care ar primi-o la vânzare.
 */
object Scoring {

    /** Valoarea întregii acțiuni (titlu plus anexele deținute), la 80%. */
    fun holdingsValue(player: Player, actions: Map<String, PropertyAction>): Long =
        player.ownedProperties.entries.sumOf { (propId, level) ->
            val prop = actions[propId] ?: return@sumOf 0L
            ActionCalculator.wholeSaleValue(prop, level)
        }

    fun netWorth(player: Player, actions: Map<String, PropertyAction>): Long =
        player.money + holdingsValue(player, actions)

    /** Jucătorii sortați descrescător după scor; primul este câștigătorul. */
    fun ranking(players: List<Player>, actions: Map<String, PropertyAction>): List<Player> =
        players.sortedByDescending { netWorth(it, actions) }
}

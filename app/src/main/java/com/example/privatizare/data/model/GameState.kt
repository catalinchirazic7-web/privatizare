package com.example.privatizare.data.model

data class GameState(
    val turn: Int = 1,
    val players: List<Player>,
    val bankMoney: Long,
    val history: List<String> = emptyList(),
    /** Statistica pe proprietate (id -> [PropertyStats]): vizitele adversarilor și chiriile încasate. */
    val stats: Map<String, PropertyStats> = emptyMap(),
) {
    /** Jucătorul care deține titlul obiectivului, sau null dacă e la Bancă. */
    fun ownerOf(propertyId: String): Player? =
        players.firstOrNull { it.ownedProperties.containsKey(propertyId) }
}

/** Câte aterizări au avut adversarii pe o proprietate (per jucător) și cât s-a încasat de pe ea. */
data class PropertyStats(
    val visits: Map<String, Int> = emptyMap(),
    val collected: Long = 0,
)

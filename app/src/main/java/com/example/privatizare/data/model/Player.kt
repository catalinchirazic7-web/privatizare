package com.example.privatizare.data.model

/**
 * Jucător. [ownedProperties] mapează id-ul proprietății la numărul de anexe cumpărate (nivelul).
 * Prezența cheii înseamnă că jucătorul deține titlul.
 * [skipTurns] = numărul de ture de pierdut (ex. CASA DE ODIHNĂ).
 */
data class Player(
    val id: String,
    val name: String,
    val color: PawnColor,
    val money: Long,
    val position: Int = 0,
    val ownedProperties: Map<String, Int> = emptyMap(),
    val skipTurns: Int = 0,
    /** Ordinea sosirii la CASA DE ODIHNĂ (mai mic = a venit mai devreme); la ieșire are prioritate cine a venit primul. */
    val houseSince: Int = 0,
    /** Robot: deciziile sunt luate automat de [com.example.privatizare.logic.BotAI]. */
    val isBot: Boolean = false,
    /** CatalinRobot (doar când [isBot]): decizii pe orizont și zar cu noroc, ca în web. */
    val catalin: Boolean = false,
    /** A vândut anexe pentru datorie: nu mai cumpără anexe până își reface rezerva de 15% din averea totală. */
    val annexRecovery: Boolean = false,
)

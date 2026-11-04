package com.example.privatizare.multiplayer

/**
 * Acțiunile jucătorului care se transmit prin rețea. Fiecare conține id-ul jucătorului care le-a făcut,
 * ca gazda să poată verifica rândul. Zarul e trimis ca valoare, nu ca rezultat al unei alte operații.
 */
sealed class GameEvent {
    abstract val playerId: String

    data class RollDiceEvent(override val playerId: String, val value: Int) : GameEvent()
    data class BuyPropertyEvent(override val playerId: String, val tileIndex: Int) : GameEvent()
    data class DeclinePropertyEvent(override val playerId: String, val tileIndex: Int) : GameEvent()
    data class BuyAnnexEvent(override val playerId: String, val tileIndex: Int) : GameEvent()
    data class EndTurnEvent(override val playerId: String) : GameEvent()

    /** Plata chiriei după aterizarea pe proprietatea altui jucător. */
    data class PayRentEvent(override val playerId: String) : GameEvent()

    /** "OK" la taxa de refuz sau "Am înțeles" la cartonaș: gazda decide după dialogul activ. */
    data class AcknowledgeEvent(override val playerId: String) : GameEvent()

    /** Vânzare la Bancă în timpul lichidării. [annexIndex] null = titlul. */
    data class SellEvent(override val playerId: String, val propertyId: String, val annexIndex: Int?) : GameEvent()

    /** Tragerea manuală a unui cartonaș din pachetul pe care a ajuns jucătorul. */
    data class DrawCardEvent(override val playerId: String) : GameEvent()

    /** Declararea falimentului când nu mai există nimic de vândut. */
    data class BankruptEvent(override val playerId: String) : GameEvent()
}

/**
 * Canal între ecranul local și ceilalți jucători. Implementări: [LocalPassAndPlayBridge] (același ecran)
 * și [FirebaseLobbyBridge] (fiecare pe telefonul lui, prin cod de cameră).
 */
interface GameRoomSession {
    /** Trimite un eveniment către ceilalți. */
    fun send(event: GameEvent)

    /** Înregistrează primitorul pentru evenimentele venite de la ceilalți. */
    fun setListener(listener: (GameEvent) -> Unit)
}

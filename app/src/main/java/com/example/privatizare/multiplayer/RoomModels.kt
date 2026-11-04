package com.example.privatizare.multiplayer

import com.example.privatizare.data.model.PawnColor

enum class RoomStage { LOBBY, PLAYING, FINISHED }

enum class RoomRole { HOST, GUEST, BOT }

/** Un loc la masă: id-ul coincide cu id-ul jucătorului din joc ("p0".."p3"). */
data class RoomPlayer(val id: String, val name: String, val color: PawnColor, val role: RoomRole)

/** Starea camerei din `rooms/{cod}/info`. */
data class RoomInfo(val stage: RoomStage, val hostId: String, val players: List<RoomPlayer>) {
    val isFull: Boolean get() = players.size >= MAX_PLAYERS
    val canStart: Boolean get() = players.size >= MIN_PLAYERS && players.any { it.role != RoomRole.BOT }

    companion object {
        const val MAX_PLAYERS = 4
        const val MIN_PLAYERS = 2
    }
}

class RoomException(message: String) : Exception(message)

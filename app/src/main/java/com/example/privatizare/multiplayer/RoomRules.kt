package com.example.privatizare.multiplayer

import com.example.privatizare.data.model.PawnColor

/** Reguli pure ale camerei, folosite de ambele implementări de backend (Firebase și în memorie). */
object RoomRules {

    /** Id-urile locurilor coincid cu id-urile jucătorilor din joc. */
    private val SEATS = listOf("p0", "p1", "p2", "p3")

    fun freeSeat(players: List<RoomPlayer>): String? =
        SEATS.firstOrNull { id -> players.none { it.id == id } }

    fun freeColor(players: List<RoomPlayer>): PawnColor? =
        PawnColor.entries.firstOrNull { c -> players.none { it.color == c } }

    /** Motivul pentru care cineva nu poate intra, sau null dacă poate. */
    fun joinError(info: RoomInfo?, color: PawnColor): String? = when {
        info == null -> "Camera nu există."
        info.stage != RoomStage.LOBBY -> "Meciul a început deja."
        info.isFull -> "Masa este plină."
        info.players.any { it.color == color } -> "Pionul este deja luat de alt jucător."
        else -> null
    }

    fun toMap(info: RoomInfo): Map<String, Any?> = mapOf(
        "stage" to info.stage.name,
        "hostId" to info.hostId,
        "players" to info.players.associate { it.id to playerMap(it) },
    )

    fun playerMap(p: RoomPlayer): Map<String, Any?> =
        mapOf("name" to p.name, "color" to p.color.name, "role" to p.role.name)

    /** Citește `info` din structura de hărți returnată de baza de date. Null dacă lipsește sau e stricată. */
    fun fromMap(raw: Any?): RoomInfo? {
        val m = raw as? Map<*, *> ?: return null
        val stage = (m["stage"] as? String)?.let { runCatching { RoomStage.valueOf(it) }.getOrNull() } ?: return null
        val hostId = m["hostId"] as? String ?: return null
        val players = (m["players"] as? Map<*, *>)?.mapNotNull { (id, v) ->
            val pm = v as? Map<*, *> ?: return@mapNotNull null
            RoomPlayer(
                id = id as? String ?: return@mapNotNull null,
                name = pm["name"] as? String ?: return@mapNotNull null,
                color = (pm["color"] as? String)?.let { runCatching { PawnColor.valueOf(it) }.getOrNull() }
                    ?: return@mapNotNull null,
                role = (pm["role"] as? String)?.let { runCatching { RoomRole.valueOf(it) }.getOrNull() }
                    ?: return@mapNotNull null,
            )
        }?.sortedBy { it.id } ?: emptyList()
        return RoomInfo(stage, hostId, players)
    }
}

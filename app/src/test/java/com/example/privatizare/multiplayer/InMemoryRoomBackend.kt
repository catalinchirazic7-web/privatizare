package com.example.privatizare.multiplayer

import com.example.privatizare.data.model.PawnColor
import java.io.Closeable

/** Backend fără rețea, sincron, cu aceleași reguli ca Firebase ([RoomRules]). Pentru teste. */
class InMemoryRoomBackend : RoomBackend {

    private class Room(var info: RoomInfo) {
        var state: String? = null
        val infoListeners = mutableListOf<(RoomInfo?) -> Unit>()
        val stateListeners = mutableListOf<(String) -> Unit>()
        val intentListeners = mutableListOf<(String) -> Unit>()
        val pendingIntents = ArrayDeque<String>()
    }

    private val rooms = mutableMapOf<String, Room>()

    /** Pentru teste: intenții ajunse și ne-livrate încă. */
    val sentIntents = mutableListOf<String>()

    override fun createRoom(code: String, hostName: String, hostColor: PawnColor, onDone: (Result<RoomPlayer>) -> Unit) {
        if (rooms.containsKey(code)) {
            onDone(Result.failure(RoomException("Codul $code este deja folosit.")))
            return
        }
        val host = RoomPlayer("p0", hostName, hostColor, RoomRole.HOST)
        rooms[code] = Room(RoomInfo(RoomStage.LOBBY, "p0", listOf(host)))
        onDone(Result.success(host))
    }

    override fun joinRoom(code: String, name: String, color: PawnColor, onDone: (Result<RoomPlayer>) -> Unit) {
        val room = rooms[code]
        val err = RoomRules.joinError(room?.info, color)
        if (room == null || err != null) {
            onDone(Result.failure(RoomException(err ?: "Camera nu există.")))
            return
        }
        val me = RoomPlayer(RoomRules.freeSeat(room.info.players)!!, name, color, RoomRole.GUEST)
        update(room) { it.copy(players = it.players + me) }
        onDone(Result.success(me))
    }

    override fun addBot(code: String, onDone: (Result<Unit>) -> Unit) {
        val room = rooms[code]
        val seat = room?.let { RoomRules.freeSeat(it.info.players) }
        val color = room?.let { RoomRules.freeColor(it.info.players) }
        if (room == null || seat == null || color == null || room.info.stage != RoomStage.LOBBY) {
            onDone(Result.failure(RoomException("Nu se mai poate adăuga un robot (masa e plină).")))
            return
        }
        val n = room.info.players.count { it.role == RoomRole.BOT } + 1
        update(room) { it.copy(players = it.players + RoomPlayer(seat, "Robot $n", color, RoomRole.BOT)) }
        onDone(Result.success(Unit))
    }

    override fun setStage(code: String, stage: RoomStage, onDone: (Result<Unit>) -> Unit) {
        val room = rooms[code] ?: return onDone(Result.failure(RoomException("Camera nu există.")))
        update(room) { it.copy(stage = stage) }
        onDone(Result.success(Unit))
    }

    override fun peekInfo(code: String, onInfo: (RoomInfo?) -> Unit) {
        onInfo(rooms[code]?.info)
    }

    override fun listenInfo(code: String, onInfo: (RoomInfo?) -> Unit): Closeable {
        val room = rooms[code]
        room?.infoListeners?.add(onInfo)
        onInfo(room?.info)
        return Closeable { room?.infoListeners?.remove(onInfo) }
    }

    override fun publishState(code: String, json: String) {
        val room = rooms[code] ?: return
        room.state = json
        room.stateListeners.toList().forEach { it(json) }
    }

    override fun listenState(code: String, onState: (String) -> Unit): Closeable {
        val room = rooms[code]
        room?.stateListeners?.add(onState)
        room?.state?.let(onState)
        return Closeable { room?.stateListeners?.remove(onState) }
    }

    override fun sendIntent(code: String, json: String) {
        val room = rooms[code] ?: return
        sentIntents += json
        room.pendingIntents.addLast(json)
        flush(room)
    }

    override fun listenIntents(code: String, onIntent: (String) -> Unit): Closeable {
        val room = rooms[code]
        room?.intentListeners?.add(onIntent)
        room?.let { flush(it) }
        return Closeable { room?.intentListeners?.remove(onIntent) }
    }

    private fun flush(room: Room) {
        while (room.intentListeners.isNotEmpty() && room.pendingIntents.isNotEmpty()) {
            val json = room.pendingIntents.removeFirst()
            room.intentListeners.toList().forEach { it(json) }
        }
    }

    private fun update(room: Room, change: (RoomInfo) -> RoomInfo) {
        room.info = change(room.info)
        room.infoListeners.toList().forEach { it(room.info) }
    }
}

package com.example.privatizare.multiplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.ui.game.GameViewModel
import com.example.privatizare.ui.game.PlayerSetup
import java.io.Closeable
import com.example.privatizare.logic.rollDie

/**
 * Sesiunea online a acestui telefon: creează/intră într-o cameră și, odată pornit meciul,
 * conectează [GameViewModel] la rețea.
 *  - Gazda rulează motorul: primește intențiile, le aplică, apoi publică starea oficială.
 *  - Oaspetele trimite doar intenții și afișează starea primită.
 */
class OnlineController(
    private val backend: RoomBackend,
    private val vm: GameViewModel,
    private val hostDie: () -> Int = { rollDie() },
    private val newCode: () -> String = { RoomCode.generate() },
) {
    var code by mutableStateOf<String?>(null)
        private set
    var me by mutableStateOf<RoomPlayer?>(null)
        private set
    var info by mutableStateOf<RoomInfo?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** True când meciul rulează și ecranul de joc trebuie afișat. */
    var inGame by mutableStateOf(false)
        private set

    val isHost: Boolean get() = me?.role == RoomRole.HOST
    val isInRoom: Boolean get() = code != null && me != null

    private val closers = mutableListOf<Closeable>()
    private var lastPublished: String? = null

    /** Apelat de UI după un timp fără răspuns: oprește așteptarea și explică ce poate fi greșit. */
    fun timeoutIfBusy() {
        if (!busy) return
        busy = false
        error = "Baza de date nu răspunde. Verifică internetul și că Realtime Database a fost creată " +
            "(regiunea ei poate cere URL în OnlineConfig.DATABASE_URL)."
    }

    /** Culorile deja luate în camera al cărei cod a fost scris; gol dacă nu există sau nu e încă un cod complet. */
    var takenColors by mutableStateOf<Set<PawnColor>>(emptySet())
        private set

    private var peekedCode: String? = null

    /** Apelat când se schimbă codul scris: pionii luați de alții se dezactivează înainte de conectare. */
    fun peekRoom(rawCode: String) {
        val c = rawCode.trim().uppercase()
        if (!RoomCode.isValid(c)) {
            peekedCode = null
            takenColors = emptySet()
            return
        }
        peekedCode = c
        backend.peekInfo(c) { room ->
            if (peekedCode == c) takenColors = room?.players?.map { it.color }?.toSet() ?: emptySet()
        }
    }

    fun clearError() {
        error = null
    }

    fun createRoom(name: String, color: PawnColor) {
        if (busy) return
        busy = true
        error = null
        tryCreate(name.trim(), color, attemptsLeft = 5)
    }

    private fun tryCreate(name: String, color: PawnColor, attemptsLeft: Int) {
        val c = newCode()
        backend.createRoom(c, name, color) { result ->
            result.onSuccess { player ->
                code = c
                me = player
                busy = false
                listenInfo(c)
            }.onFailure { e ->
                if (e.message?.contains("deja folosit") == true && attemptsLeft > 1) tryCreate(name, color, attemptsLeft - 1)
                else fail(e)
            }
        }
    }

    fun joinRoom(rawCode: String, name: String, color: PawnColor) {
        if (busy) return
        val c = rawCode.trim().uppercase()
        if (!RoomCode.isValid(c)) {
            error = "Codul are 4 caractere (litere și cifre)."
            return
        }
        busy = true
        error = null
        backend.joinRoom(c, name.trim(), color) { result ->
            result.onSuccess { player ->
                code = c
                me = player
                busy = false
                listenInfo(c)
            }.onFailure { fail(it) }
        }
    }

    fun addBot() {
        val c = code ?: return
        if (!isHost) return
        backend.addBot(c) { r -> r.onFailure { error = it.message } }
    }

    /** Gazda pornește meciul: locurile din cameră devin jucătorii partidei. */
    fun startMatch() {
        val c = code ?: return
        val room = info ?: return
        if (!isHost || !room.canStart) return
        val setup = room.players.sortedBy { it.id }.map { PlayerSetup(it.name, it.color, isBot = it.role == RoomRole.BOT) }
        vm.startGame(setup)
        vm.enterOnlineHost(me!!.id)
        closers += backend.listenIntents(c) { json -> onIntent(json) }
        publishNow()
        inGame = true
        backend.setStage(c, RoomStage.PLAYING) { r -> r.onFailure { error = it.message } }
    }

    /** Gazda: apelat la fiecare schimbare de stare, publică starea oficială doar dacă s-a schimbat. */
    fun publishNow() {
        val c = code ?: return
        if (!isHost) return
        val snap = vm.snapshot() ?: return
        val json = SnapshotCodec.encode(snap)
        if (json == lastPublished) return
        lastPublished = json
        backend.publishState(c, json)
    }

    private fun onIntent(json: String) {
        val event = IntentCodec.decode(json) ?: return
        val applied = applyEvent(vm, event) { hostDie() }
        if (applied) publishNow()
    }

    private fun listenInfo(c: String) {
        closers += backend.listenInfo(c) { room ->
            info = room
            if (room == null) return@listenInfo
            if (!isHost && room.stage == RoomStage.PLAYING && !inGame) beginGuestGame(c)
        }
    }

    private fun beginGuestGame(c: String) {
        val myId = me?.id ?: return
        vm.enterOnlineGuest(myId) { event -> backend.sendIntent(c, IntentCodec.encode(event)) }
        closers += backend.listenState(c) { json ->
            runCatching { SnapshotCodec.decode(json) }.onSuccess { vm.applySnapshot(it) }
        }
        inGame = true
    }

    fun leave() {
        closers.forEach { runCatching { it.close() } }
        closers.clear()
        vm.leaveOnline()
        code = null
        me = null
        info = null
        inGame = false
        busy = false
        lastPublished = null
    }

    private fun fail(e: Throwable) {
        busy = false
        error = e.message ?: "Eroare necunoscută."
    }
}

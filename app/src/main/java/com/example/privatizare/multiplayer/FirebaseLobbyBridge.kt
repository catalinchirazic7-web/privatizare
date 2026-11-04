package com.example.privatizare.multiplayer

import com.example.privatizare.data.model.PawnColor
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.io.Closeable

/**
 * Backend Firebase Realtime Database. Structura:
 *  - rooms/{cod}/info    : stadiul camerei, gazda, locurile (nume, culoare, rol)
 *  - rooms/{cod}/state   : starea oficială a jocului (JSON), scrisă doar de gazdă
 *  - rooms/{cod}/intents : coada de intenții trimise de oaspeți (JSON), ștearsă de gazdă după procesare
 *
 * [databaseUrl] e necesar doar dacă baza de date nu e în regiunea implicită
 * (google-services.json fără `firebase_url`).
 */
class FirebaseLobbyBridge(databaseUrl: String? = null) : RoomBackend {

    private val db: FirebaseDatabase =
        if (databaseUrl.isNullOrBlank()) FirebaseDatabase.getInstance() else FirebaseDatabase.getInstance(databaseUrl)

    private fun room(code: String) = db.getReference("rooms").child(code)

    override fun createRoom(code: String, hostName: String, hostColor: PawnColor, onDone: (Result<RoomPlayer>) -> Unit) {
        val host = RoomPlayer("p0", hostName, hostColor, RoomRole.HOST)
        val info = RoomInfo(RoomStage.LOBBY, "p0", listOf(host))
        room(code).child("info").runTransaction(object : Transaction.Handler {
            override fun doTransaction(d: MutableData): Transaction.Result {
                if (d.value != null) return Transaction.abort()
                d.value = RoomRules.toMap(info)
                return Transaction.success(d)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {
                onDone(
                    when {
                        error != null -> Result.failure(RoomException(describe(error)))
                        !committed -> Result.failure(RoomException("Codul $code este deja folosit."))
                        else -> Result.success(host)
                    },
                )
            }
        })
    }

    override fun joinRoom(code: String, name: String, color: PawnColor, onDone: (Result<RoomPlayer>) -> Unit) {
        var joined: RoomPlayer? = null
        room(code).child("info").runTransaction(object : Transaction.Handler {
            override fun doTransaction(d: MutableData): Transaction.Result {
                // Prima rulare vine cu cache-ul local gol: o confirmăm nemodificată ca Firebase să ceară
                // valoarea reală de pe server și să rerulăm tranzacția cu ea.
                if (d.value == null) return Transaction.success(d)
                val info = RoomRules.fromMap(d.value) ?: return Transaction.abort()
                if (RoomRules.joinError(info, color) != null) return Transaction.abort()
                val seat = RoomRules.freeSeat(info.players) ?: return Transaction.abort()
                val me = RoomPlayer(seat, name, color, RoomRole.GUEST)
                joined = me
                d.value = RoomRules.toMap(info.copy(players = info.players + me))
                return Transaction.success(d)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {
                val me = joined
                onDone(
                    when {
                        error != null -> Result.failure(RoomException(describe(error)))
                        committed && me != null -> Result.success(me)
                        else -> Result.failure(
                            RoomException(
                                RoomRules.joinError(RoomRules.fromMap(snap?.value), color) ?: "Nu se poate intra în cameră.",
                            ),
                        )
                    },
                )
            }
        })
    }

    override fun addBot(code: String, onDone: (Result<Unit>) -> Unit) {
        room(code).child("info").runTransaction(object : Transaction.Handler {
            override fun doTransaction(d: MutableData): Transaction.Result {
                // Prima rulare vine cu cache-ul local gol: o confirmăm nemodificată ca Firebase să ceară
                // valoarea reală de pe server și să rerulăm tranzacția cu ea.
                if (d.value == null) return Transaction.success(d)
                val info = RoomRules.fromMap(d.value) ?: return Transaction.abort()
                if (info.stage != RoomStage.LOBBY) return Transaction.abort()
                val seat = RoomRules.freeSeat(info.players) ?: return Transaction.abort()
                val color = RoomRules.freeColor(info.players) ?: return Transaction.abort()
                val bots = info.players.count { it.role == RoomRole.BOT }
                val bot = RoomPlayer(seat, "Robot ${bots + 1}", color, RoomRole.BOT)
                d.value = RoomRules.toMap(info.copy(players = info.players + bot))
                return Transaction.success(d)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {
                onDone(
                    when {
                        error != null -> Result.failure(RoomException(describe(error)))
                        committed -> Result.success(Unit)
                        else -> Result.failure(RoomException("Nu se mai poate adăuga un robot (masa e plină)."))
                    },
                )
            }
        })
    }

    override fun setStage(code: String, stage: RoomStage, onDone: (Result<Unit>) -> Unit) {
        room(code).child("info").child("stage").setValue(stage.name) { error, _ ->
            onDone(if (error != null) Result.failure(RoomException(describe(error))) else Result.success(Unit))
        }
    }

    override fun peekInfo(code: String, onInfo: (RoomInfo?) -> Unit) {
        room(code).child("info").get()
            .addOnSuccessListener { onInfo(RoomRules.fromMap(it.value)) }
            .addOnFailureListener { onInfo(null) }
    }

    override fun listenInfo(code: String, onInfo: (RoomInfo?) -> Unit): Closeable {
        val ref = room(code).child("info")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) = onInfo(RoomRules.fromMap(s.value))
            override fun onCancelled(e: DatabaseError) = onInfo(null)
        }
        ref.addValueEventListener(l)
        return Closeable { ref.removeEventListener(l) }
    }

    override fun publishState(code: String, json: String) {
        room(code).child("state").setValue(json)
    }

    override fun listenState(code: String, onState: (String) -> Unit): Closeable {
        val ref = room(code).child("state")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                (s.value as? String)?.let(onState)
            }

            override fun onCancelled(e: DatabaseError) = Unit
        }
        ref.addValueEventListener(l)
        return Closeable { ref.removeEventListener(l) }
    }

    override fun sendIntent(code: String, json: String) {
        room(code).child("intents").push().setValue(json)
    }

    override fun listenIntents(code: String, onIntent: (String) -> Unit): Closeable {
        val ref = room(code).child("intents")
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, previous: String?) {
                val json = s.value as? String
                s.ref.removeValue()   // fiecare intenție se livrează o singură dată
                if (json != null) onIntent(json)
            }

            override fun onChildChanged(s: DataSnapshot, previous: String?) = Unit
            override fun onChildRemoved(s: DataSnapshot) = Unit
            override fun onChildMoved(s: DataSnapshot, previous: String?) = Unit
            override fun onCancelled(e: DatabaseError) = Unit
        }
        ref.addChildEventListener(l)
        return Closeable { ref.removeEventListener(l) }
    }

    private fun describe(e: DatabaseError): String = when (e.code) {
        DatabaseError.PERMISSION_DENIED ->
            "Baza de date a refuzat accesul. Verifică regulile Realtime Database (citire/scriere)."
        DatabaseError.NETWORK_ERROR, DatabaseError.DISCONNECTED -> "Fără conexiune la internet."
        else -> "Eroare la baza de date: ${e.message}"
    }
}

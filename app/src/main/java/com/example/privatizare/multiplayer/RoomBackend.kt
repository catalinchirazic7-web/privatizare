package com.example.privatizare.multiplayer

import com.example.privatizare.data.model.PawnColor
import java.io.Closeable

/**
 * Canalul cu serverul camerei. Implementări: [FirebaseRoomBackend] (Realtime Database)
 * și [InMemoryRoomBackend] (teste, fără rețea). Rezultatele vin prin callback.
 */
interface RoomBackend {
    /** Creează camera [code] cu gazda la locul p0. Eșuează dacă există deja. */
    fun createRoom(code: String, hostName: String, hostColor: PawnColor, onDone: (Result<RoomPlayer>) -> Unit)

    /** Ocupă primul loc liber. Eșuează dacă camera lipsește, e plină, a pornit sau culoarea e luată. */
    fun joinRoom(code: String, name: String, color: PawnColor, onDone: (Result<RoomPlayer>) -> Unit)

    /** Gazda adaugă un robot pe primul loc liber, cu prima culoare liberă. */
    fun addBot(code: String, onDone: (Result<Unit>) -> Unit)

    fun setStage(code: String, stage: RoomStage, onDone: (Result<Unit>) -> Unit)

    /** Citește o singură dată starea camerei (null dacă nu există), ca să știm ce culori sunt luate. */
    fun peekInfo(code: String, onInfo: (RoomInfo?) -> Unit)

    fun listenInfo(code: String, onInfo: (RoomInfo?) -> Unit): Closeable

    /** Scrisă doar de gazdă: starea oficială a jocului, ca JSON. */
    fun publishState(code: String, json: String)

    fun listenState(code: String, onState: (String) -> Unit): Closeable

    /** Intenție trimisă de un oaspete (JSON). */
    fun sendIntent(code: String, json: String)

    /** Gazda primește fiecare intenție o singură dată; ea se șterge după livrare. */
    fun listenIntents(code: String, onIntent: (String) -> Unit): Closeable
}

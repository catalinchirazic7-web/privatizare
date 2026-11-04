package com.example.privatizare.multiplayer

/** Modul pass-and-play: evenimentul se livrează direct local, fără rețea. */
class LocalPassAndPlayBridge : GameRoomSession {
    private var listener: ((GameEvent) -> Unit)? = null

    override fun send(event: GameEvent) {
        listener?.invoke(event)
    }

    override fun setListener(listener: (GameEvent) -> Unit) {
        this.listener = listener
    }
}

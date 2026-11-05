package com.example.privatizare.logic

import kotlin.random.Random

/**
 * Pachet de cărți cu extragere FIFO din teancul amestecat.
 * Cărțile trase merg în teancul de descărcare. Când teancul de extragere se golește,
 * teancul de descărcare se amestecă din nou și devine teanc de extragere.
 */
class CardDeck<T>(cards: List<T>, private val random: Random) {

    private val all: List<T> = cards.toList()
    private val pile = ArrayDeque<T>()
    private val discard = ArrayList<T>()

    init {
        require(all.isNotEmpty()) { "Pachet gol" }
        pile.addAll(all.shuffled(random))
    }

    /** Numărul de cărți rămase în teancul de extragere. */
    val remaining: Int get() = pile.size

    /** Numărul de cărți din teancul de descărcare. */
    val discardCount: Int get() = discard.size

    /** Extrage cartea de sus. Dacă teancul e gol, reunește descărcarea și reamestecă înainte. */
    fun draw(): T {
        if (pile.isEmpty()) {
            pile.addAll(discard.shuffled(random))
            discard.clear()
        }
        val card = pile.removeFirst()
        discard.add(card)
        return card
    }

    /** Scoate din teanc cartonașul cu valoarea cea mai mare pentru [valueOf] (la egalitate: primul). */
    fun drawBest(valueOf: (T) -> Long): T {
        if (pile.isEmpty()) {
            pile.addAll(discard.shuffled(random))
            discard.clear()
        }
        var bi = 0
        for (i in 1 until pile.size) if (valueOf(pile[i]) > valueOf(pile[bi])) bi = i
        val card = pile.removeAt(bi)
        discard.add(card)
        return card
    }
}

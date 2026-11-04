package com.example.privatizare.logic

import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.PropertyAction

/**
 * Calcule pentru acțiuni, în ECU. Prețurile sunt fixe (valoarea scrisă pe fiecare anexă).
 * Nivelul = numărul de anexe deținute. Anexele se cumpără în ordinea indexului.
 */
object ActionCalculator {

    /**
     * Chiria datorată de un jucător care intră pe proprietate.
     * Nivel 0: doar titlul -> titleTax.
     * Nivel k: titleTax + suma profiturilor anexelor cu index <= k.
     */
    fun calculateRent(property: PropertyAction, ownedLevel: Int): Long {
        require(ownedLevel in 0..property.annexes.size) { "Nivel invalid: $ownedLevel" }
        val profits = property.annexes.filter { it.index <= ownedLevel }.sumOf { it.profit }
        return property.titleTax + profits
    }

    /** Următoarea anexă de cumpărat, sau null dacă toate sunt deținute. */
    fun nextAnnex(property: PropertyAction, ownedLevel: Int): Annex? =
        property.annexes.firstOrNull { it.index == ownedLevel + 1 }

    /** Suma primită de la Bancă la returnarea unei acțiuni. */
    fun sellBackValue(initialPrice: Long): Long =
        initialPrice * GameRules.RESALE_RATIO_PERCENT / 100

    /** Valoarea întregii acțiuni cu [level] anexe: titlul plus anexele cu index <= level, toate la 80%. */
    fun wholeSaleValue(property: PropertyAction, level: Int): Long =
        sellBackValue(property.titlePrice) +
            property.annexes.filter { it.index <= level }.sumOf { sellBackValue(it.price) }
}

package com.example.privatizare.data.model

/** Căsuță de pe tablă. Ordinea urmează săgeata START, indexată 0..37. */
sealed class Tile {
    abstract val index: Int

    data class Start(override val index: Int) : Tile()
    data class Property(override val index: Int, val propertyId: String) : Tile()
    data class Loto(override val index: Int) : Tile()
    data class Telex(override val index: Int) : Tile()
    data class Bank(override val index: Int) : Tile()
    data class BankDividend(override val index: Int) : Tile()
    data class CasaDeOdihna(override val index: Int) : Tile()
    data class SalaConsfatuiri(override val index: Int) : Tile()
    data class RechematUrgent(override val index: Int) : Tile()
}

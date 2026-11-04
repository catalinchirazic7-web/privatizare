package com.example.privatizare.multiplayer

import java.security.SecureRandom

/** Cod de cameră de 4 caractere (litere mari și cifre), generat cu un generator criptografic. */
object RoomCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // fără I, O, 0, 1 ca să nu se confunde
    private const val LENGTH = 4
    private val random = SecureRandom()

    fun generate(): String = buildString {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }
}

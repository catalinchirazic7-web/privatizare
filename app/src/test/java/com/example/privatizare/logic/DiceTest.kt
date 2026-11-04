package com.example.privatizare.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceTest {

    @Test
    fun zarulAcopereToateFetele1La6() {
        val counts = IntArray(7)
        val rnd = Random(2026)
        repeat(60_000) { counts[rollDie(rnd)]++ }
        assertEquals(0, counts[0])
        // fiecare față ~10 000 de ori (toleranță 6%), nu 1 și 2 în proporție mare
        for (face in 1..6) assertTrue("fața $face: ${counts[face]}", counts[face] in 9_400..10_600)
    }

    @Test
    fun zarulImplicitNuEsteConstant() {
        val seen = (1..600).map { rollDie() }.toSet()
        assertEquals(setOf(1, 2, 3, 4, 5, 6), seen)
    }
}

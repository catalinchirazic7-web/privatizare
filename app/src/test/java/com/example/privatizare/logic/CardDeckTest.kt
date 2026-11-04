package com.example.privatizare.logic

import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.TelexCard
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class CardDeckTest {

    private fun telexFromJson(): List<TelexCard> {
        val text = File("src/main/assets/cards.json").readText(Charsets.UTF_8)
        return GameRepository.parseTelex(JSONObject(text).getJSONArray("telex"))
    }

    @Test
    fun ordineaDupaAmestecareDifera_deOrdineaBruta() {
        val raw = telexFromJson()
        val deck = CardDeck(raw, Random(12345))
        val drawn = List(raw.size) { deck.draw() }
        assertNotEquals("Ordinea amestecată nu trebuie să coincidă cu ordinea din JSON", raw.map { it.id }, drawn.map { it.id })
        // Aceleași 60 de cărți, fiecare o singură dată
        assertEquals(raw.map { it.id }.sorted(), drawn.map { it.id }.sorted())
    }

    @Test
    fun dupaToateCartileSePareamestecaSiExtragereaContinua() {
        val raw = telexFromJson()
        val deck = CardDeck(raw, Random(7))
        repeat(raw.size) { deck.draw() }
        assertEquals(0, deck.remaining)
        assertEquals(raw.size, deck.discardCount)

        // A 61-a extragere reamestecă descărcarea și trage o carte validă
        val next = deck.draw()
        assertTrue(raw.any { it.id == next.id })
        assertEquals(raw.size - 1, deck.remaining)
        assertEquals(1, deck.discardCount)
    }

    @Test
    fun pachetDeCiclareNuSeBlocheazaNiciodata() {
        val small = listOf("a", "b", "c")
        val deck = CardDeck(small, Random(1))
        val seen = List(30) { deck.draw() }
        assertEquals(30, seen.size)
        assertTrue(seen.all { it in small })
    }
}

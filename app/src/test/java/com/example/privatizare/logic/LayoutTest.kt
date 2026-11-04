package com.example.privatizare.logic

import com.example.privatizare.data.GameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

class LayoutTest {

    private fun asset(name: String): String =
        File("src/main/assets/$name").readText(Charsets.UTF_8)

    @Test
    fun tablaNouaAreAceleasiCasuteDarAltePozitii() {
        val oldJson = asset("board.json")
        val newJson = asset("board_new.json")
        // aceleași căsuțe, în aceeași ordine, cu aceleași tipuri și proprietăți
        assertEquals(GameRepository.parseBoard(oldJson), GameRepository.parseBoard(newJson))
        val oldPos = GameRepository.parsePositions(oldJson)
        val newPos = GameRepository.parsePositions(newJson)
        assertEquals(37, oldPos.size)
        assertEquals(37, newPos.size)
        assertNotEquals(oldPos, newPos)
    }

    @Test
    fun startulTablaNoiEsteCelDinJson() {
        val start = GameRepository.parseStart(asset("board_new.json"))
        assertEquals(0.137f, start.first, 1e-6f)
        assertEquals(0.884f, start.second, 1e-6f)
    }
}

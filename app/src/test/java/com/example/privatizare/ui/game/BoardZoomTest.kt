package com.example.privatizare.ui.game

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardZoomTest {

    private val w = 1000f
    private val h = 700f

    @Test
    fun laScara1TabilaNuSePoateMuta() {
        val s = applyZoomGesture(ZoomState(1f, Offset.Zero), Offset(500f, 350f), Offset(80f, 40f), 1f, w, h)
        assertEquals(Offset.Zero, s.offset)
        assertEquals(1f, s.scale, 0f)
    }

    @Test
    fun scaraNuDepaseste4xSiNuScadeSub1x() {
        val up = applyZoomGesture(ZoomState(3f, Offset.Zero), Offset(0f, 0f), Offset.Zero, 5f, w, h)
        assertEquals(4f, up.scale, 0f)
        val down = applyZoomGesture(ZoomState(1.5f, Offset(-100f, -50f)), Offset(0f, 0f), Offset.Zero, 0.1f, w, h)
        assertEquals(1f, down.scale, 0f)
    }

    @Test
    fun punctulDeSubDegeteRamanePeLaZoom() {
        // Punctul din tablă aflat sub centroid trebuie să rămână sub centroid după zoom
        val centroid = Offset(600f, 300f)
        val before = ZoomState(1.5f, Offset(-200f, -100f))
        val pointBefore = (centroid - before.offset) / before.scale
        val after = applyZoomGesture(before, centroid, Offset.Zero, 1.4f, w, h)
        val pointAfter = (centroid - after.offset) / after.scale
        assertEquals(pointBefore.x, pointAfter.x, 0.01f)
        assertEquals(pointBefore.y, pointAfter.y, 0.01f)
    }

    @Test
    fun deplasareaRamaneInLimite() {
        val s = applyZoomGesture(ZoomState(2f, Offset(-500f, -300f)), Offset(0f, 0f), Offset(900f, 900f), 1f, w, h)
        assertTrue(s.offset.x <= 0f && s.offset.y <= 0f)
        val far = applyZoomGesture(ZoomState(2f, Offset(-500f, -300f)), Offset(0f, 0f), Offset(-5000f, -5000f), 1f, w, h)
        assertEquals(w * (1f - 2f), far.offset.x, 0.01f)
        assertEquals(h * (1f - 2f), far.offset.y, 0.01f)
    }
}

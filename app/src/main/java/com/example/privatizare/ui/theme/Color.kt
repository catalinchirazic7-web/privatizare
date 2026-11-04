package com.example.privatizare.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Culorile copertei jocului, calculate din imaginea logo_privatizare.webp
val CoverRed = Color(0xFFDA5C3D)
val CoverYellow = Color(0xFFF0D20E)
val CoverNight = Color(0xFF5C5F79)
val CoverSky = Color(0xFF6F7792)
val CoverDark = Color(0xFF332522)
val CoverCream = Color(0xFFF7F1E1)

/** Fundalul jocului: de la maroul închis al copertei spre albastrul de noapte. */
val CoverGradient: Brush = Brush.verticalGradient(listOf(CoverDark, CoverNight))

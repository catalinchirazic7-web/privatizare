package com.example.privatizare.data.model

/** Cele 8 culori disponibile pentru pioni. [hex] este ARGB (0xAARRGGBB). */
enum class PawnColor(val displayName: String, val hex: Long) {
    ROSU("Roșu", 0xFFD32F2F),
    ALBASTRU("Albastru", 0xFF1976D2),
    GALBEN("Galben", 0xFFFBC02D),
    VERDE("Verde", 0xFF388E3C),
    MOV("Mov", 0xFF7B1FA2),
    PORTOCALIU("Portocaliu", 0xFFF57C00),
    NEGRU("Negru", 0xFF263238),
    ALB_ARGINTIU("Alb/Argintiu", 0xFFECEFF1),
}

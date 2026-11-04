package com.example.privatizare.data.model

/** Anexă a unui obiectiv. [index] începe de la 1. Prețul este fix. */
data class Annex(
    val index: Int,
    val price: Long,
    val profit: Long,
)

/** Obiectiv privatizabil: titlu de bază plus 2–4 anexe. Sursa: actions.json. */
data class PropertyAction(
    val id: String,
    val name: String,
    val titlePrice: Long,
    val titleTax: Long,
    val annexes: List<Annex>,
)

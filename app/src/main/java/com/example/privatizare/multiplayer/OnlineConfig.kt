package com.example.privatizare.multiplayer

/**
 * Setări pentru jocul online. Dacă baza de date Realtime Database nu e în regiunea implicită
 * (google-services.json fără `firebase_url`), pune aici adresa ei din consola Firebase,
 * de exemplu "https://privatizare-default-rtdb.europe-west1.firebasedatabase.app".
 */
object OnlineConfig {
    const val DATABASE_URL: String = "https://privatizare-default-rtdb.europe-west1.firebasedatabase.app"
}

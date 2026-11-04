package com.example.privatizare.ui.game

import androidx.compose.ui.graphics.Color
import com.example.privatizare.data.model.Tile

/**
 * Identitatea vizuală a unei căsuțe: iconița, culoarea de brand și eticheta scurtă.
 * Iconița spune dintr-o privire ce fel de firmă e (avion = aviație, tren = CFR etc.),
 * culoarea de brand se folosește la carduri, la marcaje și la scânteia de aterizare.
 */
data class TileVisual(
    val icon: String,
    val accent: Color,
    /** Numele fără prefixele juridice (S.C. / S.A. / S.R.L.), pentru spații mici. */
    val shortName: String,
)

/** Elimină prefixele juridice și punctele finale: "S.A. CONTINENTAL" -> "CONTINENTAL". */
fun shortPropertyName(name: String): String {
    var s = name.trim()
    listOf("S.C.", "S.A.", "S.R.L.", "SC ", "SA ", "SRL ").forEach { prefix ->
        if (s.startsWith(prefix, ignoreCase = true)) s = s.removePrefix(prefix).trim()
    }
    return s.trimEnd('.', ' ').ifBlank { name }
}

// --- Culori de brand, saturate (nu pastelate) ---
private val NAVY = Color(0xFF0D47A1)
private val CRIMSON = Color(0xFFC62828)
private val MAGENTA = Color(0xFFAD1457)
private val PURPLE = Color(0xFF6A1B9A)
private val VIOLET = Color(0xFF7B1FA2)
private val DEEP_PURPLE = Color(0xFF4527A0)
private val INDIGO = Color(0xFF283593)
private val SKY = Color(0xFF0277BD)
private val CYAN = Color(0xFF00838F)
private val TEAL = Color(0xFF00695C)
private val MINT = Color(0xFF00897B)
private val GREEN = Color(0xFF2E7D32)
private val FOREST = Color(0xFF1B5E20)
private val OLIVE = Color(0xFF33691E)
private val AMBER = Color(0xFFF9A825)
private val ORANGE = Color(0xFFEF6C00)
private val RUST = Color(0xFFD84315)
private val BROWN = Color(0xFF5D4037)
private val TAUPE = Color(0xFF8D6E63)
private val STEEL = Color(0xFF455A64)
private val SLATE = Color(0xFF37474F)
private val BLUE_GREY = Color(0xFF546E7A)
private val GOLD = Color(0xFF8D6E00)

/** Iconița și culoarea fiecărei firme privatizabile. */
fun propertyVisual(propertyId: String): TileVisual = when (propertyId) {
    "continental" -> TileVisual("\uD83C\uDFE8", NAVY, "CONTINENTAL")          // hotel
    "ambasador" -> TileVisual("\uD83D\uDECE", CRIMSON, "AMBASADOR")           // recepție hotel
    "maria" -> TileVisual("\uD83C\uDF7D", MAGENTA, "MARIA")                   // restaurant
    "lipscani" -> TileVisual("\uD83C\uDFEC", PURPLE, "LIPSCANI")              // magazin
    "piata_romana" -> TileVisual("\u26F2", GREEN, "PIAȚA ROMÂNĂ")             // fântână
    "piata_victoriei" -> TileVisual("\uD83C\uDFAD", ORANGE, "PIAȚA VICTORIEI")// teatru/operă
    "unirii" -> TileVisual("\uD83C\uDFDB", GOLD, "UNIRII")                    // clădire istorică
    "taberei" -> TileVisual("\uD83C\uDFD5", OLIVE, "TABEREI")                 // cort
    "lahovary" -> TileVisual("\uD83C\uDFE2", SLATE, "LAHOVARY")               // birouri
    "poli" -> TileVisual("\u26BD", MINT, "POLI")                              // fotbal
    "timisoreana" -> TileVisual("\uD83C\uDF7A", AMBER, "TIMIȘOREANA")         // bere
    "prichindel" -> TileVisual("\uD83E\uDDF8", MAGENTA, "PRICHINDEL")         // jucării
    "padurea_verde" -> TileVisual("\uD83C\uDF32", FOREST, "PĂDUREA VERDE")    // pădure
    "televiziune" -> TileVisual("\uD83D\uDCFA", DEEP_PURPLE, "TELEVIZIUNE")   // TV
    "helicon" -> TileVisual("\uD83C\uDFB5", VIOLET, "HELICON")                // muzică
    "memorii" -> TileVisual("\uD83D\uDCBE", CYAN, "MEMORII")                  // memorie
    "celuloza" -> TileVisual("\uD83D\uDCC4", BROWN, "CELULOZA")               // hârtie
    "metalo_tim" -> TileVisual("\u2699", STEEL, "METALO-TIM")                 // metalurgie
    "ohm" -> TileVisual("\uD83D\uDD0C", INDIGO, "OHM")                        // electric
    "dura" -> TileVisual("\uD83D\uDD27", BLUE_GREY, "DURA")                   // unelte
    "guban" -> TileVisual("\uD83D\uDD28", TAUPE, "GUBAN")                     // ciocan
    "electromotor" -> TileVisual("\u26A1", AMBER, "ELECTROMOTOR")             // curent
    "telefoane" -> TileVisual("\u260E", TEAL, "TELEFOANE")                    // telefon
    "circumvalatiunii" -> TileVisual("\uD83D\uDEE3", INDIGO, "CIRCUMVALAȚIUNII") // drum
    "calea_sagului" -> TileVisual("\uD83D\uDE89", RUST, "CALEA ȘAGULUI")      // gară
    "atelier_cfr" -> TileVisual("\uD83D\uDE82", CRIMSON, "ATELIERE CFR")      // tren
    "aviatica" -> TileVisual("\u2708", SKY, "AVIATICA")                       // avion
    else -> TileVisual("\uD83C\uDFE2", SLATE, shortPropertyName(propertyId))
}

/** Iconița unei căsuțe speciale (START, LOTO, TELEX, BANCA, colțuri). */
fun tileVisual(tile: Tile): TileVisual = when (tile) {
    is Tile.Start -> TileVisual("\uD83C\uDFC1", GOLD, "START")
    is Tile.Loto -> TileVisual("\uD83C\uDFB0", MAGENTA, "LOTO")
    is Tile.Telex -> TileVisual("\uD83D\uDCE0", SKY, "TELEX")
    is Tile.Bank -> TileVisual("\uD83C\uDFE6", TEAL, "BANCA")
    is Tile.BankDividend -> TileVisual("\uD83D\uDCB0", GOLD, "DIVIDENDE")
    is Tile.CasaDeOdihna -> TileVisual("\uD83D\uDECB", MINT, "CASA DE ODIHNĂ")
    is Tile.SalaConsfatuiri -> TileVisual("\uD83D\uDC54", SLATE, "SALA CONSFAȚUIRI")
    is Tile.RechematUrgent -> TileVisual("\u21A9", RUST, "RECHEMAT URGENT")
    is Tile.Property -> propertyVisual(tile.propertyId)
}

/** O descriere scurtă pentru căsuțele fără proprietar (apare pe cardul de aterizare). */
fun tileHint(tile: Tile): String? = when (tile) {
    is Tile.Start -> "Treci pe aici și încasezi banii de la Bancă."
    is Tile.Loto -> "Trage un cartonaș LOTO: câștig sau pierdere, după noroc."
    is Tile.Telex -> "Trage un cartonaș TELEX: mutări, plăți și beneficii."
    is Tile.Bank -> "Aici se fac plățile și încasările către Bancă."
    is Tile.BankDividend -> "Banca îți achită dividendele cuvenite."
    is Tile.CasaDeOdihna -> "Stai două ture: pionul se odihnește. Ieși în ordinea în care ai ajuns."
    is Tile.SalaConsfatuiri -> "Ședință la consiliu: urmează instrucțiunile."
    is Tile.RechematUrgent -> "Te întorci urgent la START."
    is Tile.Property -> null
}

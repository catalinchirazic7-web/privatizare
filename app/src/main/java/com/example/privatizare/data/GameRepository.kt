package com.example.privatizare.data

import android.content.Context
import com.example.privatizare.data.model.Annex
import com.example.privatizare.data.model.BeneficiaryType
import com.example.privatizare.data.model.CardEffect
import com.example.privatizare.data.model.LotoCard
import com.example.privatizare.data.model.LotoKind
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.TelexCard
import com.example.privatizare.data.model.Tile
import org.json.JSONArray
import org.json.JSONObject

/** Încarcă board.json, actions.json și cards.json din assets. */
class GameRepository(private val context: Context) {

    data class Catalog(
        val board: List<Tile>,
        val actions: Map<String, PropertyAction>,
        val loto: List<LotoCard>,
        val telex: List<TelexCard>,
        /** Centrele căsuțelor în fracții [0,1] (x, y), indexate după poziție. */
        val positions: List<Pair<Float, Float>>,
        /** Casetele pachetelor de pe tablă ("loto", "telex"): x, y, lățime, înălțime ca fracții [0,1]. */
        val decks: Map<String, DeckRect> = emptyMap(),
        /** Id-ul cartonașului -> fotografia lui din assets (cartonașele reale, scanate). */
        val cardArt: Map<String, String> = emptyMap(),
        /** Coordonatele săgeții de START: locul de unde pleacă toți pionii (nu e căsuță de traseu). */
        val startMarker: Pair<Float, Float> = 0.125f to 0.908f,
    )

    data class DeckRect(val x: Float, val y: Float, val w: Float, val h: Float)

    fun load(): Catalog {
        val cards = JSONObject(read("cards.json"))
        val boardJson = read("board.json")
        val loto = parseLoto(cards.getJSONArray("loto"))
        val telex = parseTelex(cards.getJSONArray("telex"))
        return Catalog(
            board = parseBoard(boardJson),
            positions = parsePositions(boardJson),
            decks = parseDecks(boardJson),
            actions = parseActions(read("actions.json")),
            loto = loto,
            telex = telex,
            // Fiecare cartonaș tras își găsește fotografia după id-ul lui
            // (textul nu e unic: mai multe cartonașe au același text).
            cardArt = cardArtMap(loto, telex),
            startMarker = parseStart(boardJson),
        )
    }

    private fun read(name: String): String =
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    companion object {

        /**
         * Harta id de cartonaș -> calea fotografiei lui din assets.
         * Cheia e id-ul, nu textul: zece cartonașe LOTO au același text.
         */
        fun cardArtMap(loto: List<LotoCard>, telex: List<TelexCard>): Map<String, String> = buildMap {
            loto.forEach { put(it.id, "carti/${it.id}.webp") }
            telex.forEach { put(it.id, "carti/${it.id}.webp") }
        }

        fun parseBoard(text: String): List<Tile> {
            val arr = JSONObject(text).getJSONArray("spaces")
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val index = o.getInt("index")
                when (o.getString("type")) {
                    "START" -> Tile.Start(index)
                    "PROPRIETATE" -> Tile.Property(index, o.getString("propertyId"))
                    "LOTO" -> Tile.Loto(index)
                    "TELEX" -> Tile.Telex(index)
                    "BANCA" -> Tile.Bank(index)
                    "BANCA_DIVIDEND" -> Tile.BankDividend(index)
                    "COLT" -> {
                        val name = o.getString("name")
                        when {
                            name.startsWith("SALA") -> Tile.SalaConsfatuiri(index)
                            name.startsWith("CASA") -> Tile.CasaDeOdihna(index)
                            name.startsWith("RECHEMAT") -> Tile.RechematUrgent(index)
                            else -> error("Colț necunoscut: $name")
                        }
                    }
                    else -> error("Tip de căsuță necunoscut: ${o.getString("type")}")
                }
            }.sortedBy { it.index }
        }

        fun parsePositions(text: String): List<Pair<Float, Float>> {
            val arr = JSONObject(text).getJSONArray("spaces")
            return (0 until arr.length())
                .map { i -> arr.getJSONObject(i) }
                .sortedBy { it.getInt("index") }
                .map { o -> o.getDouble("x").toFloat() to o.getDouble("y").toFloat() }
        }

        /** Săgeata de START din colțul tablei (fracții din imagine). */
        fun parseStart(text: String): Pair<Float, Float> {
            val o = JSONObject(text).optJSONObject("_meta")?.optJSONObject("start")
                ?: return 0.125f to 0.908f
            return o.getDouble("x").toFloat() to o.getDouble("y").toFloat()
        }

        fun parseDecks(text: String): Map<String, DeckRect> {
            val o = JSONObject(text).optJSONObject("decks") ?: return emptyMap()
            return o.keys().asSequence().associateWith { k ->
                val d = o.getJSONObject(k)
                DeckRect(d.getDouble("x").toFloat(), d.getDouble("y").toFloat(), d.getDouble("w").toFloat(), d.getDouble("h").toFloat())
            }
        }

        fun parseActions(text: String): Map<String, PropertyAction> {
            val arr = JSONObject(text).getJSONArray("properties")
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val anexe = o.getJSONArray("anexe")
                PropertyAction(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    titlePrice = o.getLong("titlePrice"),
                    titleTax = o.getLong("titleTax"),
                    annexes = (0 until anexe.length()).map { j ->
                        val a = anexe.getJSONObject(j)
                        Annex(index = j + 1, price = a.getLong("price"), profit = a.getLong("profit"))
                    },
                )
            }.associateBy { it.id }
        }

        fun parseLoto(arr: JSONArray): List<LotoCard> = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val kind = when (o.getString("kind")) {
                "W" -> LotoKind.WIN
                "L" -> LotoKind.LOSE
                "N" -> LotoKind.NOTHING
                else -> error("Tip LOTO necunoscut")
            }
            LotoCard(
                id = o.getString("id"),
                kind = kind,
                amount = if (o.isNull("amount")) 0L else o.getLong("amount"),
                text = o.getString("text"),
            )
        }

        fun parseTelex(arr: JSONArray): List<TelexCard> = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val effects = o.getJSONArray("effects")
            TelexCard(
                id = o.getString("id"),
                text = o.getString("text"),
                effects = (0 until effects.length()).map { j -> parseEffect(effects.getJSONObject(j)) },
                beneficiaryType = o.optString("beneficiaryType").takeIf { it.isNotEmpty() }?.let { BeneficiaryType.valueOf(it) },
                targetCompanyId = o.optString("targetCompanyId").takeIf { !o.isNull("targetCompanyId") && it.isNotEmpty() },
                handwritten = o.optBoolean("handwritten", false),
            )
        }

        private fun parseEffect(e: JSONObject): CardEffect = when (e.getString("type")) {
            "pay_bank" -> CardEffect.PayBank(e.getLong("amount"))
            "pay_owner_or_bank" -> CardEffect.PayOwnerOrBank(e.getLong("amount"), e.getString("propertyId"))
            "receive_bank" -> CardEffect.ReceiveBank(e.getLong("amount"))
            "receive_from_each_player" -> CardEffect.ReceiveFromEachPlayer(e.getLong("amount"))
            "move" -> CardEffect.Move(e.getInt("steps"))
            "skip_turns" -> CardEffect.SkipTurns(e.getInt("turns"))
            else -> error("Efect necunoscut: ${e.getString("type")}")
        }
    }
}

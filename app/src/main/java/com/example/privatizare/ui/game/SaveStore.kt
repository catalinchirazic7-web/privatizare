package com.example.privatizare.ui.game

import android.content.Context
import com.example.privatizare.multiplayer.GameSnapshot
import com.example.privatizare.multiplayer.SnapshotCodec
import org.json.JSONObject
import java.io.File

/**
 * Salvarea partidei locale. Fișierul din memoria internă conține starea (același format ca la jocul online)
 * și timpul rămas. Erorile de citire/scriere se ignoră: jocul merge și fără salvare.
 */
object SaveStore {
    private const val FILE = "partida_salvata.json"

    class Saved(val snapshot: GameSnapshot, val leftMs: Long?)

    fun save(context: Context, snapshot: GameSnapshot, leftMs: Long?) {
        runCatching {
            val json = JSONObject()
                .put("snap", SnapshotCodec.encode(snapshot))
                .put("left", leftMs ?: JSONObject.NULL)
            File(context.filesDir, FILE).writeText(json.toString())
        }
    }

    fun load(context: Context): Saved? = runCatching {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return@runCatching null
        val json = JSONObject(file.readText())
        Saved(
            snapshot = SnapshotCodec.decode(json.getString("snap")),
            leftMs = if (json.isNull("left")) null else json.getLong("left"),
        )
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}

/** Ultima configurare de start (jucători, nume, culori, sumă, timp), ca să nu o reintroduci. */
object SetupMemory {
    private const val PREFS = "configurare_start"

    class Config(
        val count: Int,
        val names: List<String>,
        val colors: List<String?>,
        val bots: List<Boolean>,
        val money: Long?,
        val timeMinutes: Int?,
        /** 1 zar (implicit) sau 2 zaruri (mod nou, doar local). */
        val dice: Int = 1,
        /** "old" (tabla veche, implicit) sau "new" (tabla nouă). */
        val layout: String = "old",
    )

    fun load(context: Context): Config? = runCatching {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("count")) return@runCatching null
        Config(
            count = p.getInt("count", 2),
            names = (0 until 4).map { p.getString("name$it", null) ?: "Jucător ${it + 1}" },
            colors = (0 until 4).map { p.getString("color$it", null) },
            bots = (0 until 4).map { p.getBoolean("bot$it", false) },
            money = p.getLong("money", -1L).takeIf { it > 0 },
            timeMinutes = p.getInt("time", -1).takeIf { it > 0 },
            dice = if (p.getInt("dice", 1) == 2) 2 else 1,
            layout = if (p.getString("layout", "old") == "new") "new" else "old",
        )
    }.getOrNull()

    fun save(context: Context, c: Config) {
        runCatching {
            val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            e.putInt("count", c.count)
            c.names.forEachIndexed { i, n -> e.putString("name$i", n) }
            c.colors.forEachIndexed { i, col -> e.putString("color$i", col) }
            c.bots.forEachIndexed { i, b -> e.putBoolean("bot$i", b) }
            e.putLong("money", c.money ?: -1L)
            e.putInt("time", c.timeMinutes ?: -1)
            e.putInt("dice", c.dice)
            e.putString("layout", c.layout)
            e.apply()
        }
    }
}

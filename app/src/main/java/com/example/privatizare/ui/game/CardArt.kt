package com.example.privatizare.ui.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Încarcă fotografiile cartonașelor din `assets/carti/`.
 * Decodarea se face pe un fir secundar, iar ultimele imagini rămân în memorie (LRU),
 * ca să nu clipească la fiecare deschidere.
 */
object CardArt {
    private const val MAX_CACHED = 6
    private val cache = LinkedHashMap<String, ImageBitmap>(16, 0.75f, true)

    fun cached(path: String): ImageBitmap? = synchronized(cache) { cache[path] }

    suspend fun load(context: Context, path: String): ImageBitmap? {
        cached(path)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        } ?: return null
        synchronized(cache) {
            cache[path] = bmp
            while (cache.size > MAX_CACHED) {
                val oldest = cache.entries.iterator()
                oldest.next()
                oldest.remove()
            }
        }
        return bmp
    }

    /** Calea fotografiei cartonașului unei firme: titlu sau anexa [annex] (1..n). */
    fun actionPath(propertyId: String, annex: Int = 0): String =
        if (annex <= 0) "carti/act_${propertyId}_t.webp" else "carti/act_${propertyId}_a$annex.webp"

    /** Calea fotografiei unui cartonaș LOTO / TELEX, după id-ul din cards.json. */
    fun deckPath(id: String): String = "carti/$id.webp"

    /** Iconița tipărită pe cartonașul firmei, decupată din colțul titlului. */
    fun iconPath(propertyId: String): String = "carti/icon_$propertyId.webp"
}

/** Fotografia unui cartonaș, încărcată asincron. Null cât timp se descarcă (sau dacă lipsește). */
@Composable
fun rememberCardArt(path: String?): ImageBitmap? {
    val context = LocalContext.current
    var art by remember(path) { mutableStateOf(path?.let { CardArt.cached(it) }) }
    LaunchedEffect(path) {
        if (path == null) {
            art = null
            return@LaunchedEffect
        }
        art = CardArt.load(context, path)
    }
    return art
}

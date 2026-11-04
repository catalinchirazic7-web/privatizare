package com.example.privatizare.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.privatizare.R
import kotlin.math.roundToInt
import kotlin.math.sin

/** Bancnotele din joc, de la cea mai mare la cea mai mică (ECU), ca în tiraj. */
val BILL_DENOMINATIONS: List<Long> = listOf(2000L, 1000L, 500L, 100L, 50L)

/** Câte bancnote se desenează pe o grămadă (ca să nu fie prea înaltă). */
private const val MAX_LAYERS = 6

/**
 * Împarte o sumă în bancnote. Fiecare valoare primește o parte egală din sumă (ca toate cele cinci
 * să apară), iar restul se completează de la cea mai mare valoare. Restul sub 50 nu se desenează.
 */
fun billCounts(money: Long): Map<Long, Int> {
    val total = money.coerceAtLeast(0L)
    val counts = linkedMapOf<Long, Int>()
    var rest = total
    val share = total / BILL_DENOMINATIONS.size
    for (d in BILL_DENOMINATIONS) {
        val n = minOf(share / d, rest / d)
        if (n > 0) {
            counts[d] = n.toInt()
            rest -= n * d
        }
    }
    for (d in BILL_DENOMINATIONS) {
        val n = rest / d
        if (n > 0) {
            counts[d] = (counts[d] ?: 0) + n.toInt()
            rest -= n * d
        }
    }
    return counts
}

/**
 * Un zbor de bani: jucătorul uman a plătit [amount]. Ajung la adversarul din ([toFx], [toFy]),
 * sau, dacă [toBank], spre Bancă (în sus, spre ecran).
 */
data class MoneyFlight(
    val id: Int,
    val amount: Long,
    val toBank: Boolean,
    val toFx: Float,
    val toFy: Float,
)

/** Imaginea unei bancnote (fotografia reală din folderul „Bani”). */
private fun billRes(value: Long): Int = when (value) {
    2000L -> R.drawable.bill_2000
    1000L -> R.drawable.bill_1000
    500L -> R.drawable.bill_500
    100L -> R.drawable.bill_100
    else -> R.drawable.bill_50
}

/** Locul unei grămezi pe tablă, în pixeli: un dreptunghi care o include. */
private data class PileRect(val value: Long, val left: Float, val top: Float, val width: Float, val height: Float) {
    fun contains(p: Offset): Boolean = p.x in left..(left + width) && p.y in top..(top + height)
}

/** Mărimea tuturor bancnotelor pe tablă (aceeași pentru toate valorile). */
private const val BILL_SCALE = 1.60f

/** Ordinea grămezilor pe un rând, de la stânga la dreapta: 2000 în stânga, 100 și 1000 spre mijloc. */
private val ROW_ORDER: List<Long> = listOf(2000L, 500L, 50L, 100L, 1000L)

/** Poziția grămezilor: un rând în zona verde din mijlocul tablei, sub cartonașul violet. */
private fun pileRects(counts: Map<Long, Int>, w: Float, h: Float, aspect: (Long) -> Float): List<PileRect> {
    val rects = mutableListOf<PileRect>()
    ROW_ORDER.forEachIndexed { slot, value ->
        val n = counts[value] ?: return@forEachIndexed
        val billW = w * 0.05f * BILL_SCALE
        val billH = billW * aspect(value)
        val stepY = billH * 0.18f
        val layers = minOf(n, MAX_LAYERS)
        val cx = 0.20f + slot * 0.07f
        val cy = 0.74f
        val base = h * cy
        val top = base - billH - (layers - 1) * stepY
        val height = billH + (layers - 1) * stepY
        rects += PileRect(value, w * cx - billW / 2f, top, billW, height)
    }
    return rects
}

/**
 * Banii fizici ai jucătorului, pe tablă, cu fotografiile bancnotelor din folderul „Bani”.
 * La atingerea unei grămezi se deschide fotografia bancnotei respective.
 * Se desenează doar pentru jucătorul uman afișat.
 */
@Composable
fun CashStacks(money: Long, boardWidth: Dp, boardHeight: Dp, flight: MoneyFlight? = null) {
    val counts = billCounts(money)
    if (counts.isEmpty() && flight == null) return
    val bills: Map<Long, ImageBitmap> = mapOf(
        2000L to ImageBitmap.imageResource(R.drawable.bill_2000),
        1000L to ImageBitmap.imageResource(R.drawable.bill_1000),
        500L to ImageBitmap.imageResource(R.drawable.bill_500),
        100L to ImageBitmap.imageResource(R.drawable.bill_100),
        50L to ImageBitmap.imageResource(R.drawable.bill_50),
    )
    var shownBill by remember { mutableStateOf<Long?>(null) }
    val aspect: (Long) -> Float = { v -> bills.getValue(v).let { it.height.toFloat() / it.width } }
    // zborul banilor: 0 = pornesc de la teanc, 1 = au ajuns
    val progress = remember { Animatable(1f) }
    ForcedLaunchedEffect(flight?.id) {
        if (flight != null) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }

    Canvas(
        modifier = Modifier
            .size(boardWidth, boardHeight)
            .pointerInput(counts) {
                // consumăm atingerea doar pe grămezi; restul trece mai departe (zarul, casetele tablei)
                val area = size
                awaitPointerEventScope {
                    var pending: Long? = null
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.changedToDown()) {
                            val hit = pileRects(counts, area.width.toFloat(), area.height.toFloat(), aspect)
                                .firstOrNull { it.contains(change.position) }
                            if (hit != null) {
                                pending = hit.value
                                change.consume()
                            }
                        } else if (change.changedToUp() && pending != null) {
                            shownBill = pending
                            pending = null
                            change.consume()
                        }
                    }
                }
            },
    ) {
        pileRects(counts, size.width, size.height, aspect).forEach { rect ->
            val n = counts.getValue(rect.value)
            drawBillPile(bills.getValue(rect.value), minOf(n, MAX_LAYERS), rect)
        }
        val f = flight
        if (f != null && progress.value < 1f) {
            drawFlight(f, bills, progress.value, size.width, size.height)
        }
    }

    shownBill?.let { value ->
        Dialog(onDismissRequest = { shownBill = null }) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Bancnotă de $value ECU", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Image(
                        painter = painterResource(id = billRes(value)),
                        contentDescription = "Bancnotă de $value ECU",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { shownBill = null }) { Text("Închide") }
                }
            }
        }
    }
}

/**
 * Bancnotele care zboară: pornesc de la teancul jucătorului (stânga-jos) și ajung la adversar,
 * sau, la plata către Bancă, urcă spre ecran (dincolo de marginea tablei).
 */
private fun DrawScope.drawFlight(
    flight: MoneyFlight,
    bills: Map<Long, ImageBitmap>,
    t: Float,
    w: Float,
    h: Float,
) {
    val startX = 0.27f
    val startY = 0.68f
    val endX = if (flight.toBank) 0.5f else flight.toFx
    val endY = if (flight.toBank) -0.20f else flight.toFy
    // ease-in: bancnotele pleacă încet și accelerează spre destinație; se micșorează și se estompează la final
    val k = t * t
    val alpha = (1f - k * 0.6f).coerceIn(0f, 1f)
    var index = 0
    for ((value, n) in billCounts(flight.amount)) {
        val image = bills[value] ?: continue
        val shown = minOf(n, 3)
        repeat(shown) { i ->
            val offX = (i - 1) * 0.02f
            val fx = startX + (endX - startX) * k + offX + index * 0.012f
            val fy = startY + (endY - startY) * k + i * 0.01f
            val billW = w * 0.05f * BILL_SCALE * (1f - 0.4f * k)
            val billH = billW * image.height / image.width
            drawImage(
                image = image,
                dstOffset = IntOffset((fx * w - billW / 2f).roundToInt(), (fy * h - billH / 2f).roundToInt()),
                dstSize = IntSize(billW.roundToInt(), billH.roundToInt()),
                alpha = alpha,
            )
        }
        index++
    }
}

/** O grămadă de bancnote de aceeași valoare, una peste alta, cu un mic decalaj ca un teanc real. */
private fun DrawScope.drawBillPile(image: ImageBitmap, layers: Int, rect: PileRect) {
    if (layers <= 0) return
    val billW = rect.width
    val billH = billW * image.height / image.width
    val stepY = billH * 0.18f
    for (i in 0 until layers) {
        val jitter = sin(i * 1.7f) * billW * 0.04f
        val x = (rect.left + jitter).roundToInt()
        val y = (rect.top + rect.height - billH - i * stepY).roundToInt()
        drawImage(
            image = image,
            dstOffset = IntOffset(x, y),
            dstSize = IntSize(billW.roundToInt(), billH.roundToInt()),
        )
    }
}

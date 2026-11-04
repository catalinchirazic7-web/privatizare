package com.example.privatizare.ui.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.lerp
import com.example.privatizare.data.model.PawnColor
import kotlin.math.sin

private fun Color.lighten(f: Float) = lerp(this, Color.White, f)
private fun Color.darken(f: Float) = lerp(this, Color.Black, f)

/**
 * Pion 3D desenat în Canvas: cap sferic, corp conic simetric, degradeu pe lățime pentru volum,
 * reflexii de lumină și o bază mai închisă. Raport lățime:înălțime = 1:1.75.
 *
 * Pionul e dimensionat la cota părintelui căsuței: ~1.18x înălțimea căsuței (*0.045 boardHeight),
 * deci numele jucătorului se citește clar pe tablă, chiar și la zoom mic.
 */
@Composable
fun PawnShape(color: PawnColor, modifier: Modifier = Modifier, label: String? = null) {
    val base = Color(color.hex)
    // Pionul alb are nevoie de umbre mai puternice ca să nu se piardă pe tablă
    val shadeAmt = if (color == PawnColor.ALB_ARGINTIU) 0.45f else 0.38f
    val dark = base.darken(shadeAmt)
    val mid = base.darken(0.08f)
    val light = base.lighten(if (color == PawnColor.NEGRU) 0.35f else 0.40f)

    Canvas(modifier = modifier.aspectRatio(1f / 1.75f)) {
        val w = size.width
        val h = size.height
        val r = w * 0.27f                       // raza capului
        val cx = w / 2f
        val headCy = r + h * 0.02f
        val neckY = headCy + r * 0.80f
        val neckHalf = w * 0.15f
        val baseY = h * 0.92f
        val baseHalf = w * 0.47f

        // corpul (simetric), cu bază rotunjită
        val body = Path().apply {
            moveTo(cx - neckHalf, neckY)
            cubicTo(cx - neckHalf * 1.1f, h * 0.50f, cx - baseHalf, h * 0.62f, cx - baseHalf, baseY)
            cubicTo(cx - baseHalf, h * 1.02f, cx + baseHalf, h * 1.02f, cx + baseHalf, baseY)
            cubicTo(cx + baseHalf, h * 0.62f, cx + neckHalf * 1.1f, h * 0.50f, cx + neckHalf, neckY)
            close()
        }
        // volum: întuneric la margini, lumină puțin spre stânga
        drawPath(
            body,
            Brush.horizontalGradient(
                0f to dark, 0.22f to mid, 0.38f to light, 0.62f to mid, 1f to dark.darken(0.15f),
                startX = cx - baseHalf, endX = cx + baseHalf,
            ),
        )
        // întunecare spre bază și o linie fină de contur jos
        drawPath(
            body,
            Brush.verticalGradient(
                0f to Color.Transparent, 0.70f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.30f),
                startY = neckY, endY = h,
            ),
        )
        // reflexie alungită pe corp
        clipPath(body) {
            drawOval(
                Color.White.copy(alpha = if (color == PawnColor.NEGRU) 0.18f else 0.30f),
                topLeft = Offset(cx - baseHalf * 0.62f, h * 0.58f),
                size = Size(baseHalf * 0.24f, h * 0.26f),
            )
        }
        // cap sferic cu lumina din stânga-sus
        drawCircle(
            brush = Brush.radialGradient(
                0f to light.lighten(0.15f), 0.45f to base, 1f to dark,
                center = Offset(cx - r * 0.35f, headCy - r * 0.40f),
                radius = r * 1.75f,
            ),
            radius = r,
            center = Offset(cx, headCy),
        )
        // reflexia mică, strălucitoare, de pe cap
        drawOval(
            Color.White.copy(alpha = 0.75f),
            topLeft = Offset(cx - r * 0.55f, headCy - r * 0.62f),
            size = Size(r * 0.42f, r * 0.26f),
        )
        // numele jucătorului pe corp, vertical, de jos în sus
        if (!label.isNullOrBlank()) {
            val maxLen = (baseY - neckY) * 0.88f          // lungimea disponibilă pe corp
            val maxThick = w * 0.34f                      // grosimea textului (peste lățimea corpului)
            val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            val stroke = android.graphics.Paint(fill).apply {
                this.color = android.graphics.Color.BLACK
                style = android.graphics.Paint.Style.STROKE
            }
            var text: String = label
            var size = maxThick
            fill.textSize = size
            while (fill.measureText(text) > maxLen && size > maxThick * 0.55f) {
                size *= 0.92f
                fill.textSize = size
            }
            while (fill.measureText(text) > maxLen && text.length > 2) text = text.dropLast(1)
            stroke.textSize = fill.textSize
            stroke.strokeWidth = fill.textSize * 0.22f
            val centerY = (neckY + baseY) / 2f + h * 0.04f
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                n.save()
                n.rotate(-90f, cx, centerY)
                val baseline = centerY + fill.textSize * 0.35f
                n.drawText(text, cx, baseline, stroke)
                n.drawText(text, cx, baseline, fill)
                n.restore()
            }
        }
    }
}

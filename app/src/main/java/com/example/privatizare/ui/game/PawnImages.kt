package com.example.privatizare.ui.game

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import com.example.privatizare.data.model.PawnColor
import kotlin.math.max

/** Lățimea pionului față de înălțime (forma desenată din web). */
const val PAWN_ASPECT = 0.54f

private fun Color.lighten(f: Float) = lerp(this, Color.White, f)
private fun Color.darken(f: Float) = lerp(this, Color.Black, f)

/**
 * Pionul desenat simetric, ca în web (board.js, drawPawnShape): baza în partea de jos a
 * casetei, vârful la înălțime; corp conic cu degradeu pe lățime (luminos, culoarea, umbră),
 * cap rotund cu reflexie și numele jucătorului scris orizontal pe corp.
 */
@Composable
fun PawnShape(color: PawnColor, modifier: Modifier = Modifier, label: String? = null) {
    val hex = Color(color.hex)
    val dark = hex.darken(0.35f)
    val light = hex.lighten(0.3f)

    Canvas(modifier = modifier.aspectRatio(PAWN_ASPECT)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val stroke = max(1f, h * 0.018f)
        val grad = Brush.horizontalGradient(
            0f to light, 0.55f to hex, 1f to dark,
            startX = cx - 0.5f * w, endX = cx + 0.5f * w,
        )

        // corpul: baza lată în jos, gâtul îngust sus
        val body = Path().apply {
            moveTo(cx - 0.5f * w, h)
            lineTo(cx + 0.5f * w, h)
            cubicTo(cx + 0.5f * w, h - 0.3f * h, cx + 0.16f * w, h - 0.36f * h, cx + 0.16f * w, h - 0.6f * h)
            lineTo(cx - 0.16f * w, h - 0.6f * h)
            cubicTo(cx - 0.16f * w, h - 0.36f * h, cx - 0.5f * w, h - 0.3f * h, cx - 0.5f * w, h)
            close()
        }
        drawPath(body, grad)
        drawPath(body, dark, style = Stroke(width = stroke, join = StrokeJoin.Round))

        // numele jucătorului, orizontal, pe corp (se micșorează ca să încapă)
        if (!label.isNullOrBlank()) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val outline = Paint(fill).apply {
                this.color = android.graphics.Color.argb(153, 0, 0, 0)
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
            }
            var fs = h * 0.13f
            fill.textSize = fs
            while (fs > h * 0.04f && fill.measureText(label) > w * 0.62f) {
                fs -= h * 0.005f
                fill.textSize = fs
            }
            outline.textSize = fs
            outline.strokeWidth = max(1f, fs * 0.25f)
            val fm = fill.fontMetrics
            val baseline = h * 0.70f - (fm.ascent + fm.descent) / 2f
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(label, cx, baseline, outline)
                c.nativeCanvas.drawText(label, cx, baseline, fill)
            }
        }

        // capul rotund
        val headCy = h * 0.22f
        val headR = 0.27f * w
        drawCircle(grad, radius = headR, center = Offset(cx, headCy))
        drawCircle(dark, radius = headR, center = Offset(cx, headCy), style = Stroke(width = stroke))

        // reflexia de pe cap, în stânga-sus
        val ex = cx - 0.09f * w
        val ey = h * 0.14f
        rotate(degrees = -28.65f, pivot = Offset(ex, ey)) {
            drawOval(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(ex - 0.09f * w, ey - 0.05f * h),
                size = Size(0.18f * w, 0.10f * h),
            )
        }
    }
}

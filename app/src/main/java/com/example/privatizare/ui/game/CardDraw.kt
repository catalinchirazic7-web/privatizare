package com.example.privatizare.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.privatizare.R
import com.example.privatizare.logic.CardOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private val FaceInk = Color(0xFF3B2F20)
private val Gold = Color(0xFFFFD54F)
private val CARD_W = 380.dp
private val CARD_H = 200.dp

/** O bucată de confetti: poziția de start, viteza, balansul, rotația, forma și culoarea. */
private class Confetto(
    val x: Float, val delay: Float, val fall: Float, val sway: Float, val spin: Float,
    val round: Boolean, val size: Float, val color: Color,
)

private val ConfettiColors = listOf(
    Gold, Color(0xFFE53935), Color.White, Color(0xFF29B6F6), Color(0xFF81C784), Color(0xFFFFB300),
)

private fun makeConfetti(count: Int, seed: Int): List<Confetto> {
    val r = Random(seed)
    return List(count) {
        Confetto(
            x = r.nextFloat(), delay = r.nextFloat() * 0.35f, fall = 0.7f + r.nextFloat() * 0.6f,
            sway = 0.6f + r.nextFloat() * 1.6f, spin = (if (r.nextBoolean()) 1 else -1) * (0.8f + r.nextFloat() * 2.4f),
            round = r.nextInt(3) == 0, size = 6f + r.nextFloat() * 8f, color = ConfettiColors[r.nextInt(ConfettiColors.size)],
        )
    }
}

/** Plutește emoji-urile discret în colțuri, urcă puțin și dispar. */
private val Emojis = listOf("😆" to (-215 to 70), "🤭" to (215 to -70), "😂" to (215 to 80))

/**
 * Cartonașul tras din teanc: zboară spre centru cu spatele în sus (spring), se răstoarnă 3D și arată fața.
 * Câștig: confetti în Canvas + suma pulsează auriu. Pierdere: trepidație scurtă + emoji plutitoare.
 */
@Composable
fun CardDrawDialog(
    kind: String,
    text: String,
    onConfirm: () -> Unit,
    outcome: CardOutcome = CardOutcome.NEUTRAL,
    amount: Long? = null,
    destination: String? = null,
    /** Fotografia cartonașului real, din assets. Dacă lipsește, se desenează cartonașul colorat. */
    artPath: String? = null,
) {
    val isLoto = kind == "LOTO"
    val back = if (isLoto) R.drawable.deck_back_loto else R.drawable.deck_back_telex
    // LOTO: roșu spre roz, cu mai multe nuanțe; TELEX: albastru azur cu contur deschis. Textul e alb.
    val faceBrush = if (isLoto) {
        Brush.linearGradient(listOf(Color(0xFFC62828), Color(0xFFE53570), Color(0xFFFF6FA5), Color(0xFFE53570)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF0277BD), Color(0xFF039BE5), Color(0xFF4FC3F7), Color(0xFF039BE5)))
    }
    val rim = if (isLoto) FaceInk else Color(0xFFB3E5FC)

    val fly = remember { Animatable(0f) }      // 0 = în teanc, 1 = în centru
    val flip = remember { Animatable(180f) }   // 180 = spatele, 0 = fața
    val pop = remember { Animatable(1f) }      // revenire elastică la aterizare
    val shake = remember { Animatable(0f) }    // trepidație orizontală (pierdere)
    val confetti = remember { Animatable(0f) } // 0..1 pe 2,8 s (câștig)
    val emojis = remember { Animatable(0f) }   // 0..1 pe 2 s (pierdere)
    val fade = remember { Animatable(1f) }     // închidere lină
    val scope = rememberCoroutineScope()
    val landed = flip.value == 0f
    var closing by remember { mutableStateOf(false) }
    // Se închide la atingerea cartonașului, la „Am înțeles” sau singur după 10 secunde de la apariția feței
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch(MotionForce.context) { fade.animateTo(0f, tween(220)); onConfirm() }
        }
    }
    ForcedLaunchedEffect(landed) {
        if (landed) {
            delay(10_000)
            close()
        }
    }

    ForcedLaunchedEffect(Unit) {
        run { fly.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) }
    }
    ForcedLaunchedEffect(Unit) {
        run {
            delay(380)
            flip.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
            pop.snapTo(0.94f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }
    ForcedLaunchedEffect(landed) {
        if (!landed) return@ForcedLaunchedEffect
        run {
            when (outcome) {
                CardOutcome.GAIN -> while (true) {          // confetti repetat cât stă cartonașul deschis
                    confetti.snapTo(0f)
                    confetti.animateTo(1f, tween(4500, easing = LinearEasing))
                }
                CardOutcome.LOSS -> {
                    launch {
                        while (true) {                         // emoji-urile plutesc în valuri cât stă deschis
                            emojis.snapTo(0f)
                            emojis.animateTo(1f, tween(3200, easing = LinearEasing))
                        }
                    }
                    for (v in listOf(-16f, 16f, -12f, 12f, -7f, 7f, 0f)) shake.animateTo(v, tween(38))   // ~270 ms
                }
                CardOutcome.NEUTRAL -> Unit
            }
        }
    }
    // Pulsul sumei câștigate: buclă manuală, ca să nu depindă de animațiile de sistem
    val pulseAnim = remember { Animatable(1f) }
    ForcedLaunchedEffect(Unit) {
        run {
            while (true) {
                pulseAnim.animateTo(1.12f, tween(650))
                pulseAnim.animateTo(1f, tween(650))
            }
        }
    }
    val pulse = pulseAnim.value
    val density = LocalDensity.current
    val pieces = remember { makeConfetti(80, seed = text.hashCode()) }
    val art = rememberCardArt(artPath)
    // Pachetul LOTO e în stânga-sus a tablei, TELEX în dreapta-jos: cartea pornește din direcția lui
    val startX = if (isLoto) -260f else 260f
    val startY = if (isLoto) -90f else 110f

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(CARD_W, CARD_H)
                        .graphicsLayer {
                            val p = fly.value
                            translationX = (1f - p) * startX * density.density + shake.value * density.density
                            translationY = (1f - p) * startY * density.density
                            val s = (0.35f + 0.65f * p) * pop.value
                            scaleX = s
                            scaleY = s
                            rotationY = flip.value
                            cameraDistance = 14f * density.density
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .border(3.dp, rim, RoundedCornerShape(14.dp))
                        .clickable(enabled = landed && !closing, onClick = close),
                ) {
                    if (flip.value > 90f) {
                        Image(
                            painter = painterResource(id = back),
                            contentDescription = "Spatele cartonașului",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.size(CARD_W, CARD_H),
                        )
                    } else if (art != null) {
                        // Cartonașul real, fotografiat: fața lui exactă, cu efectul afișat peste
                        Box(
                            modifier = Modifier
                                .size(CARD_W, CARD_H)
                                .background(Color(0xFFF6EFDF)),
                        ) {
                            Image(
                                bitmap = art,
                                contentDescription = text,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (amount != null && outcome != CardOutcome.NEUTRAL) {
                                val gain = outcome == CardOutcome.GAIN
                                Text(
                                    text = (if (gain) "+" else "−") + amount + " ECU",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 19.sp,
                                    color = if (gain) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp)
                                        .background(Color(0xFFF6EFDF).copy(alpha = 0.93f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                        .graphicsLayer { if (gain) { scaleX = pulse; scaleY = pulse } },
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.size(CARD_W, CARD_H).background(faceBrush).padding(16.dp),
                        ) {
                            Text(kind, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color.White)
                            Spacer(Modifier.height(6.dp))
                            Text(text, fontFamily = FontFamily.Serif, fontSize = 18.sp, color = Color.White, textAlign = TextAlign.Start)
                            if (destination != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(destination, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFFFF59D))
                            }
                            if (amount != null && outcome != CardOutcome.NEUTRAL) {
                                Spacer(Modifier.height(6.dp))
                                val gain = outcome == CardOutcome.GAIN
                                Text(
                                    text = (if (gain) "+" else "−") + amount + " ECU",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 24.sp,
                                    color = if (gain) Gold else Color.White,
                                    style = TextStyle(
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = if (gain) Gold.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.6f),
                                            blurRadius = if (gain) 14f else 6f,
                                        ),
                                    ),
                                    modifier = Modifier.graphicsLayer {
                                        if (gain) { scaleX = pulse; scaleY = pulse }   // pulsul auriu al sumei câștigate
                                    },
                                )
                            }
                        }
                    }
                }
                // Cartonașul real nu conține linia beneficiarului: o arătăm sub el
                if (art != null && destination != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        destination,
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = close,
                    enabled = landed && !closing,
                ) { Text("Am înțeles") }
            }

            // Emoji-uri discrete de ironie, în colțuri, urcă și dispar (nu acoperă textul sau suma)
            if (outcome == CardOutcome.LOSS && landed) {
                Emojis.forEachIndexed { i, (emoji, pos) ->
                    val t = ((emojis.value * 2f - i * 0.25f)).coerceIn(0f, 1f)
                    val alpha = if (t < 0.15f) t / 0.15f else 1f - ((t - 0.15f) / 0.85f)
                    Text(
                        emoji, fontSize = 28.sp,
                        modifier = Modifier
                            .offset(x = pos.first.dp, y = (pos.second - 70 * t).dp)
                            .graphicsLayer { this.alpha = alpha.coerceIn(0f, 0.9f) },
                    )
                }
            }

            // Confetti peste cartonaș, doar la câștig
            if (outcome == CardOutcome.GAIN && landed) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = confetti.value
                    for (c in pieces) {
                        val local = ((t - c.delay) / (1f - c.delay)).coerceIn(0f, 1f)
                        if (local <= 0f) continue
                        val x = c.x * size.width + sin(local * c.sway * 2f * PI.toFloat()) * 36f
                        val y = -30f + local * c.fall * (size.height + 60f)
                        val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
                        val color = c.color.copy(alpha = alpha.coerceIn(0f, 1f))
                        rotate(degrees = local * c.spin * 360f, pivot = Offset(x, y)) {
                            if (c.round) drawCircle(color, radius = c.size / 2f, center = Offset(x, y))
                            else drawRect(color, topLeft = Offset(x - c.size / 2f, y - c.size / 3f), size = Size(c.size, c.size * 0.66f))
                        }
                    }
                }
            }
        }
    }
}

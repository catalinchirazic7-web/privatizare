package com.example.privatizare.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.privatizare.logic.rollDie

private val DieFace = Color(0xFFFDFCF6)
private val DieEdge = Color(0xFF1E1E1E)
private val DiePip = Color(0xFF161616)
private val DieGold = Color(0xFFFFC107)

/**
 * Zarul de pe tablă — **2D**, cât se poate de lizibil: pătrat alb cu colțuri rotunjite, contur gros
 * și puncte mari. Când e rândul tău pulsează cu auriu; la aruncare se scutură, își schimbă fețele
 * din ce în ce mai rar, apoi „bum" — sare puțin și se așază pe valoarea oficială.
 *
 * Ordinea e cea corectă pentru joc: zarul se oprește întâi, stă o clipă ca să fie citit,
 * abia apoi [onRolled] anunță motorul — deci pionul pornește după ce s-a văzut zarul.
 */
@Composable
fun BoardDie(
    enabled: Boolean,
    onRolled: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dieSize: Dp = 96.dp,
    remote: Boolean = false,
    resultKey: Int = 0,
    resultValue: Int? = null,
    /** Anunță când zarul începe/încetează să se rostogolească (pionii așteaptă oprirea lui). */
    onRollingChange: (Boolean) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    var face by remember { mutableIntStateOf(resultValue ?: 1) }
    var rolling by remember { mutableStateOf(false) }
    // Aruncarea pornită de degetul jucătorului local; separată de [rolling] ca o aruncare
    // venită de la robot sau de la adversar să nu declanșeze și fluxul local.
    var localRoll by remember { mutableStateOf(false) }
    var ignoreNextKey by remember { mutableStateOf(false) }
    var lastKey by remember { mutableStateOf(resultKey) }
    val latestValue by rememberUpdatedState(resultValue)

    val wobble = remember { Animatable(0f) }    // rotirea de scuturare
    val pop = remember { Animatable(1f) }       // săritura la aterizare
    val glow = remember { Animatable(0f) }      // invitația de a arunca
    val flash = remember { Animatable(0f) }     // fulgerul auriu după oprire
    // eticheta cu valoarea: se vede după aruncare, până îți vine din nou rândul
    val badgeAlpha by animateForcedFloat(
        target = if (enabled && !rolling) 0f else 1f,
        spec = tween(220),
    )

    LaunchedEffect(rolling) { onRollingChange(rolling) }

    // Pulsul invitației: conturul auriu „respiră" cât timp poți arunca
    ForcedLaunchedEffect(enabled, rolling) {
        if (!enabled || rolling) {
            glow.animateTo(0f, tween(200))
            return@ForcedLaunchedEffect
        }
        while (true) {
            glow.animateTo(1f, tween(700))
            glow.animateTo(0f, tween(700))
        }
    }

    /**
     * Arată rezultatul: o pauză scurtă (cât să „genereze"), apoi fața exactă — fără fluturare de
     * fețe, ca să nu existe dubiu ce s-a dat. La final un „bum" mic și valoarea scrisă alături.
     */
    suspend fun settleOn(value: Int) {
        delay(45)
        face = value
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pop.snapTo(0.88f)
        pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium))
        flash.snapTo(1f)
        flash.animateTo(0f, tween(420))
    }

    /** Cât timp se așteaptă rezultatul oficial (oaspete): conturul pulsează, fața rămâne pe loc. */
    suspend fun waitForResult() {
        coroutineScope {
            launch {
                while (true) {
                    glow.animateTo(1f, tween(240))
                    glow.animateTo(0f, tween(240))
                }
            }
        }
    }

    // --- Aruncarea inițiată de acest telefon (atingere pe zar) ---
    ForcedLaunchedEffect(localRoll) {
        if (!localRoll) return@ForcedLaunchedEffect
        rolling = true
        if (remote) {
            val startKey = lastKey
            onRolled(1)
            var waited = 0
            coroutineScope {
                val spin = launch { waitForResult() }
                while (lastKey == startKey && waited < 9000) {
                    delay(50); waited += 50
                }
                spin.cancel()
            }
            settleOn(latestValue ?: face)
        } else {
            val final = rollDie()
            settleOn(final)
            delay(520)                      // o clipă ca să fie citit zarul
            onRolled(final)                 // abia acum pornește și pionul
            ignoreNextKey = true
        }
        rolling = false
        localRoll = false
    }

    // --- Aruncarea oficială a altcuiva (robot / adversar online) ---
    ForcedLaunchedEffect(resultKey) {
        if (resultKey == lastKey) return@ForcedLaunchedEffect
        lastKey = resultKey
        if (ignoreNextKey) {
            ignoreNextKey = false
            return@ForcedLaunchedEffect
        }
        if (rolling) return@ForcedLaunchedEffect
        val v = latestValue ?: return@ForcedLaunchedEffect
        rolling = true
        settleOn(v)
        delay(140)
        rolling = false
    }

    val canTap = enabled && !rolling

    Box(
        modifier = modifier
            .size(dieSize)
            .graphicsLayer {
                val s = if (canTap) 1f + 0.045f * glow.value else 1f
                scaleX = s * pop.value
                scaleY = s * pop.value
                rotationZ = wobble.value
            }
            .clickable(enabled = canTap) { localRoll = true },
        contentAlignment = Alignment.Center,
    ) {
        // umbra de sub zar, ca să se desprindă de tablă
        Canvas(modifier = Modifier.size(dieSize)) {
            drawOval(
                color = Color.Black.copy(alpha = 0.30f),
                topLeft = Offset(size.width * 0.16f, size.height * 0.80f),
                size = Size(size.width * 0.68f, size.height * 0.16f),
            )
        }
        Box(
            modifier = Modifier
                .size(dieSize)
                .graphicsLayer { translationY = -size.height * 0.06f }
                .background(DieFace, RoundedCornerShape(dieSize * 0.20f))
                .border(
                    width = 3.dp,
                    color = when {
                        flash.value > 0.02f -> DieGold.copy(alpha = flash.value)
                        canTap -> DieGold
                        else -> DieEdge
                    },
                    shape = RoundedCornerShape(dieSize * 0.20f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(dieSize)) {
                val s = size.minDimension
                val radius = s * 0.085f
                for (pip in pipFractions(face)) {
                    // umbra punctului, ca să pară adâncit în zar
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.18f),
                        radius = radius * 1.06f,
                        center = Offset(s * pip.first + radius * 0.16f, s * pip.second + radius * 0.16f),
                    )
                    drawCircle(color = DiePip, radius = radius, center = Offset(s * pip.first, s * pip.second))
                }
            }
        }
        // Valoarea aruncării, desenată ultima, ca să fie deasupra zarului
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 14.dp, y = (-12).dp)
                .size(32.dp)
                .graphicsLayer {
                    alpha = badgeAlpha
                    val sc = 0.85f + 0.15f * badgeAlpha
                    scaleX = sc
                    scaleY = sc
                }
                .background(DieGold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$face", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
    }
}

private const val LEFT = 0.28f
private const val MID = 0.5f
private const val RIGHT = 0.72f

/** Poziții (x, y) în fracții ale laturii, pentru fiecare față a zarului. */
private fun pipFractions(value: Int): List<Pair<Float, Float>> = when (value) {
    1 -> listOf(MID to MID)
    2 -> listOf(LEFT to LEFT, RIGHT to RIGHT)
    3 -> listOf(LEFT to LEFT, MID to MID, RIGHT to RIGHT)
    4 -> listOf(LEFT to LEFT, RIGHT to LEFT, LEFT to RIGHT, RIGHT to RIGHT)
    5 -> listOf(LEFT to LEFT, RIGHT to LEFT, MID to MID, LEFT to RIGHT, RIGHT to RIGHT)
    else -> listOf(LEFT to LEFT, RIGHT to LEFT, LEFT to MID, RIGHT to MID, LEFT to RIGHT, RIGHT to RIGHT)
}

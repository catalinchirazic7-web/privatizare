package com.example.privatizare.ui.game

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Ca [LaunchedEffect], dar corpul rulează în contextul [MotionForce.context]:
 * animațiile din el au durata 1x când e forțat.
 */
@Composable
fun ForcedLaunchedEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    LaunchedEffect(*keys) {
        withContext(MotionForce.context) { block() }
    }
}

/**
 * Buclă infinită (ca rememberInfiniteTransition, de la [from] la [to] în [durationMs] ms, repetat).
 * Când e forțat, rulează în contextul 1x; altfel folosește animația normală.
 */
@Composable
fun rememberLoopFloat(from: Float, to: Float, durationMs: Int, label: String): State<Float> {
    if (!MotionForce.forced) {
        val transition = rememberInfiniteTransition(label = label)
        return transition.animateFloat(
            initialValue = from,
            targetValue = to,
            animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
            label = label,
        )
    }
    val value = remember { Animatable(from) }
    ForcedLaunchedEffect(Unit) {
        while (true) {
            value.snapTo(from)
            value.animateTo(to, tween(durationMs, easing = LinearEasing))
        }
    }
    return value.asState()
}

/** Ca [animateFloatAsState], dar în contextul 1x când e forțat. */
@Composable
fun animateForcedFloat(target: Float, spec: AnimationSpec<Float>): State<Float> {
    if (!MotionForce.forced) return animateFloatAsState(target, spec, label = "forced")
    val value = remember { Animatable(target) }
    ForcedLaunchedEffect(target) { value.animateTo(target, spec) }
    return value.asState()
}

/**
 * Animațiile jocului la 1x, chiar dacă telefonul/tableta are animațiile dezactivate sau micșorate.
 *
 * Forțarea se aplică doar dacă opțiunile pentru dezvoltatori sunt pornite și scara de animație din sistem
 * e diferită de 1. Altfel animațiile rulează exact ca până acum.
 */
object MotionForce {

    /** Factor fix 1x: Compose folosește acest factor în locul celui din sistem. */
    private object OneX : MotionDurationScale {
        override val scaleFactor: Float = 1f
    }

    /** true = animațiile jocului sunt forțate la 1x. */
    @Volatile
    var forced: Boolean = false
        private set

    /** Contextul de corutină pentru animațiile jocului (1x dacă e forțat, altfel contextul normal). */
    val context: CoroutineContext
        get() = if (forced) OneX else EmptyCoroutineContext

    /** Recitește setările din sistem; se apelează la pornirea și la revenirea în aplicație. */
    fun refresh(context: Context) {
        forced = runCatching {
            val resolver = context.contentResolver
            val devOn = Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
            val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            devOn && scale != 1f
        }.getOrDefault(false)
    }
}

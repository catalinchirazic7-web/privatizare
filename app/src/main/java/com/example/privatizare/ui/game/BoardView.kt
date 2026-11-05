package com.example.privatizare.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.privatizare.R
import com.example.privatizare.data.GameRepository
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.data.model.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Raportul lățime:înălțime al celor două fotografii de tablă (1600 px pe lat). */
const val BOARD_ASPECT_OLD = 1600f / 1131f
const val BOARD_ASPECT_NEW = 1600f / 1073f

/**
 * Filtrul tablei din web (saturate 1.6, contrast 1.06, brightness 1.06): tabla e mai vie.
 * Coeficienții de luminanță Rec.709 ca în CSS; contrastul și luminozitatea se compun într-o singură scară.
 */
private val BOARD_COLOR_MATRIX: ColorMatrix by lazy {
    val s = 1.6f
    val r = 0.2126f; val g = 0.7152f; val b = 0.0722f
    val sat = floatArrayOf(
        r + (1 - r) * s, g - g * s, b - b * s,
        r - r * s, g + (1 - g) * s, b - b * s,
        r - r * s, g - g * s, b + (1 - b) * s,
    )
    val k = 1.06f * 1.06f                      // contrast × luminozitate
    val offset = (0.5f - 0.5f * 1.06f) * 1.06f * 255f   // contrastul ridică în jurul gri-ului, în unități 0..255
    ColorMatrix(floatArrayOf(
        k * sat[0], k * sat[1], k * sat[2], 0f, offset,
        k * sat[3], k * sat[4], k * sat[5], 0f, offset,
        k * sat[6], k * sat[7], k * sat[8], 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    ))
}

/** Scara și deplasarea tablei după un gest de zoom. */
data class ZoomState(val scale: Float, val offset: Offset)

/** Limitează deplasarea ca tabla scalată să acopere mereu zona vizibilă. */
fun clampOffset(offset: Offset, scale: Float, viewW: Float, viewH: Float): Offset =
    Offset(
        offset.x.coerceIn(viewW * (1f - scale), 0f),
        offset.y.coerceIn(viewH * (1f - scale), 0f),
    )

/** Aplică un gest de ciupire și tragere. Punctul de sub degete ([centroid]) rămâne pe loc la zoom.
 * Scara e între [minScale] și [maxScale].
 */
fun applyZoomGesture(
    state: ZoomState,
    centroid: Offset,
    pan: Offset,
    gestureZoom: Float,
    viewW: Float,
    viewH: Float,
    minScale: Float = 1f,
    maxScale: Float = 4f,
): ZoomState {
    val newScale = (state.scale * gestureZoom).coerceIn(minScale, maxScale)
    val ratio = newScale / state.scale
    val moved = centroid - (centroid - state.offset) * ratio + pan
    return ZoomState(newScale, clampOffset(moved, newScale, viewW, viewH))
}

/**
 * Tabla de joc cu pioni. Zoom cu ciupirea degetelor (1x-4x) și deplasare prin tragere, oriunde pe ecran.
 * Când [presetNonce] se schimbă, tabla trece la [presetZoom] (1x sau 2x) și se centrează pe pionul de la [focusIndex].
 *
 * [tilt] înclină tabla în perspectivă (efect de masă de joc văzută de sus, ușor în unghi).
 * [highlightTile] e căsuța pe care trebuie să cadă privirea acum: primește un inel pulsatoriu.
 */
@Composable
fun BoardView(
    players: List<Player>,
    positions: List<Pair<Float, Float>>,
    propertyTiles: Map<String, Int>,
    modifier: Modifier = Modifier,
    presetZoom: Float = 1f,
    presetNonce: Int = 0,
    focusIndex: Int? = null,
    decks: Map<String, GameRepository.DeckRect> = emptyMap(),
    /** true: tabla se întinde pe tot spațiul primit (umple ecranul); false: păstrează proporțiile imaginii. */
    stretch: Boolean = false,
    /** Pachetul din care jucătorul local trebuie să tragă ('loto' / 'telex'): pulsează și scrie TRAGE. */
    drawKind: String? = null,
    onDeckTap: () -> Unit = {},
    /** True cât zarul se rostogolește: pionii nu se mișcă până se oprește. */
    dieBusy: () -> Boolean = { false },
    /** True cât cel puțin un pion se mută de pe o căsuță pe alta. */
    onPawnsBusy: (Boolean) -> Unit = {},
    /** Jucătorul la rând: pionul lui respiră ușor, ca să se vadă cui îi aparține tura. */
    activePlayerId: String? = null,
    /** Căsuța evidențiată acum (aterizarea curentă). */
    highlightTile: Int? = null,
    highlightColor: Color = Color(0xFFFFD54F),
    /** Coordonatele săgeții de START (poziția -1 a pionilor, înainte de primul pas). */
    startMarker: Pair<Float, Float> = 0.125f to 0.908f,
    /** Atingerea unei căsuțe de pe tablă: deschide fișa acțiunii respective. */
    onTileTap: (Int) -> Unit = {},
    /** Înclinare 3D a tablei. */
    tilt: Boolean = true,
    /** Fotografia tablei (veche sau nouă) și raportul ei. */
    boardImage: Int = R.drawable.tabla_privatizare,
    boardAspect: Float = BOARD_ASPECT_OLD,
    /** Conținut desenat pe tablă (zarul), poziționat cu fracții din dimensiunea tablei. */
    overlay: @Composable (boardWidth: Dp, boardHeight: Dp) -> Unit = { _, _ -> },
    /** Căsuța RECHEMAT URGENT (unde pionul merge pas cu pas înainte să fie trimis la START); -1 = nu există. */
    rechematTile: Int = -1,
    /** Id-urile pionilor aflați în mișcare: banii unui jucător care urcă apar abia la sosire. */
    onMovingIds: (Set<String>) -> Unit = {},
) {
    BoxWithConstraints(
        modifier = (if (stretch) modifier else modifier.aspectRatio(boardAspect)).clipToBounds(),
    ) {
        val viewW = maxWidth
        val viewH = maxHeight
        val localDensity = LocalDensity.current
        val viewWPx = with(localDensity) { viewW.toPx() }
        val viewHPx = with(localDensity) { viewH.toPx() }
        var zoomState by remember { mutableStateOf(ZoomState(1f, Offset.Zero)) }
        // pionii aflați în mișcare: ofertele, plățile și sfârșitul turei așteaptă să ajungă pe căsuță
        val movingPawns = remember { mutableStateMapOf<String, Boolean>() }
        val anyMoving = movingPawns.isNotEmpty()
        LaunchedEffect(anyMoving) { onPawnsBusy(anyMoving) }
        val movingKeys = movingPawns.keys.toSet()
        LaunchedEffect(movingKeys) { onMovingIds(movingKeys) }

        LaunchedEffect(presetNonce) {
            val f = focusIndex?.let { positions.getOrNull(it) }
            val offset = if (presetZoom > 1f && f != null) {
                clampOffset(
                    Offset(viewWPx / 2 - f.first * viewWPx * presetZoom, viewHPx / 2 - f.second * viewHPx * presetZoom),
                    presetZoom, viewWPx, viewHPx,
                )
            } else {
                Offset.Zero
            }
            zoomState = ZoomState(presetZoom, offset)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewWPx, viewHPx) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        zoomState = applyZoomGesture(zoomState, centroid, pan, gestureZoom, viewWPx, viewHPx)
                    }
                }
                .graphicsLayer {
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offset.x
                    translationY = zoomState.offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            // Tot ce urmează e desenat în planul tablei; înclinarea le mișcă împreună,
            // ca pionii să nu „plutească" peste o tablă rotită.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(positions) {
                        // atingerea unei căsuțe deschide fișa acțiunii; cea mai apropiată câștigă
                        detectTapGestures { offset ->
                            if (positions.isEmpty()) return@detectTapGestures
                            val fx = offset.x / size.width
                            val fy = offset.y / size.height
                            var best = -1
                            var bestDist = 1f
                            positions.forEachIndexed { i, p ->
                                val dx = (p.first - fx) / 0.034f
                                val dy = (p.second - fy) / 0.045f
                                val d = dx * dx + dy * dy
                                if (d < bestDist) {
                                    bestDist = d
                                    best = i
                                }
                            }
                            if (best >= 0) onTileTap(best)
                        }
                    }
                    .graphicsLayer {
                        if (tilt) {
                            // perspectivă ușoară: tabla pare așezată pe masă, văzută de sus
                            rotationX = 8f
                            scaleX = 0.93f
                            scaleY = 0.93f
                            translationY = -size.height * 0.022f
                        }
                    },
            ) {
                Image(
                    painter = painterResource(id = boardImage),
                    contentDescription = "Tabla de joc",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.colorMatrix(BOARD_COLOR_MATRIX),
                    modifier = Modifier.fillMaxSize(),
                )
                decks["loto"]?.let { DeckStack(it, R.drawable.deck_back_loto, "Pachet LOTO", viewW, viewH, drawKind == "loto", onDeckTap) }
                decks["telex"]?.let { DeckStack(it, R.drawable.deck_back_telex, "Pachet TELEX", viewW, viewH, drawKind == "telex", onDeckTap) }
                highlightTile?.let { idx ->
                    positions.getOrNull(idx)?.let { pos ->
                        TileHighlight(pos, viewW, viewH, highlightColor)
                    }
                }
                propertyMarkers(players, propertyTiles).forEach { m ->
                    PropertyMarkerView(marker = m, position = positions[m.tileIndex], boardWidth = viewW, boardHeight = viewH)
                }
                overlay(viewW, viewH)

                // Pionii de pe aceeași căsuță se împart locul în cerc, ca să rămână toți vizibili
                val byTile = players.groupBy { it.position }
                players.forEach { p ->
                    val mates = byTile[p.position].orEmpty()
                    val idx = mates.indexOfFirst { it.id == p.id }.coerceAtLeast(0)
                    val slot = clusterSlot(idx, mates.size)
                    PawnView(
                        player = p,
                        positions = positions,
                        clusterDx = slot.first,
                        clusterDy = slot.second,
                        sizeScale = slot.third,
                        boardWidth = viewW,
                        boardHeight = viewH,
                        active = p.id == activePlayerId,
                        startMarker = startMarker,
                        dieBusy = dieBusy,
                        onMoving = { moving -> if (moving) movingPawns[p.id] = true else movingPawns.remove(p.id) },
                        rechematTile = rechematTile,
                    )
                }
            }
        }
    }
}

/**
 * Așezarea pionilor pe aceeași căsuță: (decalaj x, decalaj y, scară), în fracții din tablă.
 * Deplasările țin cont de mărimea pionului, ca cei doi-trei pioni de pe o casă să nu se suprapună.
 */
private fun clusterSlot(index: Int, count: Int): Triple<Float, Float, Float> = when {
    count <= 1 -> Triple(0f, 0f, 1f)
    count == 2 -> Triple(if (index == 0) -0.028f else 0.028f, 0f, 0.86f)
    count == 3 -> when (index) {
        0 -> Triple(-0.025f, 0.008f, 0.80f)
        1 -> Triple(0.025f, 0.008f, 0.80f)
        else -> Triple(0f, -0.022f, 0.80f)
    }
    else -> when (index) {
        0 -> Triple(-0.024f, 0.009f, 0.76f)
        1 -> Triple(0.024f, 0.009f, 0.76f)
        2 -> Triple(-0.024f, -0.019f, 0.76f)
        else -> Triple(0.024f, -0.019f, 0.76f)
    }
}

/**
 * Pionul unui jucător. Se deplasează continuu, căsuță cu căsuță, cu o săritură pe fiecare pas
 * (arc, alungire la zbor, turtire la aterizare) — nu mai „sare" dintr-o poziție în alta.
 */
@Composable
private fun PawnView(
    player: Player,
    positions: List<Pair<Float, Float>>,
    clusterDx: Float,
    clusterDy: Float,
    sizeScale: Float,
    boardWidth: Dp,
    boardHeight: Dp,
    active: Boolean,
    startMarker: Pair<Float, Float>,
    dieBusy: () -> Boolean,
    onMoving: (Boolean) -> Unit,
    rechematTile: Int = -1,
) {
    val total = positions.size
    if (total == 0) return

    // poziția pe traseu, ca număr fracționar de căsuțe (poate trece peste 37 -> 0 fără salt)
    val travel = remember(player.id) { Animatable(player.position.toFloat()) }
    val land = remember(player.id) { Animatable(0f) }

    DisposableEffect(player.id) { onDispose { onMoving(false) } }
    ForcedLaunchedEffect(player.position) {
        val target = player.position
        val start = travel.value.roundToInt()
        if (start == target && travel.value == target.toFloat()) return@ForcedLaunchedEffect
        onMoving(true)
        try {
            // pionul se mută abia după ce zarul s-a oprit
            delay(90)
            withTimeoutOrNull(5000) { snapshotFlow { dieBusy() }.first { !it } }
            val forward = (target - start + total) % total
            val backward = (start - target + total) % total
            when {
                // RECHEMAT URGENT: pionul merge pas cu pas, pe drumul zarului, până la căsuța RECHEMAT
                // (adversarii o văd), apoi e trimis la START. Fără bonus, ca în web.
                target < 0 && rechematTile >= 0 -> {
                    val toRechemat = (rechematTile - start + total) % total
                    for (i in 1..toRechemat) {
                        GameSound.step()
                        travel.animateTo((start + i).toFloat(), tween(WALK_MS, easing = LinearEasing))
                    }
                    travel.snapTo(target.toFloat())
                }
                forward in 1..12 -> for (i in 1..forward) {
                    GameSound.step()
                    travel.animateTo((start + i).toFloat(), tween(STEP_MS, easing = LinearEasing))
                }
                backward in 1..6 -> for (i in 1..backward) {
                    GameSound.step()
                    travel.animateTo((start - i).toFloat(), tween(STEP_MS, easing = LinearEasing))
                }
                else -> travel.snapTo(target.toFloat())   // salt (ex. RECHEMAT URGENT)
            }
            // „pop" la aterizare: pionul se turtește o clipă și revine
            land.animateTo(1f, tween(70))
            land.animateTo(0f, tween(230))
            travel.snapTo(target.toFloat())
            delay(180)   // o scurtă pauză pe căsuța de sosire, înainte de ofertă / plată / cartonaș
        } finally {
            onMoving(false)
        }
    }

    // respirația pionului activ: mișcare fină, ca să se vadă al cui e rândul.
    // Se citește abia în faza de desenare, ca să nu recompună pionul la fiecare cadru.
    val phase = rememberLoopFloat(0f, (2 * PI).toFloat(), 2600, "phase")

    // Pionul e dimensionat ca părintele căsuței pe care stă: tile ≈ 0.045 stângă boardHeight,
    // iar bonusul de vizualizare e ~2.36x, deci numele jucătorului se citește de pe tablă,
    // chiar și la zoom mic.
    val tileH = boardHeight * 0.045f * sizeScale
    // Aceleași proporții ca în web: lățimea pionului e 0.54 din înălțime (PAWN_ASPECT)
    val pawnH = tileH * 2.36f
    val pawnW = pawnH * PAWN_ASPECT
    val boxW = pawnW * 1.42f
    val shadowH = boardHeight * 0.013f
    val boxH = pawnH + shadowH

    Box(
        modifier = Modifier
            .size(boxW, boxH)
            .graphicsLayer {
                val v = travel.value
                val fx0: Float
                val fy0: Float
                if (v < 0f) {
                    // încă pe săgeata de START: primul pas pornește de aici spre prima căsuță
                    val t = (v + 1f).coerceIn(0f, 1f)
                    fx0 = startMarker.first + (positions[0].first - startMarker.first) * t
                    fy0 = startMarker.second + (positions[0].second - startMarker.second) * t
                } else {
                    val base = floor(v).toInt()
                    val frac = v - base
                    val i0 = ((base % total) + total) % total
                    val i1 = (i0 + 1) % total
                    val p0 = positions[i0]
                    val p1 = positions[i1]
                    fx0 = p0.first + (p1.first - p0.first) * frac
                    fy0 = p0.second + (p1.second - p0.second) * frac
                }
                val fx = fx0 + clusterDx
                val fy = fy0 + clusterDy
                // baza pionului stă în cercul căsuței, puțin sub centru (ca un pion pe masă)
                val baseX = boardWidth.toPx() * fx
                val baseY = boardHeight.toPx() * (fy + 0.008f)
                translationX = baseX - size.width / 2f
                // baza desenată a pionului e la pawnH - shadowH/3 de la vârful cutiei
                translationY = baseY - (pawnH.toPx() - shadowH.toPx() / 3f)
            },
    ) {
        // Săritura: derivată din fracțiunea dintre căsuțe, deci un arc pe fiecare pas.
        // Citirea se face în faza de desenare (nu în compoziție), ca animația să fie fluidă.
        val hopNow = {
            val v = travel.value
            sin(PI * (v - floor(v)).toFloat()).toFloat().coerceIn(0f, 1f)
        }

        // umbra rămâne pe tablă, se micșorează când pionul e sus
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = pawnW * 1.25f, height = shadowH)
                .graphicsLayer {
                    val hop = hopNow()
                    scaleX = 1f - 0.2f * hop
                    scaleY = 1f - 0.25f * hop
                    alpha = max(0.2f, 1f - 0.35f * hop)
                }
                .background(
                    Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
                    CircleShape,
                ),
        )
        PawnShape(
            color = player.color,
            modifier = Modifier
                .align(Alignment.BottomCenter)   // centrat pe căsuță, ca umbra de sub el
                .size(pawnW, pawnH)
                .padding(bottom = shadowH / 3)
                .graphicsLayer {
                    val hop = hopNow()
                    val squash = land.value
                    val bob = if (active) sin(phase.value) * 0.0035f else 0f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationY = -boardHeight.toPx() * (0.030f * hop + bob)
                    // alungire la zbor, turtire la aterizare (ca în web)
                    scaleY = (1f + 0.06f * hop) * (1f - 0.1f * squash)
                    scaleX = (1f - 0.03f * hop) * (1f + 0.07f * squash)
                },
            label = player.name,
        )
    }
}

/** Inelul care marchează căsuța pe care a aterizat pionul: puls fin, în culoarea jucătorului. */
@Composable
private fun TileHighlight(pos: Pair<Float, Float>, boardWidth: Dp, boardHeight: Dp, color: Color) {
    val t by rememberLoopFloat(0f, 1f, 1100, "pulse")
    Canvas(
        modifier = Modifier
            .size(boardWidth * 0.11f, boardHeight * 0.14f)
            .graphicsLayer {
                translationX = boardWidth.toPx() * pos.first - size.width / 2f
                translationY = boardHeight.toPx() * pos.second - size.height / 2f
            },
    ) {
        val r = this.size.minDimension / 2f
        val breathe = 0.85f + 0.15f * sin(t * 2 * PI.toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = 0.38f * (1f - t * 0.6f)),
                0.7f to color.copy(alpha = 0.18f * (1f - t * 0.6f)),
                1f to Color.Transparent,
                center = center,
                radius = r * breathe,
            ),
            radius = r * breathe,
            center = center,
        )
        drawCircle(
            color = color.copy(alpha = 0.85f * (1f - t * 0.5f)),
            radius = r * 0.82f * breathe,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.09f),
        )
    }
}

/** Straturile și decalajul teancului 3D, ca în web (board.js: DECK_LAYERS, DECK_OFFSET). */
private const val DECK_LAYERS = 12
private const val DECK_OFFSET = 0.24f

/**
 * Teancul de cartonașe pus peste caseta lui de pe tablă, ca în web (board.js, drawDecks):
 * 12 straturi decalate în sus-dreapta, cu fețe laterale și umbră, lumină pe cartonașul de sus,
 * pulsare cât e rândul jucătorului să tragă, și eticheta TRAGE.
 */
@Composable
private fun DeckStack(
    rect: GameRepository.DeckRect,
    drawable: Int,
    description: String,
    boardWidth: Dp,
    boardHeight: Dp,
    active: Boolean = false,
    onTap: () -> Unit = {},
) {
    val image = ImageBitmap.imageResource(id = drawable)
    val transition = rememberInfiniteTransition(label = "teanc")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "teancFaza",
    )
    val pulse = if (active) 1f + 0.07f * (0.5f - 0.5f * cos(2f * PI.toFloat() * phase)) else 1f
    val w = boardWidth * rect.w
    val h = boardHeight * rect.h
    Canvas(
        modifier = Modifier
            .offset(x = boardWidth * rect.x, y = boardHeight * rect.y)
            .size(w, h)
            .semantics { contentDescription = description }
            .clickable(enabled = active, onClick = onTap),
    ) {
        val bh = boardHeight.toPx()
        scale(pulse, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawDeck(image, size.width, size.height, bh, active)
        }
    }
}

/** Desenează teancul în spațiul casetei [W]x[H]; [bh] = înălțimea tablei (pentru mărimile de text). */
private fun DrawScope.drawDeck(image: ImageBitmap, W: Float, H: Float, bh: Float, active: Boolean) {
    val off = min(W, H) * DECK_OFFSET
    val step = off / (DECK_LAYERS - 1)
    fun cardAt(i: Int): Offset {
        val k = DECK_LAYERS - 1 - i          // 0 pentru cartonașul de sus
        return Offset(k * step, -k * step)
    }
    fun shadowPaint(blur: Float, dx: Float, dy: Float, alpha: Int) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        setShadowLayer(blur, dx, dy, android.graphics.Color.argb(alpha, 0, 0, 0))
    }

    drawIntoCanvas { c ->
        val n = c.nativeCanvas
        // umbra teancului pe masă și marginile vizibile (sus și dreapta)
        val edges = shadowPaint(bh * 0.06f, bh * 0.03f, bh * 0.04f, 140)
        n.drawPath(polyPath(listOf(Offset(0f, 0f), Offset(off, -off), Offset(off + W, -off), Offset(W, 0f))), edges.apply { color = android.graphics.Color.rgb(0xbd, 0xb2, 0x92) })
        n.drawPath(polyPath(listOf(Offset(W, 0f), Offset(off + W, -off), Offset(off + W, H - off), Offset(W, H))), edges.apply { color = android.graphics.Color.rgb(0xe2, 0xd9, 0xbf) })
    }

    // cartonașele, din spate spre față
    for (i in 0 until DECK_LAYERS) {
        val at = cardAt(i)
        val top = i == DECK_LAYERS - 1
        drawIntoCanvas { c ->
            // umbra cartonașului: un dreptunghi negru sub el, vizibil doar ca umbră
            c.nativeCanvas.drawRect(at.x, at.y, at.x + W, at.y + H, shadowPaint(bh * 0.008f, bh * 0.004f, bh * 0.006f, 115))
        }
        drawImage(
            image = image,
            dstOffset = IntOffset(at.x.roundToInt(), at.y.roundToInt()),
            dstSize = IntSize(W.roundToInt(), H.roundToInt()),
        )
        drawRect(
            color = if (top && active) Color(0xFFF0D20E) else Color(0x99281E14),
            topLeft = at,
            size = Size(W, H),
            style = Stroke(width = if (top && active) max(2f, bh * 0.004f) else max(1f, bh * 0.0012f)),
        )
    }

    // lumină pe cartonașul de sus
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.28f),
            0.45f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.18f),
            start = Offset(0f, 0f),
            end = Offset(W, H),
        ),
        size = Size(W, H),
    )

    if (active) {
        // eticheta TRAGE, în centrul pachetului
        val fs = max(12f, bh * 0.034f)
        drawIntoCanvas { c ->
            val n = c.nativeCanvas
            val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = fs
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val tw = text.measureText("TRAGE") + fs
            val th = fs * 1.6f
            val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(166, 0, 0, 0) }
            n.drawRoundRect(W / 2 - tw / 2, H / 2 - th / 2, W / 2 + tw / 2, H / 2 + th / 2, fs * 0.4f, fs * 0.4f, bg)
            val fm = text.fontMetrics
            n.drawText("TRAGE", W / 2, H / 2 - (fm.ascent + fm.descent) / 2f, text)
        }
    }
}

/** Un contur poligonal, ca un path Android (pentru fețele laterale ale teancului). */
private fun polyPath(points: List<Offset>): android.graphics.Path = android.graphics.Path().apply {
    moveTo(points[0].x, points[0].y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

/** Marcaj de proprietate pe tablă: o căsuță deținută, în culoarea proprietarului.
 * Nu conține numărul de anexe: anexele nu sunt publice, conform regulamentului.
 */
data class PropertyMarker(val propertyId: String, val tileIndex: Int, val color: PawnColor)

/** Marcajele de pe tablă. Căsuțele libere nu au marcaj. Funcție pură, testabilă fără interfață. */
fun propertyMarkers(players: List<Player>, propertyTiles: Map<String, Int>): List<PropertyMarker> =
    players.flatMap { p ->
        p.ownedProperties.keys.mapNotNull { propId ->
            propertyTiles[propId]?.let { PropertyMarker(propId, it, p.color) }
        }
    }

/** Cât durează un pas al pionului de pe o căsuță pe alta. */
// ca în web: un pas pe tablă durează 430 ms, iar mersul forțat (RECHEMAT) 240 ms
private const val STEP_MS = 430
private const val WALK_MS = 240

@Composable
private fun PropertyMarkerView(marker: PropertyMarker, position: Pair<Float, Float>, boardWidth: Dp, boardHeight: Dp) {
    val color = Color(marker.color.hex)
    // Marcajul de pe căsuță trebuie să fie nevăzut pe fundal, dar să rămână clar;
    // 10% din lățime/înălțime a tablei, cu fundal și contur în culoarea proprietarului.
    val w = boardWidth * 0.06f
    val h = boardHeight * 0.075f

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = boardWidth.toPx() * position.first - w.toPx() / 2f
                translationY = boardHeight.toPx() * position.second - h.toPx() / 2f
            }
            .size(w, h),
    ) {
        // Fundal circular portocaliu, vizibil pe orice căsuță
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color, CircleShape),
        )
        // Contur gros în culoarea proprietarului
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .border(2.dp, color, CircleShape),
        )
    }
}

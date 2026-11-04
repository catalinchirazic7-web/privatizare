package com.example.privatizare.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.data.model.Tile
import com.example.privatizare.logic.ActionCalculator
import kotlin.math.roundToLong

/**
 * Cardul care apare când un pion aterizează: iconița firmei, numele mare și situația căsuței.
 * Numele este complet (fără tăieturi) și mare, ca să se poată citi bine chiar și întreag pe telefon.
 */
@Composable
fun TileInfoCard(
    tile: Tile,
    property: PropertyAction?,
    owner: Player?,
    level: Int,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    val visual = if (property != null) propertyVisual(property.id) else tileVisual(tile)
    val accent = visual.accent
    val title = property?.name ?: visual.shortName
    val subtitle = when {
        property == null -> tileHint(tile) ?: ""
        owner != null -> buildString {
            append("A lui ${owner.name}")
            append(" · chirie ${ActionCalculator.calculateRent(property, level)} ECU")
            if (level > 0) append(" · $level ${if (level == 1) "anexă" else "anexe"}")
        }
        else -> "De vânzare · titlu ${property.titlePrice} ECU"
    }
    val ownerColor = owner?.let { Color(it.color.hex) } ?: Color.Transparent

    Row(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.82f), Color(0xFF1A1A1A))),
                RoundedCornerShape(16.dp),
            )
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .clickable(enabled = onOpen != null) { onOpen?.invoke() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // iconița pe un disc alb, ca să se vadă pe orice culoare
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(Color.White, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PropertyIconImage(property?.id, visual.icon, 60.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                textAlign = TextAlign.Start,
            )
            if (owner != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(ownerColor, RoundedCornerShape(5.dp))
                            .border(1.dp, Color.White, RoundedCornerShape(5.dp)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("proprietar: ${owner.name}", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

/** Iconița reală a firmei, decupată din cartonașul tipărit (ștampila roz de pe titlu).
 * Dacă fotografia nu e disponibilă, rămâne emoji-ul.
 */
@Composable
fun PropertyIconImage(
    propertyId: String?,
    fallbackIcon: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val art = rememberCardArt(propertyId?.let { CardArt.iconPath(it) })
    if (art != null) {
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.2f)),
        )
    } else {
        Text(fallbackIcon, fontSize = (size.value * 0.8f).sp, modifier = modifier)
    }
}

/** Iconița unei firme, pentru liste și dialoguri. */
@Composable
fun PropertyIcon(propertyId: String, size: Int = 20) {
    PropertyIconImage(propertyId, propertyVisual(propertyId).icon, size.dp)
}

/** Bara de progres a anexelor: câte are firma din total. */
@Composable
fun AnnexPips(level: Int, total: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val owned = i < level
            Box(
                Modifier
                    .size(width = 14.dp, height = 5.dp)
                    .background(
                        if (owned) color else Color.White.copy(alpha = 0.25f),
                        RoundedCornerShape(3.dp),
                    ),
            )
            Spacer(Modifier.width(3.dp))
        }
    }
}

/** Animație de intrare a cardului: urcă și apare. */
@Composable
fun cardEnterProgress(visible: Boolean): Float =
    animateForcedFloat(if (visible) 1f else 0f, spring()).value

/**
 * Soldul unui jucător, care „curge" spre noua valoare în loc să sară.
 * Se vede imediat când încasezi o chirie sau plătești o taxă.
 */
@Composable
fun AnimatedMoney(
    value: Long,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    val shown = remember { Animatable(value.toFloat()) }
    var up by remember { mutableStateOf(false) }
    ForcedLaunchedEffect(value) {
        up = value > shown.value
        shown.animateTo(value.toFloat(), tween(620, easing = FastOutSlowInEasing))
    }
    val tint = when {
        color != Color.White -> color
        up -> Color(0xFF7CFF8A)
        else -> Color(0xFFFF8A80)
    }
    Text(
        "${shown.value.roundToLong()} ECU",
        modifier = modifier,
        color = tint,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )
}

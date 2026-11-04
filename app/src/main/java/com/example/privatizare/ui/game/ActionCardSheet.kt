package com.example.privatizare.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction

private val Paper = Color(0xFFF6EFDF)
private val Ink = Color(0xFF2E2418)
private val SheetGold = Color(0xFFFFD54F)
private val Owned = Color(0xFF2E7D32)

/**
 * Fișa originală a unei acțiuni, așa cum e tipărită pe cartonașul fotografiat.
 * Prima pagină e TITLUL DE PROPRIETATE; următoarele sunt anexele, în ordine.
 * Atingerea cartonașului (sau săgețile) trece la pagina următoare, până la ultima anexă.
 */
@Composable
fun ActionCardSheet(
    property: PropertyAction,
    owner: Player?,
    level: Int,
    canBuyAnnex: Boolean,
    onBuyAnnex: () -> Unit,
    onClose: () -> Unit,
) {
    val pages = 1 + property.annexes.size
    var page by remember(property.id) { mutableIntStateOf(0) }
    val visual = propertyVisual(property.id)
    val art = rememberCardArt(CardArt.actionPath(property.id, page))
    val ratio = art?.let { it.width.toFloat() / it.height.toFloat() } ?: 1.36f

    val annex = property.annexes.getOrNull(page - 1)
    val owned = owner != null && (page == 0 || page <= level)
    val canBuy = page >= 1 && annex != null && canBuyAnnex && page == level + 1

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.84f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(16.dp)
                    // atingerile din fișă nu închid fereastra
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Antet: firma și starea ei
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { PropertyIconImage(property.id, visual.icon, 50.dp) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            property.name,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                owner == null -> "titlu liber · ${property.titlePrice} ECU"
                                else -> "proprietar: ${owner.name}"
                            },
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = Color.White, fontSize = 15.sp) }
                }

                // Cartonașul fotografiat
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                        .shadow(16.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(Paper)
                        .border(2.dp, if (owned) Owned else SheetGold.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                        .clickable { page = (page + 1) % pages },
                    contentAlignment = Alignment.Center,
                ) {
                    if (art != null) {
                        Image(
                            bitmap = art,
                            contentDescription = if (page == 0) "Titlu de proprietate" else "Anexa $page",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("Se încarcă…", color = Ink.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                    // eticheta paginii, în colț
                    Text(
                        if (page == 0) "TITLU" else "ANEXA $page",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(if (page == 0) Ink.copy(alpha = 0.85f) else visual.accent.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (owned) {
                        Text(
                            "DEȚINUTĂ",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Owned, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Răsfoire: săgeți + puncte
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PagerArrow("‹") { page = (page - 1 + pages) % pages }
                    Spacer(Modifier.width(10.dp))
                    repeat(pages) { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == page) 9.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == page) SheetGold else Color.White.copy(alpha = 0.35f),
                                )
                                .clickable { page = i },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    PagerArrow("›") { page = (page + 1) % pages }
                }

                Spacer(Modifier.height(8.dp))

                // Explicația paginii curente
                Text(
                    text = when {
                        page == 0 -> "Titlu de proprietate · ${property.titlePrice} ECU · taxă ${property.titleTax} ECU"
                        annex != null -> buildString {
                            append("Anexa $page: ${annex.price} ECU · profit +${annex.profit} ECU")
                            append(if (page <= level) " · deținută" else " · de cumpărat")
                        }
                        else -> ""
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )

                if (canBuy && annex != null) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onBuyAnnex,
                        colors = ButtonDefaults.buttonColors(containerColor = visual.accent),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("CUMPĂRĂ ANEXA $page — ${annex.price} ECU", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    if (pages > 1) "atinge cartonașul pentru pagina următoare" else "",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun PagerArrow(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.10f))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Color.White, fontSize = 20.sp, fontFamily = FontFamily.Serif)
    }
}

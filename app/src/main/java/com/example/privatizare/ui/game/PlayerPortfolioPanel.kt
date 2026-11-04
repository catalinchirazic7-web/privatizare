package com.example.privatizare.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.logic.ActionCalculator
import com.example.privatizare.logic.Sale

private val CardPaper = Color(0xFFF7F1E1)
private val CardInk = Color(0xFF3B2F20)
private val CardMuted = Color(0xFF8A7D68)

/**
 * Portofoliul unui jucător: un card pentru fiecare firmă deținută.
 * Antetul: numele firmei și titlul. Corpul: anexele. Subsolul: chiria totală încasabilă.
 * [canBuyAnnexFor] = proprietatea pe care jucătorul la rând stă acum și poate extinde.
 * [sellMode] = lichidare: acțiunile se vând de la buton.
 */
@Composable
fun PlayerPortfolioPanel(
    player: Player,
    actions: Map<String, PropertyAction>,
    detailed: Boolean,
    canBuyAnnexFor: String?,
    onBuyAnnex: (String) -> Unit,
    sellMode: Boolean,
    onSell: (Sale) -> Unit,
    modifier: Modifier = Modifier,
    /** Atingerea unei firme deschide fișa acțiunii (cartonașul original). */
    onOpenCard: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Portofoliul lui ${player.name}",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Text("Sold: ${player.money} ECU", color = CardMuted)
        Spacer(Modifier.height(8.dp))

        if (player.ownedProperties.isEmpty()) {
            Text("Nicio firmă deținută încă.", color = CardMuted)
        }

        player.ownedProperties.entries
            .sortedBy { actions[it.key]?.name ?: it.key }
            .forEach { (propId, level) ->
                val prop = actions.getValue(propId)
                PropertyCard(
                    prop = prop,
                    level = level,
                    detailed = detailed,
                    showBuyButton = detailed && canBuyAnnexFor == propId && level < prop.annexes.size,
                    onBuyAnnex = { onBuyAnnex(propId) },
                    sellMode = sellMode,
                    onSell = onSell,
                    onOpen = { onOpenCard(propId) },
                )
                Spacer(Modifier.height(10.dp))
            }
    }
}

@Composable
private fun PropertyCard(
    prop: PropertyAction,
    level: Int,
    detailed: Boolean,
    showBuyButton: Boolean,
    onBuyAnnex: () -> Unit,
    sellMode: Boolean,
    onSell: (Sale) -> Unit,
    onOpen: () -> Unit = {},
) {
    val rent = ActionCalculator.calculateRent(prop, level)
    val visual = propertyVisual(prop.id)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardPaper)
            .border(1.5.dp, CardInk, RoundedCornerShape(12.dp)),
    ) {
        // Antet colorat, în culoarea firmei: iconița + numele, pe fond viu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(visual.accent, visual.accent.copy(alpha = 0.78f))),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(onClick = onOpen),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White, RoundedCornerShape(10.dp)),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { PropertyIconImage(prop.id, visual.icon, 36.dp) }
            Spacer(Modifier.width(8.dp))
            Text(
                prop.name,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
            )
        }
        Column(modifier = Modifier.padding(12.dp)) {
        Text(
            "Titlu de proprietate · cost ${prop.titlePrice} ECU · taxă ${prop.titleTax} ECU",
            fontSize = 12.sp,
            color = CardMuted,
        )
        Spacer(Modifier.height(8.dp))

        if (!detailed) {
            // Adversar: anexele și chiria sunt confidențiale
            Text("Anexe și chirie: confidențiale", color = CardMuted, fontSize = 12.sp)
            return@Column
        }

        // Corp: anexele, una sub alta
        prop.annexes.forEach { annex ->
            val owned = annex.index <= level
            val line = if (owned) {
                "Anexa ${annex.index}: profit +${annex.profit} ECU"
            } else {
                "Anexa ${annex.index}: de cumpărat (${annex.price} ECU)"
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    line,
                    color = if (owned) CardInk else CardMuted,
                    modifier = Modifier.weight(1f),
                )
                if (sellMode && owned && annex.index == level) {
                    TextButton(onClick = { onSell(Sale(prop.id, level)) }) { Text("Vinde") }
                }
            }
        }
        if (sellMode) {
            // scoate proprietatea întreagă (titlu + anexe) de pe tablă
            TextButton(onClick = { onSell(Sale(prop.id, null)) }) {
                Text(if (level == 0) "Vinde titlul" else "Vinde întreaga acțiune")
            }
        }

        Spacer(Modifier.height(8.dp))
        // Subsol: chiria totală încasabilă, evidențiat
        Text(
            "CHIRIE TOTALĂ ÎNCASATĂ: $rent ECU",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = visual.accent,
        )

        if (showBuyButton) {
            Spacer(Modifier.height(6.dp))
            val next = level + 1
            Button(onClick = onBuyAnnex, modifier = Modifier.fillMaxWidth()) {
                Text("EXTINDE / CUMPĂRĂ ANEXA $next")
            }
        }
        }
    }
}

/** Cartonaș LOTO sau TELEX, afișat ca o carte de joc, cu buton de confirmare. */
@Composable
fun CardDialogContent(kind: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardPaper, RoundedCornerShape(12.dp))
            .border(3.dp, CardInk, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            kind,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            color = CardInk,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text,
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp,
            color = CardInk,
        )
    }
}

@Composable
fun CardDialogButton(onConfirm: () -> Unit) {
    OutlinedButton(onClick = onConfirm) { Text("Am înțeles") }
}

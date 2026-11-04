package com.example.privatizare.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.privatizare.data.model.Player
import com.example.privatizare.data.model.PropertyAction
import com.example.privatizare.logic.ActionCalculator
import com.example.privatizare.logic.BuyOption
import com.example.privatizare.logic.Debt
import com.example.privatizare.logic.GameRules
import com.example.privatizare.logic.Sale

/**
 * Căsuță liberă. Titlu: [DA, CUMPĂR] / [NU], cu avertisment despre taxa Băncii.
 * Anexă: [DA, CUMPĂR] / [NU], fără taxă la refuz.
 */
@Composable
fun BuyDecisionDialog(
    option: BuyOption,
    property: PropertyAction,
    buyer: Player,
    onBuy: () -> Unit,
    onRefuse: () -> Unit,
) {
    val price = when (option) {
        is BuyOption.Title -> option.price
        is BuyOption.AnnexOption -> option.annex.price
    }
    val canAfford = buyer.money >= price
    val visual = propertyVisual(property.id)
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PropertyIconImage(property.id, visual.icon, 56.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    when (option) {
                        is BuyOption.Title -> property.name
                        is BuyOption.AnnexOption -> "Anexa ${option.annex.index} — ${property.name}"
                    },
                )
            }
        },
        text = {
            Column {
                Text("Cost achiziție: $price ECU")
                Text("Sold: ${buyer.money} ECU")
                if (option is BuyOption.Title) {
                    Spacer(Modifier.height(6.dp))
                    Text("Atenție: dacă nu cumperi, achiți Băncii taxa de ${property.titleTax} ECU.")
                }
                if (!canAfford) {
                    Spacer(Modifier.height(6.dp))
                    Text("Fonduri insuficiente pentru cumpărare.")
                }
            }
        },
        confirmButton = {
            Button(onClick = onBuy, enabled = canAfford) { Text("DA, CUMPĂR") }
        },
        dismissButton = {
            OutlinedButton(onClick = onRefuse) { Text("NU") }
        },
    )
}

/** Balon după refuz: taxa titlului trebuie plătită Băncii. Transferul se execută la OK. */
@Composable
fun BankFeeDialog(property: PropertyAction, taxAmount: Long, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("CHIRIE BANCĂ") },
        text = { Text("Ai refuzat achiziția. Plătești taxa de $taxAmount ECU către Bancă (${property.name}).") },
        confirmButton = { Button(onClick = onOk) { Text("OK") } },
    )
}

/** Balon de chirie: proprietarul e alt jucător. Plata se face la PLĂTEȘTE; fonduri lipsă -> lichidare. */
@Composable
fun RentDueDialog(property: PropertyAction, owner: Player, rentAmount: Long, onPay: () -> Unit) {
    val level = owner.ownedProperties[property.id] ?: 0
    val visual = propertyVisual(property.id)
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PropertyIconImage(property.id, visual.icon, 56.dp)
                Spacer(Modifier.width(10.dp))
                Text("Ai oprit la ${property.name}!")
            }
        },
        text = {
            Column {
                Text("Proprietar: ${owner.name}. Trebuie să achiți chiria de $rentAmount ECU.")
                Spacer(Modifier.height(8.dp))
                // Defalcarea se arată doar aici, când jucătorul calcă pe căsuța adversarului
                Text("Taxă titlu: ${property.titleTax} ECU")
                property.annexes.filter { it.index <= level }.forEach { annex ->
                    Text("Anexa ${annex.index}: +${annex.profit} ECU")
                }
            }
        },
        confirmButton = { Button(onClick = onPay) { Text("PLĂTEȘTE") } },
    )
}

/**
 * Dialogul de lichidare. Jucătorul vinde acțiuni la Bancă (80%) până acoperă datoria.
 * Dacă nu mai are nimic de vândut, poate declara falimentul.
 */
@Composable
fun LiquidationDialog(
    debt: Debt,
    debtor: Player,
    sellable: List<Sale>,
    actions: Map<String, PropertyAction>,
    onSell: (Sale) -> Unit,
    onBankrupt: () -> Unit,
) {
    val creditor = if (debt.creditorId == null) "Băncii" else "jucătorului ${debt.creditorId}"
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Fonduri insuficiente pentru plată! Lichidați acțiuni sau anexe") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("${debtor.name} datorează ${debt.amount} ECU $creditor (${debt.reason}).")
                Text("Sold lichid: ${debtor.money} ECU. Lipsesc: ${debt.amount - debtor.money} ECU.")
                Spacer(Modifier.height(8.dp))
                if (sellable.isEmpty()) {
                    Text("Nu mai aveți acțiuni de vândut.")
                }
                sellable.forEach { sale ->
                    val prop = actions.getValue(sale.propertyId)
                    val level = debtor.ownedProperties[sale.propertyId] ?: 0
                    val (label, value) = if (sale.annexIndex != null) {
                        val annex = prop.annexes.first { it.index == sale.annexIndex }
                        "Anexa ${annex.index} la ${prop.name}" to ActionCalculator.sellBackValue(annex.price)
                    } else if (level > 0) {
                        "Întreaga acțiune ${prop.name} (titlu + $level anexe)" to ActionCalculator.wholeSaleValue(prop, level)
                    } else {
                        "Titlul ${prop.name}" to ActionCalculator.sellBackValue(prop.titlePrice)
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "$label (+$value ECU, ${GameRules.RESALE_RATIO_PERCENT}%)",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onSell(sale) }) { Text("Vinde") }
                    }
                }
            }
        },
        confirmButton = {
            if (sellable.isEmpty()) {
                Button(onClick = onBankrupt) { Text("Declară faliment") }
            }
        },
    )
}

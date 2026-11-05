package com.example.privatizare.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.privatizare.data.model.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Suma cu spațiu între grupele de cifre, ca în web (ex. 1 200). */
fun ecuText(n: Long): String {
    val digits = abs(n).toString().reversed().chunked(3).joinToString(" ").reversed()
    return if (n < 0) "-$digits" else digits
}

/** O modificare de bani, afișată 3 secunde ca în web. */
private class MoneyChip(val key: Long, val text: String, val plus: Boolean)

/**
 * Sumele care s-au schimbat (+/−) pe fiecare jucător: apar 3 secunde, sus pe tablă.
 * La creștere se aude moneda, ca în web.
 */
@Composable
fun MoneyFloats(players: List<Player>, modifier: Modifier = Modifier) {
    val chips = remember { mutableStateListOf<MoneyChip>() }
    val last = remember { HashMap<String, Long>() }
    var seq by remember { mutableLongStateOf(0L) }

    LaunchedEffect(players.map { it.id to it.money }) {
        for (p in players) {
            val old = last.put(p.id, p.money)
            if (old == null || old == p.money) continue
            val diff = p.money - old
            val key = ++seq
            chips += MoneyChip(key, "${p.name} ${if (diff > 0) "+" else "−"}${ecuText(abs(diff))}", diff > 0)
            if (diff > 0) GameSound.coin()
            launch {
                delay(3000)
                chips.removeAll { it.key == key }
            }
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chips.forEach { chip ->
            AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    chip.text,
                    color = if (chip.plus) Color(0xFFBDFBC8) else Color(0xFFFFB4B4),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

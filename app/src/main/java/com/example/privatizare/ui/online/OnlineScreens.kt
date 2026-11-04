package com.example.privatizare.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.privatizare.data.model.PawnColor
import com.example.privatizare.multiplayer.OnlineController
import com.example.privatizare.multiplayer.RoomRole
import com.example.privatizare.ui.game.ColorPalette
import com.example.privatizare.ui.game.PawnShape
import kotlinx.coroutines.delay

/** Cât așteptăm răspunsul bazei de date înainte să arătăm o eroare de conectare. */
private const val CONNECT_TIMEOUT_MS = 10_000L

/** Meniul [JOC ONLINE]: creezi o masă nouă sau intri într-una cu cod. */
@Composable
fun OnlineMenuScreen(controller: OnlineController, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(PawnColor.ROSU) }
    var codeText by remember { mutableStateOf("") }
    val nameOk = name.isNotBlank()

    // Pionii luați de alți jucători din camera al cărei cod e scris sunt dezactivați; dacă al tău a fost luat, treci pe unul liber
    LaunchedEffect(codeText) { controller.peekRoom(codeText) }
    LaunchedEffect(controller.takenColors) {
        if (color in controller.takenColors) {
            color = PawnColor.entries.firstOrNull { it !in controller.takenColors } ?: color
        }
    }

    // Dacă baza de date nu răspunde (URL greșit, fără internet), nu rămânem blocați
    LaunchedEffect(controller.busy) {
        if (controller.busy) {
            delay(CONNECT_TIMEOUT_MS)
            controller.timeoutIfBusy()
        }
    }

    // Landscape: formularul în stânga, alegerea pionului în dreapta, ca totul să încapă pe ecran
    Row(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("JOC ONLINE", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(12) },
                label = { Text("Numele tău") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { controller.createRoom(name, color) },
                enabled = nameOk && !controller.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("CREEAZĂ MASĂ") }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(4) },
                    label = { Text("Cod cameră") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { controller.joinRoom(codeText, name, color) },
                    enabled = nameOk && codeText.length == 4 && !controller.busy,
                ) { Text("CONECTARE") }
            }
            Spacer(Modifier.height(8.dp))
            if (controller.busy) Text("Se conectează…")
            controller.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onBack) { Text("Înapoi") }
        }
        Spacer(Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("Alege pionul")
            ColorPalette(selected = color, taken = controller.takenColors, onSelect = { color = it })
            if (controller.takenColors.isNotEmpty()) {
                Text("Pionii estompați sunt deja luați la această masă.", fontSize = 12.sp)
            }
        }
    }
}

/** Camera de așteptare: codul camerei, cine a intrat, roboți, pornirea meciului. */
@Composable
fun LobbyScreen(controller: OnlineController, onLeave: () -> Unit) {
    val info = controller.info
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Cod cameră", fontSize = 16.sp)
        Text(
            controller.code ?: "----",
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
        Text("Spune-le prietenilor acest cod ca să intre la masă.")
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (0 until 4).forEach { i ->
                val p = info?.players?.getOrNull(i)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
                    if (p != null) {
                        PawnShape(p.color, Modifier.size(width = 40.dp, height = 70.dp))
                        Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            when (p.role) {
                                RoomRole.HOST -> "Gazdă"
                                RoomRole.GUEST -> "Oaspete"
                                RoomRole.BOT -> "Robot"
                            },
                            fontSize = 12.sp,
                        )
                    } else {
                        Spacer(Modifier.size(width = 40.dp, height = 70.dp))
                        Text("liber", fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (controller.isHost) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { controller.addBot() }, enabled = info?.isFull == false) {
                    Text("+ ROBOT")
                }
                Button(onClick = { controller.startMatch() }, enabled = info?.canStart == true) {
                    Text("PORNEȘTE MECIUL")
                }
            }
            if (info?.canStart != true) Text("Sunt necesari cel puțin 2 jucători.", fontSize = 12.sp)
        } else {
            Text("Așteptăm ca gazda să pornească meciul…")
        }
        controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onLeave) { Text("Părăsește masa") }
    }
}

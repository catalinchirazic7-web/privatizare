package com.example.privatizare.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.privatizare.BuildConfig
import com.example.privatizare.R

/** Numărul de versiune afișat în dreapta sus, lângă butonul „Despre”. */
val APP_VERSION: String = BuildConfig.VERSION_NAME

/** Ecranul principal: logo-ul oficial, buton de start și dialog informativ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewGame: () -> Unit = {},
    onOnline: () -> Unit = {},
    onExit: () -> Unit = {},
    canResume: Boolean = false,
    onResume: () -> Unit = {},
    onRules: () -> Unit = {},
) {
    var showInfo by remember { mutableStateOf(false) }
    var showDonate by remember { mutableStateOf(false) }
    if (showDonate) DonateDialog(onDismiss = { showDonate = false })

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Privatizare") },
                actions = {
                    if (DonateConfig.URL.isNotBlank()) {
                        TextButton(onClick = { showDonate = true }) {
                            Text(DonateConfig.LABEL)
                        }
                    }
                    TextButton(onClick = { showInfo = true }) {
                        Text("Despre")
                    }
                    Text(
                        "v$APP_VERSION",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 14.dp),
                    )
                },
            )
        },
    ) { padding ->
        // Landscape: logo-ul în stânga, butoanele în dreapta, ca să încapă fără derulare
        Row(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_privatizare),
                contentDescription = "Logo Privatizare",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            Spacer(Modifier.width(24.dp))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (canResume) {
                    Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                        Text("Continuă jocul")
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
                    Text("Joc nou")
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOnline, modifier = Modifier.fillMaxWidth()) {
                    Text("JOC ONLINE")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth()) {
                    Text("Regulament")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                    Text("Ieșire din joc")
                }
            }
        }
    }

    if (showInfo) {
        // Dialog propriu (nu AlertDialog): pe telefonul în landscape fereastra standard
        // e îngustă și scurtă, iar textul cu creditul autorului ar rămâne tăiat.
        Dialog(
            onDismissRequest = { showInfo = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .heightIn(max = 300.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_privatizare),
                                contentDescription = "Logo Privatizare",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(70.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Privatizare", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    "Joc de societate românesc",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text("Joc de societate românesc, pentru tineret și adulți. Se joacă în 4 persoane.")
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Proiect independent realizat de Chirazic Catalin",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Contact: catalinchirazic7@gmail.com", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text("Versiune $APP_VERSION", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { showInfo = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Închide")
                        }
                    }
                }
            }
        }
    }
}

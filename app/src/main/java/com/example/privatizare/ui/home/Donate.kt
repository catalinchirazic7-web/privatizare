package com.example.privatizare.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.privatizare.R

/**
 * Donația opțională pentru proiect, prin Revolut. Suma e aleasă de fiecare, de la 1 leu în sus.
 * Linkul e cel de Revolut, citit din captura cu codul QR.
 */
object DonateConfig {
    const val URL: String = "https://revolut.me/catalidl7u"
    const val LABEL: String = "Donează"
}

/** Fereastra de donație: codul QR, linkul și butonul de deschidere. Suma o alege fiecare. */
@Composable
fun DonateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Donează", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Donezi cât consideri tu, de la 1 leu în sus. Jocul rămâne gratuit.",
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                Image(
                    painter = painterResource(id = R.drawable.donate_qr),
                    contentDescription = "Cod QR pentru donație",
                    modifier = Modifier.size(170.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(DonateConfig.URL, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DonateConfig.URL)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Deschide Revolut") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Închide")
                }
            }
        }
    }
}

package com.example.privatizare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.privatizare.ui.theme.CoverGradient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalContext
import com.example.privatizare.data.GameRepository
import com.example.privatizare.ui.game.AppRoot
import com.example.privatizare.ui.game.MotionForce
import com.example.privatizare.ui.home.HomeScreen
import com.example.privatizare.ui.theme.PrivatizareTheme

class MainActivity : ComponentActivity() {
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onResume() {
        super.onResume()
        // dacă s-au schimbat opțiunile pentru dezvoltatori între timp, recitim starea
        MotionForce.refresh(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MotionForce.refresh(this)
        enableEdgeToEdge()
        setContent {
            PrivatizareTheme {
                Box(modifier = Modifier.fillMaxSize().background(CoverGradient)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                val context = LocalContext.current
                val catalog = remember { GameRepository(context).load() }
                AppRoot(catalog = catalog) { onNewGame, onOnline, onExit, canResume, onResume, onRules ->
                    HomeScreen(
                        onNewGame = onNewGame,
                        onOnline = onOnline,
                        onExit = onExit,
                        canResume = canResume,
                        onResume = onResume,
                        onRules = onRules,
                    )
                }
                }
                }
            }
        }
    }
}

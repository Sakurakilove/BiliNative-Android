package dev.opencode.bilimobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.opencode.bilimobile.ui.BiliApp
import dev.opencode.bilimobile.ui.theme.BiliTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BiliTheme { BiliApp() } }
    }
}

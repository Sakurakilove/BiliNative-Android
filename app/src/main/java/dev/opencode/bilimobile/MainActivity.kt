package dev.opencode.bilimobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.opencode.bilimobile.ui.BiliApp
import dev.opencode.bilimobile.ui.theme.AppThemeMode
import dev.opencode.bilimobile.ui.theme.BiliTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences("appearance", 0) }
            var mode by remember { mutableStateOf(runCatching { AppThemeMode.valueOf(preferences.getString("theme", AppThemeMode.System.name).orEmpty()) }.getOrDefault(AppThemeMode.System)) }
            BiliTheme(mode) { BiliApp(mode, { value -> mode = value; preferences.edit().putString("theme", value.name).apply() }) }
        }
    }
}

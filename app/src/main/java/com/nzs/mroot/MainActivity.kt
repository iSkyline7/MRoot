package com.nzs.mroot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nzs.mroot.ui.MainScreen
import com.nzs.mroot.ui.SettingsScreen
import com.nzs.mroot.ui.theme.MRootTheme

import com.nzs.mroot.util.AppLogger

enum class AppScreen {
    Main,
    Settings
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.installCrashHandler(this)
        enableEdgeToEdge()
        setContent {
            MRootTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.Main) }

                BackHandler(enabled = currentScreen == AppScreen.Settings) {
                    currentScreen = AppScreen.Main
                }

                when (currentScreen) {
                    AppScreen.Main -> {
                        MainScreen(
                            onNavigateToSettings = { currentScreen = AppScreen.Settings }
                        )
                    }
                    AppScreen.Settings -> {
                        SettingsScreen(
                            onNavigateBack = { currentScreen = AppScreen.Main }
                        )
                    }
                }
            }
        }
    }
}
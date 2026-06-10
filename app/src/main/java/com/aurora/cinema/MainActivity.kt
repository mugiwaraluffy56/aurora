package com.aurora.cinema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aurora.cinema.ui.AuroraApp
import com.aurora.cinema.ui.theme.AuroraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as AuroraApplication).appContainer
        setContent {
            AuroraTheme {
                AuroraApp(appContainer = appContainer)
            }
        }
    }
}

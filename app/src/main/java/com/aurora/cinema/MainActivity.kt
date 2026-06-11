package com.aurora.cinema

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aurora.cinema.ui.AuroraApp
import com.aurora.cinema.ui.theme.AuroraTheme

class MainActivity : ComponentActivity() {
    private val appContainer: com.aurora.cinema.app.AppContainer
        get() = (application as AuroraApplication).appContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AuroraTheme {
                AuroraApp(appContainer = appContainer)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val hardwareInputController = appContainer.hardwareInputController
        if (hardwareInputController is com.aurora.cinema.input.AndroidHardwareInputController &&
            hardwareInputController.dispatchKeyEvent(event)
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

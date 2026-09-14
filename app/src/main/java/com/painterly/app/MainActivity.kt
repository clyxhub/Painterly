package com.painterly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.painterly.app.ui.PainterlyApp
import com.painterly.app.ui.theme.PainterlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PainterlyTheme {
                PainterlyApp()
            }
        }
    }
}

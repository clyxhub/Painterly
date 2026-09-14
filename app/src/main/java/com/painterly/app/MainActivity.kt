package com.painterly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.lifecycle.viewmodel.compose.viewModel
import com.painterly.app.ui.PainterlyApp
import com.painterly.app.ui.PainterlyViewModel
import com.painterly.app.ui.theme.PainterlyTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PainterlyTheme {
                val viewModel: PainterlyViewModel = viewModel()
                val windowSizeClass = calculateWindowSizeClass(this)
                PainterlyApp(viewModel = viewModel, windowSizeClass = windowSizeClass)
            }
        }
    }
}

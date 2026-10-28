package com.pescasub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pescasub.app.ui.AppRoot
import com.pescasub.app.ui.theme.BajamarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PescasubApp).container
        setContent {
            BajamarTheme {
                AppRoot(container)
            }
        }
    }
}

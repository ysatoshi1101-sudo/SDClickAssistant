package com.example.sdclickassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.sdclickassistant.presentation.navigation.AppNavHost
import com.example.sdclickassistant.ui.theme.SDClickAssistantTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SDClickAssistantTheme {
                AppNavHost()
            }
        }
    }
}
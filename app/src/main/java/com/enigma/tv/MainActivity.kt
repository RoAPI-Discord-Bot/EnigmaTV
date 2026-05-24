package com.enigma.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.enigma.tv.ui.EnigmaShell
import com.enigma.tv.ui.theme.EnigmaTVTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnigmaTVTheme {
                EnigmaShell()
            }
        }
    }
}

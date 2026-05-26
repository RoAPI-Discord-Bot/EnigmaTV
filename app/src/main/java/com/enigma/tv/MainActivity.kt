package com.enigma.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.enigma.tv.ui.EnigmaShell
import com.enigma.tv.ui.theme.EnigmaTVTheme

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val prefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)
        
        setContent {
            EnigmaTVTheme {
                if (lastCrash != null) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            text = "FATAL CRASH:\n\n$lastCrash",
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                    prefs.edit().remove("last_crash").apply()
                } else {
                    EnigmaShell()
                }
            }
        }
    }
}

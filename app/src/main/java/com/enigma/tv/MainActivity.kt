package com.enigma.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.enigma.tv.ui.MoviesScreen
import com.enigma.tv.ui.TvScreen
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaTVTheme
import com.enigma.tv.ui.theme.MovieAccent
import com.enigma.tv.ui.theme.TvAccent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnigmaTVTheme {
                EnigmaTVApp()
            }
        }
    }
}

@Composable
fun EnigmaTVApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        containerColor = BgDark,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF141414)) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Movie, contentDescription = "Movies") },
                    label = { Text("Movies", fontWeight = FontWeight.SemiBold) },
                    colors = navColors(MovieAccent)
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Tv, contentDescription = "TV") },
                    label = { Text("TV", fontWeight = FontWeight.SemiBold) },
                    colors = navColors(TvAccent)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (tab) {
                0 -> MoviesScreen()
                else -> TvScreen()
            }
        }
    }
}

@Composable
private fun navColors(accent: Color) = NavigationBarItemDefaults.colors(
    selectedIconColor = accent,
    selectedTextColor = accent,
    indicatorColor = accent.copy(alpha = 0.15f),
    unselectedIconColor = Color(0xFF666666),
    unselectedTextColor = Color(0xFF666666)
)

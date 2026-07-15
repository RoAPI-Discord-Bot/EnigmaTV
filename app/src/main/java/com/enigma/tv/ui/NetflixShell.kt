package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enigma.tv.data.ProfileImageStorage
import com.enigma.tv.data.ViewerProfile
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

@Composable
fun NetflixBottomBar(
    current: NavSection,
    onSelect: (NavSection) -> Unit
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = EnigmaPink,
        selectedTextColor = TextPrimary,
        unselectedIconColor = TextSecondary,
        unselectedTextColor = TextSecondary,
        indicatorColor = EnigmaPurple.copy(alpha = 0.35f)
    )
    NavigationBar(
        containerColor = Color(0xFF141414),
        contentColor = TextPrimary
    ) {
        NavigationBarItem(
            selected = current == NavSection.HOME,
            onClick = { onSelect(NavSection.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontSize = 11.sp) },
            colors = itemColors
        )
        NavigationBarItem(
            selected = current == NavSection.LIVE,
            onClick = { onSelect(NavSection.LIVE) },
            icon = { Icon(Icons.Default.LiveTv, contentDescription = "Live") },
            label = { Text("Live", fontSize = 11.sp) },
            colors = itemColors
        )
        NavigationBarItem(
            selected = current == NavSection.CONTINUE,
            onClick = { onSelect(NavSection.CONTINUE) },
            icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Continue") },
            label = { Text("Continue", fontSize = 11.sp) },
            colors = itemColors
        )
        NavigationBarItem(
            selected = current == NavSection.PLAYLISTS,
            onClick = { onSelect(NavSection.PLAYLISTS) },
            icon = { Icon(Icons.Default.Favorite, contentDescription = "Playlists") },
            label = { Text("Playlists", fontSize = 11.sp) },
            colors = itemColors
        )
        NavigationBarItem(
            selected = current == NavSection.PROFILE,
            onClick = { onSelect(NavSection.PROFILE) },
            icon = { Icon(Icons.Default.Person, contentDescription = "Account") },
            label = { Text("Account", fontSize = 11.sp) },
            colors = itemColors
        )
    }
}

@Composable
fun ProfileNavIcon(profile: ViewerProfile?, modifier: Modifier = Modifier) {
    val index = profile?.avatarIndex ?: 0
    val color = profileAvatarColor(index)
    val context = LocalContext.current
    val imageModel = profile?.let {
        remember(it.id, it.avatarBase64, it.avatarUri) {
            ProfileImageStorage.avatarModel(it, context)
        }
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .glassSurface(cornerRadius = 18.dp, accentBorder = true)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Switch profile",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                profileAvatarIcon(index),
                contentDescription = "Switch profile",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

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
import androidx.compose.ui.graphics.vector.ImageVector
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
    NavigationBar(
        containerColor = Color(0xFF141414),
        contentColor = TextPrimary
    ) {
        NetflixNavItem(Icons.Default.Home, "Home", NavSection.HOME, current, onSelect)
        NetflixNavItem(Icons.Default.LiveTv, "Live", NavSection.LIVE, current, onSelect)
        NetflixNavItem(Icons.Default.PlayCircle, "Continue", NavSection.CONTINUE, current, onSelect)
        NetflixNavItem(Icons.Default.Favorite, "List", NavSection.FAVORITES, current, onSelect)
        NetflixNavItem(Icons.Default.Person, "Account", NavSection.PROFILE, current, onSelect)
    }
}

@Composable
private fun NetflixNavItem(
    icon: ImageVector,
    label: String,
    section: NavSection,
    current: NavSection,
    onSelect: (NavSection) -> Unit
) {
    NavigationBarItem(
        selected = current == section,
        onClick = { onSelect(section) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontSize = 11.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = EnigmaPink,
            selectedTextColor = TextPrimary,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary,
            indicatorColor = EnigmaPurple.copy(alpha = 0.35f)
        )
    )
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

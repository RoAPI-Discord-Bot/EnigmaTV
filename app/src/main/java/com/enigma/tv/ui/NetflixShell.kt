package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
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
        NetflixNavItem(Icons.Default.LiveTv, "Live TV", NavSection.LIVE, current, onSelect)
        NetflixNavItem(Icons.Default.Favorite, "My List", NavSection.FAVORITES, current, onSelect)
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
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            profileAvatarIcon(index),
            contentDescription = "Switch profile",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enigma.tv.data.ProfileImageStorage
import com.enigma.tv.data.ViewerProfile
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

@Composable
fun NetflixBottomBar(
    current: NavSection,
    onSelect: (NavSection) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavEntry(Icons.Default.Home, "Home", NavSection.HOME, current, onSelect)
        BottomNavEntry(Icons.Default.LiveTv, "Live TV", NavSection.LIVE, current, onSelect)
        BottomNavEntry(Icons.Default.Favorite, "My List", NavSection.FAVORITES, current, onSelect)
        BottomNavEntry(Icons.Default.Person, "Account", NavSection.PROFILE, current, onSelect)
    }
}

@Composable
private fun BottomNavEntry(
    icon: ImageVector,
    label: String,
    section: NavSection,
    current: NavSection,
    onSelect: (NavSection) -> Unit
) {
    val selected = current == section
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onSelect(section) }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) EnigmaPink else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ProfileNavIcon(profile: ViewerProfile?, modifier: Modifier = Modifier) {
    val index = profile?.avatarIndex ?: 0
    val color = profileAvatarColor(index)
    val context = LocalContext.current
    val imageModel = profile?.let {
        remember(it.id, it.avatarBase64) {
            ProfileImageStorage.avatarModel(it, context)
        }
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
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

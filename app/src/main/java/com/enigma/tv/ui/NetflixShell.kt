package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.enigma.tv.data.ProfileImageStorage
import com.enigma.tv.data.ViewerProfile
import com.enigma.tv.ui.theme.glassSurface

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

package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enigma.tv.data.ProfileImageStorage
import com.enigma.tv.data.ViewerProfile
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

@Composable
fun ProfileAvatarCircle(
    profile: ViewerProfile,
    selected: Boolean,
    sizeDp: Int = 88,
    showEditBadge: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = remember(profile.id, profile.avatarBase64, profile.avatarUri, profile.avatarIndex) {
        ProfileImageStorage.avatarModel(profile, context)
            ?: ProfileAvatarPresets.imageUrl(profile.avatarIndex)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .focusable()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
                .then(
                    if (selected) Modifier.border(3.dp, EnigmaPink, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = profile.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
            if (showEditBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(EnigmaPurple)
                        .border(2.dp, BgDark, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            profile.name,
            color = if (selected) EnigmaPink else TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth()
        )
    }
}

@Composable
fun ProfilePresetPickerGrid(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = modifier.height(280.dp)
    ) {
        itemsIndexed(ProfileAvatarPresets.all) { index, preset ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(index) }
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .then(
                            if (selectedIndex == index) Modifier.border(2.dp, EnigmaPink, CircleShape)
                            else Modifier
                        )
                ) {
                    AsyncImage(
                        model = preset.imageUrl,
                        contentDescription = preset.label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter
                    )
                }
                Text(
                    preset.label,
                    color = if (selectedIndex == index) EnigmaPink else TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun AddProfileTile(sizeDp: Int = 88, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .focusable()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add profile", tint = TextSecondary, modifier = Modifier.size(36.dp))
        }
        Text("Add Profile", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
fun NetflixProfilePicker(
    profiles: List<ViewerProfile>,
    activeProfileId: String,
    title: String = "Who's watching?",
    columns: Int = 4,
    onSelect: (ViewerProfile) -> Unit,
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (title.isNotBlank()) {
            Text(title, color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceIn(2, 5)),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 32.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(profiles.size) { i ->
                val profile = profiles[i]
                ProfileAvatarCircle(
                    profile = profile,
                    selected = activeProfileId.isNotBlank() && profile.id == activeProfileId,
                    sizeDp = when {
                        columns <= 3 -> 108
                        columns >= 5 -> 96
                        else -> 88
                    },
                    onClick = { onSelect(profile) }
                )
            }
            item(key = "add") {
                AddProfileTile(
                    sizeDp = when {
                        columns <= 3 -> 108
                        columns >= 5 -> 96
                        else -> 88
                    },
                    onClick = onAddProfile
                )
            }
        }
    }
}

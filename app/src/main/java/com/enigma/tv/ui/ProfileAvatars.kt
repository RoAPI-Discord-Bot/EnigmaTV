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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
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
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.enigma.tv.data.ProfileImageStorage
import com.enigma.tv.data.ViewerProfile
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

private val avatarColors = listOf(
    Color(0xFF6A1B9A),
    Color(0xFFE040FB),
    Color(0xFF00ACC1),
    Color(0xFFFF6F00),
    Color(0xFF43A047),
    Color(0xFF5C6BC0),
    Color(0xFFD81B60),
    Color(0xFF00838F)
)

private val avatarIcons = listOf(
    Icons.Default.Person,
    Icons.Default.Face,
    Icons.Default.Star,
    Icons.Default.Movie,
    Icons.Default.Tv,
    Icons.Default.SportsEsports,
    Icons.Default.Favorite,
    Icons.Default.ChildCare
)

fun profileAvatarColor(index: Int): Color = avatarColors[index.mod(avatarColors.size)]

fun profileAvatarIcon(index: Int): ImageVector = avatarIcons[index.mod(avatarIcons.size)]

@Composable
fun ProfileAvatarCircle(
    profile: ViewerProfile,
    selected: Boolean,
    sizeDp: Int = 88,
    showEditBadge: Boolean = false,
    onClick: () -> Unit
) {
    val color = profileAvatarColor(profile.avatarIndex)
    val context = LocalContext.current
    val imageModel = remember(profile.id, profile.avatarBase64, profile.avatarUri) {
        ProfileImageStorage.avatarModel(profile, context)
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
                .background(color)
                .then(
                    if (selected) Modifier.border(3.dp, EnigmaPink, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = profile.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    profileAvatarIcon(profile.avatarIndex),
                    contentDescription = profile.name,
                    tint = Color.White,
                    modifier = Modifier.size((sizeDp * 0.45f).dp)
                )
            }
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
            Spacer8()
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceIn(2, 5)),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 32.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(profiles, key = { it.id }) { profile ->
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

@Composable
private fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
}

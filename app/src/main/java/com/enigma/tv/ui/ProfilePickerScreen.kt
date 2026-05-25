package com.enigma.tv.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.R
import com.enigma.tv.data.ViewerProfile
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

/**
 * Full-screen Netflix-style "Who's watching?" gate shown on every app open.
 */
@Composable
fun ProfilePickerGate(
    profiles: List<ViewerProfile>,
    layout: ScreenLayout,
    onSelectProfile: (String) -> Unit,
    onAddProfile: (String) -> Unit,
    onRenameProfile: (String, String) -> Unit,
    onRemoveProfile: (String) -> Unit,
    onSetAvatarIndex: (String, Int) -> Unit,
    onSetAvatarUri: (String, String?) -> Unit
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var manageMode by rememberSaveable { mutableStateOf(false) }
    var editingProfile by rememberSaveable { mutableStateOf<ViewerProfile?>(null) }
    var editName by rememberSaveable { mutableStateOf("") }
    var editAvatarIndex by rememberSaveable { mutableStateOf(0) }

    val columns = when (layout) {
        ScreenLayout.TV -> 5
        ScreenLayout.TABLET -> 4
        ScreenLayout.PHONE -> if (profiles.size <= 3) 3 else 4
    }
    val avatarSize = when (layout) {
        ScreenLayout.TV -> 120
        ScreenLayout.TABLET -> 100
        ScreenLayout.PHONE -> 88
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val profile = editingProfile ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            onSetAvatarUri(profile.id, uri.toString())
            editingProfile = profile.copy(avatarUri = uri.toString())
        }
    }

    AppAmbientBackground(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (layout == ScreenLayout.TV) 48.dp else 28.dp))
            Image(
                painter = painterResource(R.drawable.enigma_mark),
                contentDescription = ENIGMA_TV_BRAND,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(if (layout == ScreenLayout.TV) 64.dp else 52.dp)
                    .padding(6.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                ENIGMA_TV_BRAND,
                color = EnigmaPurple,
                fontSize = if (layout == ScreenLayout.TV) 36.sp else 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(if (layout == ScreenLayout.TV) 40.dp else 28.dp))
            Text(
                if (manageMode) "Manage Profiles" else "Who's watching?",
                color = TextPrimary,
                fontSize = if (layout == ScreenLayout.TV) 42.sp else 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (manageMode) "Tap a profile to edit or delete" else "Select a profile to continue",
                color = TextSecondary,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(if (layout == ScreenLayout.TV) 32.dp else 24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns.coerceIn(2, 5)),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 32.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileAvatarCircle(
                        profile = profile,
                        selected = false,
                        sizeDp = avatarSize,
                        showEditBadge = manageMode,
                        onClick = {
                            if (manageMode) {
                                editingProfile = profile
                                editName = profile.name
                                editAvatarIndex = profile.avatarIndex
                            } else {
                                onSelectProfile(profile.id)
                            }
                        }
                    )
                }
                if (!manageMode) {
                    item(key = "add") {
                        AddProfileTile(sizeDp = avatarSize, onClick = { showAddDialog = true })
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (layout == ScreenLayout.TV) 40.dp else 28.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    if (manageMode) "Done" else "Manage Profiles",
                    color = if (manageMode) EnigmaPink else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .focusable()
                        .clickable { manageMode = !manageMode }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Profile") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onAddProfile(newName.trim())
                            newName = ""
                            showAddDialog = false
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    editingProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { editingProfile = null },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Pick an icon", color = TextSecondary, fontSize = 13.sp)
                    ProfilePresetPickerGrid(
                        selectedIndex = editAvatarIndex,
                        onSelect = { index ->
                            editAvatarIndex = index
                            onSetAvatarIndex(profile.id, index)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = { photoPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = EnigmaPurple)
                        Text("  Choose photo from gallery", color = TextPrimary)
                    }
                    if (profiles.size > 1) {
                        TextButton(
                            onClick = {
                                onRemoveProfile(profile.id)
                                editingProfile = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                            Text("  Delete profile", color = Color(0xFFFF5252))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank()) onRenameProfile(profile.id, editName.trim())
                        editingProfile = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingProfile = null }) { Text("Cancel") }
            },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

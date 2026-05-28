package com.enigma.tv.ui

import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.R
import com.enigma.tv.data.ViewerProfile
import kotlinx.coroutines.delay
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud

/**
 * Full-screen Netflix-style "Who's watching?" gate shown on every app open.
 */
@Composable
fun ProfilePickerGate(
    profiles: List<ViewerProfile>,
    activeProfileId: String,
    openingProfileId: String? = null,
    layout: ScreenLayout,
    isLoggedIn: Boolean = false,
    userEmail: String = "",
    onSelectProfile: (String) -> Unit,
    onAddProfile: (String) -> Unit,
    onRenameProfile: (String, String) -> Unit,
    onRemoveProfile: (String) -> Unit,
    onSetAvatarIndex: (String, Int) -> Unit,
    onSetAvatarUri: (String, String?) -> Unit,
    onSignIn: (() -> Unit)? = null
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var manageMode by rememberSaveable { mutableStateOf(false) }
    var editingProfile by rememberSaveable { mutableStateOf<ViewerProfile?>(null) }
    var editName by rememberSaveable { mutableStateOf("") }
    var editAvatarIndex by rememberSaveable { mutableStateOf(0) }
    var focusedProfileId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(profiles, activeProfileId) {
        if (profiles.isEmpty()) return@LaunchedEffect
        if (focusedProfileId == null || profiles.none { it.id == focusedProfileId }) {
            focusedProfileId = profiles.find { it.id == activeProfileId }?.id ?: profiles.first().id
        }
    }

    val isTv = layout == ScreenLayout.TV
    val gateLocked = openingProfileId != null
    val avatarSize = when (layout) {
        ScreenLayout.TV -> 132
        ScreenLayout.TABLET -> 100
        ScreenLayout.PHONE -> 88
    }
    val columns = when (layout) {
        ScreenLayout.TV -> 3
        ScreenLayout.TABLET -> 4
        ScreenLayout.PHONE -> if (profiles.size <= 3) 3 else 4
    }

    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv, profiles, focusedProfileId) {
        if (!isTv || profiles.isEmpty() || manageMode) return@LaunchedEffect
        kotlinx.coroutines.delay(280)
        runCatching { initialFocusRequester.requestFocus() }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val profile = editingProfile ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            onSetAvatarUri(profile.id, uri.toString())
            editingProfile = profile.copy(avatarUri = uri.toString())
        }
    }

    val distinctProfiles = remember(profiles) { profiles.distinctBy { it.id } }
    val initialTargetId = remember {
        distinctProfiles.find { it.id == activeProfileId }?.id ?: distinctProfiles.firstOrNull()?.id
    }

    AppAmbientBackground(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(if (isTv) 36.dp else 24.dp))
                Image(
                    painter = painterResource(R.drawable.enigma_mark),
                    contentDescription = ENIGMA_TV_BRAND,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(if (isTv) 56.dp else 48.dp)
                        .padding(4.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    ENIGMA_TV_BRAND,
                    color = EnigmaPurple,
                    fontSize = if (isTv) 30.sp else 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(if (isTv) 24.dp else 20.dp))
                Text(
                    if (manageMode) "Manage Profiles" else "Who's watching?",
                    color = TextPrimary,
                    fontSize = if (isTv) 36.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        manageMode -> "Tap a profile to edit or delete"
                        isTv -> "Use remote to highlight, OK to select"
                        else -> "Select a profile to continue"
                    },
                    color = TextSecondary,
                    fontSize = if (isTv) 16.sp else 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(if (isTv) 28.dp else 20.dp))

                if (isTv) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(distinctProfiles, key = { it.id }) { profile ->
                            val focused = profile.id == focusedProfileId
                            val isInitialFocus = profile.id == initialTargetId
                            ProfilePickerTile(
                                profile = profile,
                                focused = focused,
                                sizeDp = avatarSize,
                                showEditBadge = manageMode,
                                isTv = true,
                                focusRequester = if (isInitialFocus) initialFocusRequester else null,
                                onFocus = { focusedProfileId = profile.id },
                                onActivate = {
                                    if (!gateLocked) {
                                        focusedProfileId = profile.id
                                        if (manageMode) {
                                            editingProfile = profile
                                            editName = profile.name
                                            editAvatarIndex = profile.avatarIndex
                                        } else {
                                            onSelectProfile(profile.id)
                                        }
                                    }
                                }
                            )
                        }
                        if (!manageMode && !gateLocked) {
                            item(key = "add") {
                                AddProfileTile(
                                    sizeDp = avatarSize,
                                    isTv = true,
                                    onClick = { showAddDialog = true }
                                )
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns.coerceIn(2, 4)),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 720.dp)
                    ) {
                        items(distinctProfiles, key = { it.id }) { profile ->
                            ProfilePickerTile(
                                profile = profile,
                                focused = profile.id == focusedProfileId,
                                sizeDp = avatarSize,
                                showEditBadge = manageMode,
                                isTv = false,
                                focusRequester = null,
                                onFocus = { focusedProfileId = profile.id },
                                onActivate = {
                                    if (!gateLocked) {
                                        focusedProfileId = profile.id
                                        if (manageMode) {
                                            editingProfile = profile
                                            editName = profile.name
                                            editAvatarIndex = profile.avatarIndex
                                        } else {
                                            onSelectProfile(profile.id)
                                        }
                                    }
                                }
                            )
                        }
                        if (!manageMode && !gateLocked) {
                            item(key = "add") {
                                AddProfileTile(
                                    sizeDp = avatarSize,
                                    isTv = false,
                                    onClick = { showAddDialog = true }
                                )
                            }
                        }
                    }
                }

                // Account sync banner
                if (!gateLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = if (isTv) 8.dp else 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isLoggedIn) EnigmaPurple.copy(alpha = 0.18f)
                                else Color(0xFF1E1E2E)
                            )
                            .then(
                                if (!isLoggedIn && onSignIn != null)
                                    Modifier.clickable { onSignIn() }
                                else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (isLoggedIn) Icons.Default.Cloud else Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = if (isLoggedIn) EnigmaPurple else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isLoggedIn) "Synced to $userEmail"
                                else "Sign in to sync profiles across devices",
                                color = if (isLoggedIn) EnigmaPurple else TextSecondary,
                                fontSize = if (isTv) 14.sp else 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isTv) 16.dp else 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (manageMode) "Done" else "Manage Profiles",
                        color = if (manageMode) EnigmaPink else TextSecondary,
                        fontSize = if (isTv) 16.sp else 14.sp,
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

@Composable
private fun ProfilePickerTile(
    profile: ViewerProfile,
    focused: Boolean,
    sizeDp: Int,
    showEditBadge: Boolean,
    isTv: Boolean,
    focusRequester: FocusRequester?,
    onFocus: () -> Unit,
    onActivate: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val showFocusRing = if (isTv) isFocused else (focused || isFocused)
    var lastActivateAt by remember { mutableStateOf(0L) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (!isTv && showFocusRing) Modifier.scale(1.05f) else Modifier)
            .onFocusChanged { state ->
                if (state.isFocused) onFocus()
            }
            .then(
                Modifier
                    .focusable(interactionSource = interaction)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.key.nativeKeyCode == KeyEvent.KEYCODE_ENTER)) {
                            onActivate()
                            true
                        } else false
                    }
                    .clickable(interactionSource = interaction, indication = null, onClick = onActivate)
            )
            .padding(horizontal = if (isTv) 14.dp else 8.dp, vertical = if (isTv) 10.dp else 6.dp)
    ) {
        Box(
            modifier = when {
                isTv && showFocusRing -> Modifier.border(4.dp, EnigmaPink, CircleShape)
                !isTv && showFocusRing -> Modifier.border(3.dp, EnigmaPink, CircleShape)
                else -> Modifier
            }
        ) {
            ProfileAvatarCircle(
                profile = profile,
                selected = false,
                sizeDp = sizeDp,
                showEditBadge = showEditBadge,
                onClick = null
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = profile.name,
            color = if (showFocusRing) Color.White else TextSecondary,
            fontSize = if (isTv) 16.sp else 14.sp,
            fontWeight = if (showFocusRing) FontWeight.Bold else FontWeight.Medium
        )
        if (!isTv && showFocusRing && !showEditBadge) {
            Text(
                "Selected",
                color = EnigmaPink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

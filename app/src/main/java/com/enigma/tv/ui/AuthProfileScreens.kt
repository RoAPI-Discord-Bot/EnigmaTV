package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.data.ViewerProfile
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

@Composable
fun ProfileScreen(
    isLoggedIn: Boolean,
    email: String,
    displayName: String,
    profiles: List<ViewerProfile>,
    activeProfileId: String,
    statusMessage: String?,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onGuest: () -> Unit,
    onSignOut: () -> Unit,
    onSync: () -> Unit,
    onSwitchProfile: (String) -> Unit,
    onAddProfile: (String) -> Unit,
    onRemoveProfile: (String) -> Unit,
    onOpenProfilePicker: () -> Unit,
    layout: ScreenLayout = ScreenLayout.PHONE
) {
    val isTv = layout == ScreenLayout.TV
    if (isTv) {
        TvProfileScreen(
            isLoggedIn = isLoggedIn,
            email = email,
            displayName = displayName,
            profiles = profiles,
            activeProfileId = activeProfileId,
            statusMessage = statusMessage,
            error = error,
            onSignIn = onSignIn,
            onSignUp = onSignUp,
            onGuest = onGuest,
            onSignOut = onSignOut,
            onOpenProfilePicker = onOpenProfilePicker
        )
    } else {
        MobileProfileScreen(
            isLoggedIn = isLoggedIn,
            email = email,
            displayName = displayName,
            profiles = profiles,
            activeProfileId = activeProfileId,
            statusMessage = statusMessage,
            error = error,
            onSignIn = onSignIn,
            onSignUp = onSignUp,
            onGuest = onGuest,
            onSignOut = onSignOut,
            onOpenProfilePicker = onOpenProfilePicker
        )
    }
}

// ─── TV: Wide centered two-column layout ──────────────────────────────────────
@Composable
private fun TvProfileScreen(
    isLoggedIn: Boolean,
    email: String,
    displayName: String,
    profiles: List<ViewerProfile>,
    activeProfileId: String,
    statusMessage: String?,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onGuest: () -> Unit,
    onSignOut: () -> Unit,
    onOpenProfilePicker: () -> Unit
) {
    val activeProfile = profiles.find { it.id == activeProfileId }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.85f),
            horizontalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            // Left: Avatar + profile card
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .glassSurface(cornerRadius = 20.dp)
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val profile = activeProfile ?: ViewerProfile("default", "Main", 0)
                var avatarFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .then(if (avatarFocused) Modifier.border(3.dp, EnigmaPink, RoundedCornerShape(50)) else Modifier)
                        .onFocusChanged { avatarFocused = it.isFocused }
                        .clickable { onOpenProfilePicker() }
                ) {
                    ProfileAvatarCircle(
                        profile = profile,
                        selected = false,
                        sizeDp = 120,
                        onClick = null
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    profile.name,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Active Profile",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(24.dp))
                var switchFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onOpenProfilePicker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .onFocusChanged { switchFocused = it.isFocused }
                        .then(if (switchFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
                    colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Switch Profile", fontSize = 15.sp)
                }
            }

            // Right: Account info / sign-in form
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(20.dp))
                    .glassSurface(cornerRadius = 20.dp)
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(36.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Account", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("EnigmaTV 2.0", color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(28.dp))

                if (isLoggedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = EnigmaPurple, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Signed in as", color = TextSecondary, fontSize = 13.sp)
                            Text(displayName.ifBlank { "EnigmaTV User" }, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(email, color = EnigmaPink, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    var signOutFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { signOutFocused = it.isFocused }
                            .then(if (signOutFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Sign out", fontSize = 15.sp, color = TextPrimary)
                    }
                } else {
                    TvSignInForm(onSignIn = onSignIn, onSignUp = onSignUp, onGuest = onGuest)
                }

                statusMessage?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = EnigmaPink, fontSize = 14.sp)
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFCC4444), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun TvSignInForm(
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onGuest: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("signin") }
    var name by rememberSaveable { mutableStateOf("") }
    var mail by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileTab("Sign in", mode == "signin") { mode = "signin" }
        ProfileTab("Sign up", mode == "signup") { mode = "signup" }
    }
    Spacer(Modifier.height(20.dp))

    if (mode == "signup") {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
        )
        Spacer(Modifier.height(12.dp))
    }
    OutlinedTextField(
        value = mail,
        onValueChange = { mail = it },
        label = { Text("Email") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = pass,
        onValueChange = { pass = it },
        label = { Text("Password") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password
        ),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle password",
                    tint = TextSecondary
                )
            }
        }
    )
    Spacer(Modifier.height(24.dp))
    var mainBtnFocused by remember { mutableStateOf(false) }
    Button(
        onClick = {
            when (mode) {
                "signup" -> onSignUp(mail, pass, name)
                else -> onSignIn(mail, pass)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { mainBtnFocused = it.isFocused }
            .then(if (mainBtnFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
        colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(if (mode == "signup") "Create account" else "Sign in", fontSize = 15.sp)
    }
    Spacer(Modifier.height(12.dp))
    var guestFocused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onGuest,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { guestFocused = it.isFocused }
            .then(if (guestFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("Continue as guest", color = TextPrimary, fontSize = 15.sp)
    }
}

// ─── Mobile: Original scrollable column layout ────────────────────────────────
@Composable
private fun MobileProfileScreen(
    isLoggedIn: Boolean,
    email: String,
    displayName: String,
    profiles: List<ViewerProfile>,
    activeProfileId: String,
    statusMessage: String?,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onGuest: () -> Unit,
    onSignOut: () -> Unit,
    onOpenProfilePicker: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("signin") }
    var name by rememberSaveable { mutableStateOf(displayName) }
    var mail by rememberSaveable { mutableStateOf(email) }
    var pass by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val activeProfile = profiles.find { it.id == activeProfileId }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Account", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "EnigmaTV 2.0",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        Spacer(Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val profile = activeProfile ?: ViewerProfile("default", "Main", 0)
            ProfileAvatarCircle(
                profile = profile,
                selected = false,
                sizeDp = 64,
                onClick = onOpenProfilePicker
            )
            Column(Modifier.weight(1f)) {
                Text("Watching as", color = TextSecondary, fontSize = 12.sp)
                Text(profile.name, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpenProfilePicker,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Switch Profile", color = TextPrimary)
        }

        if (isLoggedIn) {
            Spacer(Modifier.height(24.dp))
            Text("Signed in as", color = TextSecondary, fontSize = 12.sp)
            Text(displayName.ifBlank { "EnigmaTV User" }, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(email, color = EnigmaPink, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        } else {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileTab("Sign in", mode == "signin") { mode = "signin" }
                ProfileTab("Sign up", mode == "signup") { mode = "signup" }
            }
            Spacer(Modifier.height(12.dp))
            if (mode == "signup") {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(value = mail, onValueChange = { mail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password",
                            tint = TextSecondary
                        )
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    when (mode) {
                        "signup" -> onSignUp(mail, pass, name)
                        else -> onSignIn(mail, pass)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (mode == "signup") "Create account" else "Sign in")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onGuest, modifier = Modifier.fillMaxWidth()) { Text("Continue as guest") }
        }

        statusMessage?.let { Text(it, color = EnigmaPink, modifier = Modifier.padding(top = 12.dp)) }
        error?.let { Text(it, color = Color(0xFFCC4444), modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp) }
    }
}

@Composable
private fun ProfileTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) EnigmaPurple else Color(0xFF222222)
        ),
        shape = RoundedCornerShape(8.dp)
    ) { Text(label) }
}

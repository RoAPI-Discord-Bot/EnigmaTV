package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Account", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

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
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(value = mail, onValueChange = { mail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
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

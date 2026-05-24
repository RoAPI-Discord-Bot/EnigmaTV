package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    statusMessage: String?,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onGuest: () -> Unit,
    onSignOut: () -> Unit,
    onSync: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("signin") }
    var name by rememberSaveable { mutableStateOf(displayName) }
    var mail by rememberSaveable { mutableStateOf(email) }
    var pass by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Account", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Sync favorites & lists with Firebase", color = TextSecondary, fontSize = 13.sp)

        if (isLoggedIn) {
            Spacer(Modifier.height(20.dp))
            Text("Signed in as", color = TextSecondary, fontSize = 12.sp)
            Text(displayName.ifBlank { "EnigmaTV User" }, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(email, color = EnigmaPink, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSync,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Sync to cloud") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        } else {
            Spacer(Modifier.height(16.dp))
            RowModeTabs(mode) { mode = it }
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
                visualTransformation = PasswordVisualTransformation()
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
        error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFCC4444), modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun RowModeTabs(mode: String, onMode: (String) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterTab("Sign in", mode == "signin") { onMode("signin") }
        FilterTab("Sign up", mode == "signup") { onMode("signup") }
    }
}

@Composable
private fun FilterTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) EnigmaPurple else androidx.compose.ui.graphics.Color(0xFF222222)
        ),
        shape = RoundedCornerShape(8.dp)
    ) { Text(label) }
}

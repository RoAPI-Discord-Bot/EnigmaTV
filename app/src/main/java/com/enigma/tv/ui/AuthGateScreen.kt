package com.enigma.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.R
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

@Composable
fun AuthGateScreen(
    layout: ScreenLayout,
    loading: Boolean,
    error: String?,
    message: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    onGoogleSignIn: (String) -> Unit,
    onClearError: () -> Unit = {}
) {
    var mode by rememberSaveable { mutableStateOf("signin") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val firstFieldRequester = remember { FocusRequester() }
    LaunchedEffect(layout) {
        if (layout == ScreenLayout.TV) {
            kotlinx.coroutines.delay(300)
            runCatching { firstFieldRequester.requestFocus() }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val bgRes = if (layout == ScreenLayout.PHONE) R.drawable.bg_auth_phone else R.drawable.bg_auth_tv
    val bgAlignment = if (layout == ScreenLayout.PHONE) Alignment.CenterEnd else Alignment.Center

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(bgRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = bgAlignment
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.82f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.enigma_mark),
                contentDescription = ENIGMA_TV_BRAND,
                modifier = Modifier.size(if (layout == ScreenLayout.TV) 120.dp else 96.dp)
            )
            Text(
                ENIGMA_TV_BRAND,
                color = EnigmaPurple,
                fontSize = if (layout == ScreenLayout.TV) 36.sp else 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Text(
                "Movies · TV · Live",
                color = EnigmaPink.copy(alpha = 0.9f),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 28.dp)
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthTab("Sign in", mode == "signin") { mode = "signin" }
                    AuthTab("Sign up", mode == "signup") { mode = "signup" }
                }

                if (mode == "signup") {
                    AuthField(value = name, onValueChange = { name = it }, label = "Display name", modifier = Modifier.focusRequester(firstFieldRequester))
                    AuthField(value = email, onValueChange = { onClearError(); email = it }, label = "Email")
                } else {
                    AuthField(value = email, onValueChange = { onClearError(); email = it }, label = "Email", modifier = Modifier.focusRequester(firstFieldRequester))
                }
                var passwordFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = password,
                    onValueChange = { onClearError(); password = it },
                    label = { Text("Password") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { passwordFocused = it.isFocused }
                        .background(if (passwordFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(10.dp)),
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
                    },
                    colors = authFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )

                error?.let {
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }

                message?.let {
                    Text(it, color = Color(0xFF4CAF50), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }

                if (mode == "signin") {
                    androidx.compose.material3.TextButton(
                        onClick = { onResetPassword(email) },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Forgot password?", color = EnigmaPink, fontSize = 14.sp)
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                Button(
                    onClick = {
                        when (mode) {
                            "signup" -> onSignUp(email, password, name)
                            else -> onSignIn(email, password)
                        }
                    },
                    enabled = !loading && email.isNotBlank() && password.length >= 6,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (mode == "signup") "Create account" else "Sign in", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            com.enigma.tv.data.firebase.GoogleSignInHelper.getGoogleIdToken(context).onSuccess { idToken ->
                                onGoogleSignIn(idToken)
                            }.onFailure { e ->
                                // Could show toast or pass error
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Sign in with Google", color = TextPrimary)
                }
            }
        }

        if (loading) {
            EnigmaLoadingRing(
                fullscreen = true,
                message = "SIGNING IN",
                logoSize = 72.dp,
                ringSize = 100.dp
            )
        }
    }
}

@Composable
private fun RowScope.AuthTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) EnigmaPurple else Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f)
    ) { Text(label) }
}

@Composable
private fun AuthField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .background(if (isFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(10.dp)),
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        colors = authFieldColors(),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = EnigmaPurple,
    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
    focusedLabelColor = EnigmaPink,
    unfocusedLabelColor = TextSecondary,
    cursorColor = EnigmaPink
)

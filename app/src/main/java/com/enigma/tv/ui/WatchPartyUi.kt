package com.enigma.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

/**
 * A floating button that opens the Watch Party dialog.
 * Place this inside a Box over the player content.
 */
@Composable
fun WatchPartyButton(
    partyState: WatchPartyState,
    onShowDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = if (partyState.isActive) Icons.Default.Group else Icons.Default.GroupAdd
    val tint = if (partyState.isActive) EnigmaPink else TextSecondary

    IconButton(onClick = onShowDialog, modifier = modifier) {
        Icon(icon, contentDescription = "Watch Party", tint = tint, modifier = Modifier.size(24.dp))
    }

    if (partyState.isActive) {
        // Small pill showing member count and room code
        Box(
            modifier = modifier
                .offset(x = 24.dp, y = (-4).dp)
                .background(EnigmaPurple, RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                "${partyState.memberCount}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Dialog to host or join a watch party.
 */
@Composable
fun WatchPartyDialog(
    state: WatchPartyState,
    onHost: () -> Unit,
    onJoin: (String) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf<String?>(null) } // "host" | "join" | null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = {
            Text(
                "🎬 Watch Party",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.isActive) {
                    // Active party info
                    Text("You're in a party!", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(EnigmaPurple.copy(alpha = 0.2f))
                            .padding(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("Room Code", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                state.roomCode ?: "—",
                                color = EnigmaPink,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 8.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${state.memberCount} watching",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            if (state.isHost) {
                                Text("You are the host", color = EnigmaPurple, fontSize = 12.sp)
                            }
                        }
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                } else when (mode) {
                    "join" -> {
                        Text("Enter the 5-digit room code:", color = TextSecondary, fontSize = 13.sp)
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { if (it.length <= 5) codeInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Room Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EnigmaPurple,
                                cursorColor = EnigmaPurple
                            )
                        )
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                    "host" -> {
                        Text(
                            "A room code will be generated. Share it with friends so they can join!",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    else -> {
                        Text("Watch in sync with friends in real time.", color = TextSecondary, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { mode = "host" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EnigmaPurple)
                            ) { Text("Host Party") }
                            Button(
                                onClick = { mode = "join" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple)
                            ) { Text("Join Party") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.isActive) {
                Button(
                    onClick = { onLeave(); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))
                ) { Text("Leave Party") }
            } else when (mode) {
                "host" -> Button(
                    onClick = { onHost(); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple)
                ) { Text("Start Party") }
                "join" -> Button(
                    onClick = { if (codeInput.length == 5) { onJoin(codeInput); onDismiss() } },
                    enabled = codeInput.length == 5,
                    colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple)
                ) { Text("Join") }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (mode != null) mode = null else onDismiss()
            }) {
                Text(if (mode != null) "Back" else "Cancel", color = TextSecondary)
            }
        }
    )
}

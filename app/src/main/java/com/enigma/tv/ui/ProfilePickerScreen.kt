package com.enigma.tv.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onAddProfile: (String) -> Unit
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }

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

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0A0A), BgDark, Color(0xFF1A0A28))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (layout == ScreenLayout.TV) 48.dp else 32.dp))
            Text(
                ENIGMA_TV_BRAND,
                color = EnigmaPurple,
                fontSize = if (layout == ScreenLayout.TV) 36.sp else 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(if (layout == ScreenLayout.TV) 40.dp else 28.dp))
            Text(
                "Who's watching?",
                color = TextPrimary,
                fontSize = if (layout == ScreenLayout.TV) 42.sp else 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Select a profile to continue",
                color = TextSecondary,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(if (layout == ScreenLayout.TV) 32.dp else 24.dp))

            NetflixProfilePicker(
                profiles = profiles,
                activeProfileId = "",
                title = "",
                columns = columns,
                onSelect = { onSelectProfile(it.id) },
                onAddProfile = { showAddDialog = true },
                modifier = Modifier.weight(1f)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (layout == ScreenLayout.TV) 40.dp else 28.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Manage Profiles",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(8.dp)
                )
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
}

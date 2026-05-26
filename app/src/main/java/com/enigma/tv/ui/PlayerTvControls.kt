package com.enigma.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

data class TvPlayerControls(
    val seasons: List<Int>,
    val episodes: List<Pair<Int, String>>,
    val selectedSeason: Int,
    val selectedEpisode: Int,
    val onSeasonChange: (Int) -> Unit,
    val onEpisodeChange: (Int) -> Unit
)

/** Slide-up episode browser — only shown when user opens it; hides with player chrome. */
@Composable
fun TvEpisodePickerPanel(
    visible: Boolean,
    controls: TvPlayerControls,
    accent: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.95f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Seasons & Episodes", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close episodes", tint = TextSecondary, modifier = Modifier.size(28.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(max = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Seasons List
                LazyColumn(
                    modifier = Modifier.weight(0.35f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(controls.seasons, key = { it }) { s ->
                        val selected = s == controls.selectedSeason
                        var focused by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (focused) accent.copy(alpha = 0.8f) else if (selected) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f))
                                .clickable { controls.onSeasonChange(s) }
                                .focusable()
                                .onFocusChanged { focused = it.isFocused }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Season $s",
                                color = if (focused || selected) Color.White else TextSecondary,
                                fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // Episodes List
                LazyColumn(
                    modifier = Modifier.weight(0.65f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(controls.episodes, key = { it.first }) { (num, name) ->
                        val selected = num == controls.selectedEpisode
                        var focused by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (focused) accent.copy(alpha = 0.8f) else if (selected) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f))
                                .clickable {
                                    controls.onEpisodeChange(num)
                                    onDismiss()
                                }
                                .focusable()
                                .onFocusChanged { focused = it.isFocused }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "E$num",
                                color = if (focused || selected) Color.White else accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Text(
                                name,
                                color = if (focused) Color.White else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

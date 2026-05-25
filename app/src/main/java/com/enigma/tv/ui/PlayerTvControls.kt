package com.enigma.tv.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.ui.theme.TextPrimary

data class TvPlayerControls(
    val seasons: List<Int>,
    val episodes: List<Pair<Int, String>>,
    val selectedSeason: Int,
    val selectedEpisode: Int,
    val onSeasonChange: (Int) -> Unit,
    val onEpisodeChange: (Int) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonEpisodeDropdowns(controls: TvPlayerControls, accent: Color) {
    var seasonExpanded by remember { mutableStateOf(false) }
    var episodeExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = seasonExpanded,
        onExpandedChange = { seasonExpanded = it }
    ) {
        OutlinedButton(
            onClick = { seasonExpanded = true },
            modifier = Modifier.menuAnchor(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("S${controls.selectedSeason}", fontSize = 12.sp, color = TextPrimary)
        }
        ExposedDropdownMenu(expanded = seasonExpanded, onDismissRequest = { seasonExpanded = false }) {
            controls.seasons.forEach { s ->
                DropdownMenuItem(
                    text = { Text("Season $s") },
                    onClick = {
                        controls.onSeasonChange(s)
                        seasonExpanded = false
                    }
                )
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = episodeExpanded,
        onExpandedChange = { episodeExpanded = it }
    ) {
        OutlinedButton(
            onClick = { episodeExpanded = true },
            modifier = Modifier.menuAnchor(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("E${controls.selectedEpisode}", fontSize = 12.sp, color = TextPrimary)
        }
        ExposedDropdownMenu(expanded = episodeExpanded, onDismissRequest = { episodeExpanded = false }) {
            controls.episodes.forEach { (num, name) ->
                DropdownMenuItem(
                    text = { Text("Ep $num — $name", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        controls.onEpisodeChange(num)
                        episodeExpanded = false
                    }
                )
            }
        }
    }
}

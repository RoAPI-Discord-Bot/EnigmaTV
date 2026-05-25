package com.enigma.tv.ui

import androidx.compose.foundation.layout.heightIn
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
fun SeasonEpisodeDropdowns(controls: TvPlayerControls, accent: Color, large: Boolean = false) {
    var seasonExpanded by remember { mutableStateOf(false) }
    var episodeExpanded by remember { mutableStateOf(false) }
    val btnMinHeight = if (large) 48.dp else 40.dp
    val fontSize = if (large) 14.sp else 12.sp

    ExposedDropdownMenuBox(
        expanded = seasonExpanded,
        onExpandedChange = { seasonExpanded = it }
    ) {
        OutlinedButton(
            onClick = { seasonExpanded = true },
            modifier = Modifier
                .menuAnchor()
                .heightIn(min = btnMinHeight),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Season ${controls.selectedSeason}", fontSize = fontSize, color = TextPrimary)
        }
        ExposedDropdownMenu(expanded = seasonExpanded, onDismissRequest = { seasonExpanded = false }) {
            controls.seasons.forEach { s ->
                DropdownMenuItem(
                    text = { Text("Season $s", fontSize = if (large) 16.sp else 14.sp) },
                    onClick = {
                        controls.onSeasonChange(s)
                        seasonExpanded = false
                    },
                    modifier = Modifier.heightIn(min = if (large) 52.dp else 44.dp)
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
            modifier = Modifier
                .menuAnchor()
                .heightIn(min = btnMinHeight),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Episode ${controls.selectedEpisode}", fontSize = fontSize, color = TextPrimary)
        }
        ExposedDropdownMenu(expanded = episodeExpanded, onDismissRequest = { episodeExpanded = false }) {
            controls.episodes.forEach { (num, name) ->
                DropdownMenuItem(
                    text = { Text("Ep $num — $name", maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = if (large) 16.sp else 14.sp) },
                    onClick = {
                        controls.onEpisodeChange(num)
                        episodeExpanded = false
                    },
                    modifier = Modifier.heightIn(min = if (large) 52.dp else 44.dp)
                )
            }
        }
    }
}

package com.enigma.tv.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.MovieAccent
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private const val TAG_STREAM_URL = 0xE71A001

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayer(
    visible: Boolean,
    title: String,
    url: String,
    accent: Color,
    sourceLabel: String,
    onClose: () -> Unit,
    onNextSource: () -> Unit,
    tvControls: TvPlayerControls? = null
) {
    if (!visible) return

    var blockedNotice by remember { mutableStateOf<String?>(null) }
    val guard = remember {
        WebViewNavigationGuard(url).apply {
            onBlocked = { blocked ->
                blockedNotice = when {
                    blocked == "popup_window" -> "Blocked popup"
                    else -> "Blocked redirect"
                }
            }
        }
    }

    LaunchedEffect(blockedNotice) {
        if (blockedNotice != null) {
            delay(2500)
            blockedNotice = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                tvControls?.let { controls ->
                    SeasonEpisodeDropdowns(controls = controls, accent = accent)
                }

                Text(
                    text = sourceLabel,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Button(
                    onClick = onNextSource,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Text("Next Server", fontSize = 11.sp)
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }

            blockedNotice?.let { notice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MovieAccent.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MovieAccent,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                    Text(notice, color = TextPrimary, fontSize = 12.sp)
                }
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        guard.configureWebView(this)
                        setTag(TAG_STREAM_URL, url)
                        loadUrl(url)
                    }
                },
                update = { view ->
                    val last = view.getTag(TAG_STREAM_URL) as? String
                    if (last != url) {
                        view.setTag(TAG_STREAM_URL, url)
                        guard.resetForUrl(url)
                        view.loadUrl(url)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

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
private fun SeasonEpisodeDropdowns(controls: TvPlayerControls, accent: Color) {
    var seasonExpanded by remember { mutableStateOf(false) }
    var episodeExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = seasonExpanded,
        onExpandedChange = { seasonExpanded = it }
    ) {
        OutlinedButton(
            onClick = { seasonExpanded = true },
            modifier = Modifier.menuAnchor(),
            shape = RoundedCornerShape(4.dp)
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
            shape = RoundedCornerShape(4.dp)
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

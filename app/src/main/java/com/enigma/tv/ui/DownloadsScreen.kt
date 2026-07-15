package com.enigma.tv.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download

@OptIn(UnstableApi::class)
@Composable
fun DownloadsScreen(
    downloads: List<Download>,
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(16.dp)
    ) {
        Text(
            text = "Downloads",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No downloads yet",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(downloads) { download ->
                    DownloadItem(
                        download = download,
                        onPlay = onPlay,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun DownloadItem(
    download: Download,
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val title = try { String(download.request.data) } catch (e: Exception) { "Unknown" }
    val url = download.request.uri.toString()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (download.state == Download.STATE_COMPLETED) {
                    onPlay(url)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (download.state == Download.STATE_DOWNLOADING) {
                    val rawProgress = download.percentDownloaded
                    val progress = if (rawProgress > 0f) rawProgress / 100f else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFE50914),
                        trackColor = Color.DarkGray
                    )
                    Text(
                        text = "Downloading... ${if (rawProgress > 0f) rawProgress.toInt() else 0}%",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (download.state == Download.STATE_COMPLETED) {
                    Text(
                        text = "Completed",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp
                    )
                } else if (download.state == Download.STATE_FAILED) {
                    Text(
                        text = "Failed",
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = "Pending",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            if (download.state == Download.STATE_COMPLETED) {
                IconButton(onClick = { onPlay(url) }) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White
                    )
                }
            }
            
            IconButton(onClick = { onDelete(url) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Gray
                )
            }
        }
    }
}

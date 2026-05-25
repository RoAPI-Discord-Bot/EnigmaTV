package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

@Composable
fun PlayerChrome(
    title: String,
    subtitle: String,
    posterUrl: String? = null,
    accent: Color,
    onClose: () -> Unit,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    showNextSource: Boolean = false,
    onNextSource: (() -> Unit)? = null,
    tvControls: TvPlayerControls? = null,
    isTvLayout: Boolean = false,
    extraContent: @Composable (() -> Unit)? = null
) {
    val posterSize = if (isTvLayout) 52.dp else 44.dp
    val titleSize = if (isTvLayout) 18.sp else 15.sp
    val controlPadding = if (isTvLayout) 12.dp else 8.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.94f))
            .statusBarsPadding()
            .padding(horizontal = controlPadding, vertical = controlPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack && onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(if (isTvLayout) 52.dp else 48.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(28.dp))
                }
            }
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(posterSize)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .heightIn(min = if (isTvLayout) 48.dp else 40.dp)
            ) {
                Text(
                    title,
                    color = TextPrimary,
                    fontSize = titleSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    color = EnigmaPink,
                    fontSize = if (isTvLayout) 13.sp else 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (onRetry != null) {
                IconButton(onClick = onRetry, modifier = Modifier.size(if (isTvLayout) 52.dp else 48.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = TextSecondary)
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(if (isTvLayout) 52.dp else 48.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
            }
        }

        if (tvControls != null || showNextSource) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isTvLayout) 12.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isTvLayout) 12.dp else 8.dp)
            ) {
                tvControls?.let {
                    SeasonEpisodeDropdowns(controls = it, accent = accent, large = isTvLayout)
                }
                if (showNextSource && onNextSource != null) {
                    Button(
                        onClick = onNextSource,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = if (isTvLayout) 48.dp else 40.dp)
                    ) {
                        Text("Next Server", fontSize = if (isTvLayout) 14.sp else 12.sp)
                        Icon(
                            Icons.Default.FastForward,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 6.dp).size(18.dp)
                        )
                    }
                }
            }
        }
        extraContent?.invoke()
    }
}

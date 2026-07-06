package com.enigma.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.R
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaCyan
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextSecondary

@Composable
fun EnigmaLoadingRing(
    modifier: Modifier = Modifier,
    message: String? = null,
    logoSize: Dp = 64.dp,
    ringSize: Dp = 112.dp,
    fullscreen: Boolean = false
) {
    val infinite = rememberInfiniteTransition(label = "enigma_ring_spin")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    val boxModifier = if (fullscreen) {
        modifier.fillMaxSize().background(BgDark)
    } else {
        modifier
    }

    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(ringSize)
                        .graphicsLayer { rotationZ = rotation },
                    strokeWidth = 4.dp,
                    color = EnigmaPink,
                    trackColor = EnigmaCyan.copy(alpha = 0.15f)
                )
                Image(
                    painter = painterResource(R.drawable.enigma_mark),
                    contentDescription = ENIGMA_TV_BRAND,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(logoSize)
                        .padding(12.dp)
                )
            }
            message?.let {
                Text(
                    text = it,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
        }
    }
}

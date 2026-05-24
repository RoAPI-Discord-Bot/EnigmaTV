package com.enigma.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    logoSize: Dp = 96.dp,
    ringSize: Dp = 140.dp,
    fullscreen: Boolean = false
) {
    val infinite = rememberInfiniteTransition(label = "ring")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val boxModifier = if (fullscreen) {
        modifier.fillMaxSize().background(BgDark)
    } else {
        modifier
    }

    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
                Canvas(
                    modifier = Modifier
                        .size(ringSize)
                        .rotate(rotation)
                ) {
                    val stroke = 5.dp.toPx()
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(EnigmaCyan, EnigmaPurple, EnigmaPink, EnigmaCyan)
                        ),
                        startAngle = 0f,
                        sweepAngle = 300f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Image(
                    painter = painterResource(R.drawable.enigma_logo),
                    contentDescription = ENIGMA_TV_BRAND,
                    modifier = Modifier.size(logoSize)
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

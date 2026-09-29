package com.gideontek.phonetrack

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

enum class CircleAnimStyle { PULSE, ROTATING_ARC, RIPPLE }

private val CircleSize = 160.dp
private val StrokeWidth = 3.dp

/**
 * Marks an experimental, unused prototype composable — currently just [StatusCircle].
 *
 * [StreamStatusIndicator] is the status-circle component actually wired into
 * `MainActivity`. This one was an earlier alternative (pulse/rotating-arc/ripple styles
 * driven by raw subscription count rather than [StreamState]) kept around for reference
 * and possible future reuse, not for production use as-is — it has no callers outside
 * this file's own `@Preview`s.
 */
@RequiresOptIn(
    message = "StatusCircle is an experimental, unused prototype — see the kdoc on " +
        "ExperimentalStatusCircle for context before using it."
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalStatusCircle

@ExperimentalStatusCircle
@Composable
fun StatusCircle(
    subscriptions: List<Subscription>,
    style: CircleAnimStyle = CircleAnimStyle.PULSE,
    modifier: Modifier = Modifier
) {
    val active = subscriptions.isNotEmpty()

    val primaryColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val circleColor = if (active) primaryColor else idleColor

    // --- PULSE animation values ---
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (active && style == CircleAnimStyle.PULSE) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // --- ROTATING_ARC animation values ---
    val arcTransition = rememberInfiniteTransition(label = "arc")
    val arcAngle by arcTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (active && style == CircleAnimStyle.ROTATING_ARC) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arcAngle"
    )

    // --- RIPPLE animation values ---
    val ripple1Transition = rememberInfiniteTransition(label = "ripple1")
    val ripple1Scale by ripple1Transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active && style == CircleAnimStyle.RIPPLE) 1.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1Scale"
    )
    val ripple1Alpha by ripple1Transition.animateFloat(
        initialValue = if (active && style == CircleAnimStyle.RIPPLE) 0.5f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1Alpha"
    )

    val ripple2Transition = rememberInfiniteTransition(label = "ripple2")
    val ripple2Scale by ripple2Transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active && style == CircleAnimStyle.RIPPLE) 1.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 700),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2Scale"
    )
    val ripple2Alpha by ripple2Transition.animateFloat(
        initialValue = if (active && style == CircleAnimStyle.RIPPLE) 0.5f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 700),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2Alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
    ) {
        // Ripple rings (drawn behind the main circle)
        if (style == CircleAnimStyle.RIPPLE) {
            Box(
                modifier = Modifier
                    .size(CircleSize)
                    .graphicsLayer {
                        scaleX = ripple1Scale
                        scaleY = ripple1Scale
                        alpha = ripple1Alpha
                    }
                    .drawBehind {
                        drawCircle(
                            color = primaryColor,
                            style = Stroke(width = StrokeWidth.toPx())
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .size(CircleSize)
                    .graphicsLayer {
                        scaleX = ripple2Scale
                        scaleY = ripple2Scale
                        alpha = ripple2Alpha
                    }
                    .drawBehind {
                        drawCircle(
                            color = primaryColor,
                            style = Stroke(width = StrokeWidth.toPx())
                        )
                    }
            )
        }

        // Main circle + content
        Box(
            modifier = Modifier
                .size(CircleSize)
                .graphicsLayer {
                    if (style == CircleAnimStyle.PULSE) {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Static border circle + optional rotating arc overlay
            Canvas(modifier = Modifier.size(CircleSize)) {
                // Static ring
                drawCircle(
                    color = circleColor,
                    style = Stroke(width = StrokeWidth.toPx())
                )

                // Rotating arc overlay for ROTATING_ARC style
                if (style == CircleAnimStyle.ROTATING_ARC) {
                    drawArc(
                        color = primaryColor,
                        startAngle = arcAngle,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = StrokeWidth.toPx() * 2)
                    )
                }
            }

            // Text content
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (active) subscriptions.size.toString() else "·",
                    style = MaterialTheme.typography.headlineLarge,
                    color = circleColor
                )
                Text(
                    text = if (active) "ACTIVE" else "IDLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = circleColor
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val previewSub = Subscription(
    number = "+15550001234",
    distMeters = 200,
    freqMinutes = 15,
    durationHours = 4,
    subscribedAt = System.currentTimeMillis(),
    expiresAt = System.currentTimeMillis() + 4 * 3_600_000L,
    lastLat = 0.0,
    lastLon = 0.0,
    lastSentAt = System.currentTimeMillis()
)

@Preview(name = "Pulse – Active", showBackground = true)
@OptIn(ExperimentalStatusCircle::class)
@Composable
private fun PreviewPulseActive() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        StatusCircle(subscriptions = listOf(previewSub), style = CircleAnimStyle.PULSE)
    }
}

@Preview(name = "Pulse – Idle", showBackground = true)
@OptIn(ExperimentalStatusCircle::class)
@Composable
private fun PreviewPulseIdle() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        StatusCircle(subscriptions = emptyList(), style = CircleAnimStyle.PULSE)
    }
}

@Preview(name = "Arc – Active", showBackground = true)
@OptIn(ExperimentalStatusCircle::class)
@Composable
private fun PreviewArcActive() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        StatusCircle(subscriptions = listOf(previewSub), style = CircleAnimStyle.ROTATING_ARC)
    }
}

@Preview(name = "Arc – Idle", showBackground = true)
@OptIn(ExperimentalStatusCircle::class)
@Composable
private fun PreviewArcIdle() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        StatusCircle(subscriptions = emptyList(), style = CircleAnimStyle.ROTATING_ARC)
    }
}

@Preview(name = "Ripple – Active", showBackground = true)
@OptIn(ExperimentalStatusCircle::class)
@Composable
private fun PreviewRippleActive() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        StatusCircle(subscriptions = listOf(previewSub), style = CircleAnimStyle.RIPPLE)
    }
}

@Preview(name = "Ripple – Idle", showBackground = true)
@OptIn(ExperimentalStatusCircle::class)
@Composable
private fun PreviewRippleIdle() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        StatusCircle(subscriptions = emptyList(), style = CircleAnimStyle.RIPPLE)
    }
}

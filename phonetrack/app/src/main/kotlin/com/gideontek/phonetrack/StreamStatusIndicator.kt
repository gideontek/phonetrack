package com.gideontek.phonetrack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Represents the data-stream state shown by [StreamStatusIndicator].
 */
enum class StreamState {
    Inactive,
    Sending,
    Receiving,
    Both;

    val sendsOutbound: Boolean get() = this == Sending || this == Both
    val receivesInbound: Boolean get() = this == Receiving || this == Both
    val isActive: Boolean get() = this != Inactive
}

/**
 * Hollow-circle status indicator with animated waves showing data-stream activity.
 *
 * Visual behavior per state:
 * - [StreamState.Inactive]  : static hollow circle, no animation (no battery cost).
 * - [StreamState.Sending]   : waves pulse outward from the circle, fading as they travel.
 * - [StreamState.Receiving] : waves converge inward from far away, strengthening as they approach.
 * - [StreamState.Both]      : both wave sets animate simultaneously.
 *
 * The [content] slot is centered inside the hollow circle — put text, a counter, a peer count,
 * an icon, or anything else there.
 *
 * Minimal usage:
 * ```
 * var state by remember { mutableStateOf(StreamState.Inactive) }
 * StreamStatusIndicator(state = state) {
 *     Text("LIVE", fontWeight = FontWeight.Medium)
 * }
 * // Later, from anywhere: state = StreamState.Both
 * ```
 *
 * @param state Current stream state. Reassign to animate between modes.
 * @param modifier Layout modifier.
 * @param circleRadius Radius of the main hollow circle.
 * @param maxWaveRadius Maximum wave radius — also determines the component size (2× this).
 * @param strokeWidth Stroke thickness used for the circle and all waves.
 * @param waveCount Number of concurrent waves per direction (staggered in phase).
 * @param waveSpeed Wave cycles per second. 1f ≈ one wave every 2s. Higher = faster.
 * @param inactiveColor Circle color when inactive. Defaults to the theme's `secondary` color.
 * @param outboundColor Color for the sending-state circle and outward waves. Defaults to the
 *                       theme's `primary` color.
 * @param inboundColor Color for the inward waves. Defaults to the theme's `primary` color when
 *                      receiving alone, or `secondary` when bidirectional (so the two wave sets
 *                      stay visually distinct in [StreamState.Both]).
 * @param bothColor Circle color in the bidirectional state. Defaults to the theme's `primary` color.
 * @param content Composable placed at the center of the hollow circle.
 */
@Composable
fun StreamStatusIndicator(
    state: StreamState,
    modifier: Modifier = Modifier,
    circleRadius: Dp = 60.dp,
    maxWaveRadius: Dp = 100.dp,
    strokeWidth: Dp = 3.dp,
    waveCount: Int = 1,
    waveSpeed: Float = 1f,
    inactiveColor: Color = MaterialTheme.colorScheme.secondary,
    outboundColor: Color = MaterialTheme.colorScheme.primary,
    inboundColor: Color = if (state == StreamState.Both) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
    bothColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit = {}
) {
    var progress by remember { mutableStateOf(0f) }

    // Drive the animation only when active. When Inactive, the coroutine is cancelled
    // and the Canvas stops invalidating — zero per-frame cost.
    LaunchedEffect(state.isActive, waveSpeed) {
        if (!state.isActive) {
            progress = 0f
            return@LaunchedEffect
        }
        val periodMs = (2000f / waveSpeed).toLong().coerceAtLeast(100L)
        val startFrame = withFrameMillis { it }
        while (true) {
            val frameTime = withFrameMillis { it }
            val elapsed = frameTime - startFrame
            progress = (elapsed % periodMs).toFloat() / periodMs
        }
    }

    val circleColor = when (state) {
        StreamState.Inactive -> inactiveColor
        StreamState.Sending, StreamState.Receiving -> outboundColor
        StreamState.Both -> bothColor
    }

    Box(
        modifier = modifier.size(maxWaveRadius * 2),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val circleR = circleRadius.toPx()
            val maxR = maxWaveRadius.toPx()
            val stroke = strokeWidth.toPx()
            val span = (maxR - circleR).coerceAtLeast(0f)

            if (state.sendsOutbound) {
                for (i in 0 until waveCount) {
                    val phase = i.toFloat() / waveCount
                    val p = (progress + phase) % 1f
                    val r = circleR + p * span
                    val alpha = (1f - p) * 0.8f
                    drawCircle(
                        color = outboundColor.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = stroke)
                    )
                }
            }

            if (state.receivesInbound) {
                for (i in 0 until waveCount) {
                    val phase = i.toFloat() / waveCount
                    val p = (progress + phase) % 1f
                    val r = maxR - p * span
                    val alpha = p * 0.8f
                    drawCircle(
                        color = inboundColor.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = stroke)
                    )
                }
            }

            drawCircle(
                color = circleColor,
                radius = circleR,
                center = center,
                style = Stroke(width = stroke + 1f)
            )
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------
// Demo / preview — safe to delete or move to your test module
// ---------------------------------------------------------------------------------------

/** Demo composable with state-toggle buttons. Useful for manual testing on-device. */
@Composable
fun StreamStatusDemo() {
    var state by remember { mutableStateOf(StreamState.Inactive) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StreamStatusIndicator(
                state = state,
                circleRadius = 70.dp,
                maxWaveRadius = 160.dp,
                waveCount = 3,
                waveSpeed = 1f
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (state) {
                            StreamState.Inactive -> "IDLE"
                            StreamState.Sending -> "TX"
                            StreamState.Receiving -> "RX"
                            StreamState.Both -> "TX/RX"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "2 peers",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamState.values().forEach { option ->
                    if (state == option) {
                        Button(onClick = { state = option }) { Text(option.name) }
                    } else {
                        OutlinedButton(onClick = { state = option }) { Text(option.name) }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun StreamStatusIndicatorPreview() {
    MaterialTheme {
        StreamStatusDemo()
    }
}

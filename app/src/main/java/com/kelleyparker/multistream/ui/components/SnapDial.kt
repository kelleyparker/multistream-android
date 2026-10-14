package com.kelleyparker.multistream.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kelleyparker.multistream.core.BitrateDial
import com.kelleyparker.multistream.core.DialZone
import com.kelleyparker.multistream.ui.brandColor
import com.kelleyparker.multistream.ui.mark
import com.kelleyparker.multistream.ui.theme.LocalStatusColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP = 270f

/**
 * The bitrate dial: drag around the arc or tap −/+ to move in 500 kbps steps. The arc is amber
 * below the recommended bitrate and green above it, stops hard at the strictest platform's
 * limit, and marks each platform's own maximum with its badge.
 */
@Composable
fun SnapDial(
  dial: BitrateDial,
  valueKbps: Int,
  onValueChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  label: String = "Video bitrate",
  enabled: Boolean = true,
  dialSize: Dp = 220.dp,
) {
  val colors = LocalStatusColors.current
  val haptics = LocalHapticFeedback.current
  val value = dial.snap(valueKbps)
  val currentValue by rememberUpdatedState(value)
  val currentOnChange by rememberUpdatedState(onValueChange)
  val track = MaterialTheme.colorScheme.surfaceVariant
  val thumb = MaterialTheme.colorScheme.primary
  val textMeasurer = rememberTextMeasurer()
  val zone = dial.zone(value)

  fun emit(next: Int) {
    if (next != currentValue) {
      haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      currentOnChange(next)
    }
  }

  fun valueAt(offset: Offset, size: Size): Int {
    val center = Offset(size.width / 2f, size.height / 2f)
    val degrees = Math.toDegrees(atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())).toFloat()
    val relative = ((degrees - START_ANGLE) % 360f + 360f) % 360f
    val fraction = if (relative <= SWEEP) relative / SWEEP else if (relative > (SWEEP + 360f) / 2f) 0f else 1f
    return dial.fromFraction(fraction)
  }

  Column(modifier.alpha(if (enabled) 1f else 0.5f), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(contentAlignment = Alignment.Center) {
      Canvas(
        Modifier
          .size(dialSize)
          .semantics {
            contentDescription = label
            stateDescription = "$value kbps, ${if (zone == DialZone.GOOD) "recommended" else "below recommended"}"
            progressBarRangeInfo = ProgressBarRangeInfo(
              value.toFloat(),
              dial.minKbps.toFloat()..dial.maxKbps.toFloat(),
              steps = ((dial.maxKbps - dial.minKbps) / dial.step - 1).coerceAtLeast(0),
            )
            if (enabled) {
              setProgress { target ->
                emit(dial.snap(target.toInt()))
                true
              }
            }
          }
          .then(
            if (enabled) {
              Modifier
                .pointerInput(dial) { detectTapGestures { emit(valueAt(it, Size(size.width.toFloat(), size.height.toFloat()))) } }
                .pointerInput(dial) {
                  detectDragGestures { change, _ ->
                    emit(valueAt(change.position, Size(size.width.toFloat(), size.height.toFloat())))
                  }
                }
            } else {
              Modifier
            },
          ),
      ) {
        val stroke = size.minDimension * 0.08f
        val inset = stroke * 1.6f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        val recommendedFraction = dial.fraction(dial.recommendedKbps)

        // Zones: amber below the recommendation, green from there to the limit.
        drawArc(track, START_ANGLE, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(colors.low.copy(alpha = 0.35f), START_ANGLE, SWEEP * recommendedFraction, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(colors.good.copy(alpha = 0.35f), START_ANGLE + SWEEP * recommendedFraction, SWEEP * (1 - recommendedFraction), false, topLeft, arcSize, style = Stroke(stroke))
        // Progress up to the current value.
        val valueFraction = dial.fraction(value)
        drawArc(
          if (zone == DialZone.GOOD) colors.good else colors.low,
          START_ANGLE, SWEEP * valueFraction, false, topLeft, arcSize,
          style = Stroke(stroke * 0.55f, cap = StrokeCap.Round),
        )

        val radius = arcSize.width / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        fun pointAt(fraction: Float, r: Float): Offset {
          val angle = Math.toRadians((START_ANGLE + SWEEP * fraction).toDouble())
          return Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
        }

        // Platform limit markers, drawn as small badges outside the arc.
        for (marker in dial.markers) {
          if (marker.kbps < dial.minKbps || marker.kbps > dial.maxKbps) continue
          val at = pointAt(dial.fraction(marker.kbps), radius + stroke * 1.25f)
          val badge = stroke * 0.9f
          drawCircle(marker.platform.brandColor, badge, at)
          drawMark(textMeasurer, marker.platform.mark, at, badge)
        }

        // Thumb.
        drawCircle(thumb, stroke * 0.75f, pointAt(valueFraction, radius))
        drawCircle(Color.White, stroke * 0.35f, pointAt(valueFraction, radius))
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("kbps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          if (zone == DialZone.GOOD) "Recommended" else "Low",
          style = MaterialTheme.typography.labelMedium,
          color = if (zone == DialZone.GOOD) colors.good else colors.low,
        )
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(top = 4.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FilledTonalIconButton(onClick = { emit(dial.decrement(value)) }, enabled = enabled && value > dial.minKbps, modifier = Modifier.size(56.dp)) {
        Icon(Icons.Filled.Remove, contentDescription = "Lower bitrate by ${dial.step} kbps")
      }
      Text(
        "${dial.minKbps}–${dial.maxKbps} kbps",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FilledTonalIconButton(onClick = { emit(dial.increment(value)) }, enabled = enabled && value < dial.maxKbps, modifier = Modifier.size(56.dp)) {
        Icon(Icons.Filled.Add, contentDescription = "Raise bitrate by ${dial.step} kbps")
      }
    }
  }
}

private fun DrawScope.drawMark(textMeasurer: androidx.compose.ui.text.TextMeasurer, mark: String, center: Offset, radius: Float) {
  val fontPx = min(radius * if (mark.length > 2) 0.7f else 1.0f, 40f)
  val layout = textMeasurer.measure(
    mark,
    TextStyle(color = Color.White, fontSize = (fontPx / density).sp, fontWeight = FontWeight.Bold),
  )
  drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

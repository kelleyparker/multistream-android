package com.kelleyparker.multistream.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kelleyparker.multistream.core.Destination
import com.kelleyparker.multistream.core.DestinationStatus
import com.kelleyparker.multistream.core.formatMbps
import com.kelleyparker.multistream.ui.UiState
import com.kelleyparker.multistream.ui.components.Caption
import com.kelleyparker.multistream.ui.components.PlatformBadge
import com.kelleyparker.multistream.ui.components.StatusLight
import com.kelleyparker.multistream.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay

/** Callbacks for the Live screen, grouped so previews and tests can pass no-ops. */
data class LiveActions(
  val onGoLive: () -> Unit = {},
  val onStopAll: () -> Unit = {},
  val onStopOne: (String) -> Unit = {},
  val onRetryOne: (String) -> Unit = {},
  val onToggleMute: () -> Unit = {},
  val onSwitchCamera: () -> Unit = {},
  val onOpenPlatforms: () -> Unit = {},
  val onOpenQuality: () -> Unit = {},
  val onRequestPermissions: () -> Unit = {},
)

/**
 * The console: a 16:9 preview (exactly what is encoded) beside one card per platform with a
 * status light, and big buttons for Go Live, mute and camera flip.
 */
@Composable
fun LiveScreen(
  state: UiState,
  permissionsGranted: Boolean,
  actions: LiveActions,
  preview: @Composable (Modifier) -> Unit,
) {
  var countdown by remember { mutableStateOf<Int?>(null) }
  var confirmStop by remember { mutableStateOf(false) }
  var confirmWarnings by remember { mutableStateOf(false) }
  val haptics = LocalHapticFeedback.current

  LaunchedEffect(countdown) {
    val n = countdown ?: return@LaunchedEffect
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    delay(1_000)
    if (n > 1) {
      countdown = n - 1
    } else {
      countdown = null
      actions.onGoLive()
    }
  }

  val onGoLiveTapped: () -> Unit = {
    if (state.validation.warnings.isNotEmpty()) {
      confirmWarnings = true
    } else {
      countdown = 3
    }
  }

  Surface(Modifier.fillMaxSize(), color = Color.Black) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
      val landscape = maxWidth > maxHeight
      val previewBox = @Composable { modifier: Modifier ->
        PreviewArea(state, permissionsGranted, countdown, actions, preview, modifier)
      }
      val panel = @Composable { modifier: Modifier ->
        ControlPanel(state, actions, onGoLiveTapped, onStop = { confirmStop = true }, modifier = modifier)
      }
      if (landscape) {
        Row(Modifier.fillMaxSize()) {
          previewBox(Modifier.weight(1f).fillMaxHeight())
          panel(Modifier.width(340.dp).fillMaxHeight())
        }
      } else {
        Column(Modifier.fillMaxSize()) {
          previewBox(Modifier.fillMaxWidth())
          panel(Modifier.weight(1f).fillMaxWidth())
        }
      }
    }
  }

  if (confirmStop) {
    AlertDialog(
      onDismissRequest = { confirmStop = false },
      title = { Text("End the stream?") },
      text = { Text("All platforms will stop.") },
      confirmButton = { TextButton(onClick = { confirmStop = false; actions.onStopAll() }) { Text("End stream") } },
      dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep streaming") } },
    )
  }
  if (confirmWarnings) {
    AlertDialog(
      onDismissRequest = { confirmWarnings = false },
      title = { Text("Before you go live") },
      text = { Text(state.validation.warnings.joinToString("\n\n") { it.message }) },
      confirmButton = { TextButton(onClick = { confirmWarnings = false; countdown = 3 }) { Text("Go live anyway") } },
      dismissButton = { TextButton(onClick = { confirmWarnings = false; actions.onOpenQuality() }) { Text("Adjust quality") } },
    )
  }
}

@Composable
private fun PreviewArea(
  state: UiState,
  permissionsGranted: Boolean,
  countdown: Int?,
  actions: LiveActions,
  preview: @Composable (Modifier) -> Unit,
  modifier: Modifier,
) {
  Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
    Box(Modifier.aspectRatio(16f / 9f).background(Color(0xFF101014)), contentAlignment = Alignment.Center) {
      when {
        !permissionsGranted -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
          Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
          Text("Allow the camera and microphone to see your preview.", color = Color.White, modifier = Modifier.padding(vertical = 12.dp))
          Button(onClick = actions.onRequestPermissions, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow camera & mic") }
        }
        else -> preview(Modifier.fillMaxSize())
      }
      if (state.isLive) {
        Row(
          Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xCCD7263D), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
          Text(" LIVE", color = Color.White, fontWeight = FontWeight.Bold)
        }
      }
      state.engineError?.let {
        Text(
          it,
          color = Color.White,
          modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).background(Color(0xCC000000), RoundedCornerShape(8.dp)).padding(8.dp),
        )
      }
      AnimatedVisibility(countdown != null) {
        Box(Modifier.size(140.dp).background(Color(0xAA000000), CircleShape), contentAlignment = Alignment.Center) {
          Text("${countdown ?: ""}", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun ControlPanel(
  state: UiState,
  actions: LiveActions,
  onGoLive: () -> Unit,
  onStop: () -> Unit,
  modifier: Modifier,
) {
  Surface(modifier, color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
        val measured = state.statuses.values.filterIsInstance<DestinationStatus.Live>().sumOf { it.bitrateKbps }
        Text(
          if (state.isLive && measured > 0) " Sending ${formatMbps(measured).removeSuffix(" Mbps")} of ~${formatMbps(state.plan.uploadKbps)}"
          else " Needs ~${formatMbps(state.plan.uploadKbps)} upload",
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions.onOpenPlatforms, enabled = !state.isLive) { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Platforms") }
        IconButton(onClick = actions.onOpenQuality, enabled = !state.isLive) { Icon(Icons.Filled.Tune, contentDescription = "Quality") }
      }
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val active = state.destinations.filter { it.enabled }
        if (active.isEmpty()) Caption("No platforms are on. Tap the list icon to turn some on.")
        for (destination in active) DestinationCard(state, destination, actions)
        for (issue in state.validation.errors) {
          Text(issue.message, style = MaterialTheme.typography.bodySmall, color = LocalStatusColors.current.failed)
        }
      }
      Spacer(Modifier.size(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = actions.onToggleMute, modifier = Modifier.size(56.dp)) {
          Icon(if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = if (state.muted) "Unmute microphone" else "Mute microphone")
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = actions.onSwitchCamera, modifier = Modifier.size(56.dp)) {
          Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera")
        }
        Spacer(Modifier.width(12.dp))
        if (state.isLive) {
          OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Text(" End stream", style = MaterialTheme.typography.titleMedium)
          }
        } else {
          Button(
            onClick = onGoLive,
            enabled = state.validation.canGoLive && state.engineError == null,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD7263D), contentColor = Color.White),
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
          ) {
            Icon(Icons.Filled.FiberManualRecord, contentDescription = null)
            Text(" Go Live", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun DestinationCard(state: UiState, destination: Destination, actions: LiveActions) {
  val status = state.statusOf(destination.id)
  val tier = state.plan.tierOf(destination.id)
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      PlatformBadge(destination.platform, 40.dp)
      Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(destination.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          tier?.let {
            Text(
              " ${it.format.label}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusLight(status)
        if (status is DestinationStatus.Failed) Caption(status.reason, color = LocalStatusColors.current.failed)
        if (status is DestinationStatus.Reconnecting) Caption(status.reason)
      }
      when {
        status is DestinationStatus.Failed || (state.isLive && status == DestinationStatus.Idle) ->
          IconButton(onClick = { actions.onRetryOne(destination.id) }) { Icon(Icons.Filled.Refresh, contentDescription = "Reconnect ${destination.label}") }
        status !is DestinationStatus.Idle ->
          IconButton(onClick = { actions.onStopOne(destination.id) }) { Icon(Icons.Filled.Stop, contentDescription = "Stop ${destination.label} only") }
      }
    }
  }
}

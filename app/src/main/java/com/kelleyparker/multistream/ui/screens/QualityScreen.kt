@file:OptIn(ExperimentalMaterial3Api::class)

package com.kelleyparker.multistream.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kelleyparker.multistream.core.AudioBitrate
import com.kelleyparker.multistream.core.BitrateDial
import com.kelleyparker.multistream.core.CustomQuality
import com.kelleyparker.multistream.core.FrameRate
import com.kelleyparker.multistream.core.NoteKind
import com.kelleyparker.multistream.core.OptionRules
import com.kelleyparker.multistream.core.Platform
import com.kelleyparker.multistream.core.PlatformLimits
import com.kelleyparker.multistream.core.QualityPreset
import com.kelleyparker.multistream.core.Resolution
import com.kelleyparker.multistream.core.Severity
import com.kelleyparker.multistream.core.TierPlanner
import com.kelleyparker.multistream.core.TierSettings
import com.kelleyparker.multistream.core.formatMbps
import com.kelleyparker.multistream.core.limits
import com.kelleyparker.multistream.ui.UiState
import com.kelleyparker.multistream.ui.components.Caption
import com.kelleyparker.multistream.ui.components.ChipPicker
import com.kelleyparker.multistream.ui.components.SegmentedPicker
import com.kelleyparker.multistream.ui.components.Section
import com.kelleyparker.multistream.ui.components.SnapDial
import com.kelleyparker.multistream.ui.theme.LocalStatusColors
import com.kelleyparker.multistream.core.QualitySettings

/**
 * Quality is chosen with big preset cards that spell out what each platform will get. Custom
 * opens buttons, chips and the bitrate dial, never number fields.
 */
@Composable
fun QualityScreen(state: UiState, onBack: () -> Unit, onChange: (QualitySettings) -> Unit) {
  val quality = state.quality
  val enabled = !state.isLive
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Quality") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to camera") } },
      )
    },
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.isLive) Caption("Stop streaming to change quality.")
        for (preset in QualityPreset.entries) {
          PresetCard(state, preset, selected = quality.preset == preset, enabled = enabled) { onChange(quality.copy(preset = preset)) }
        }
        if (quality.preset == QualityPreset.CUSTOM) CustomPanel(state, enabled, onChange)
        Section(
          "Audio",
          help = "Shared by every platform: AAC stereo at 48 kHz. 128 kbps is right for voice and most music.",
        ) {
          ChipPicker(
            options = AudioBitrate.entries,
            selected = quality.audio,
            label = { it.label },
            onSelect = { onChange(quality.copy(audio = it)) },
            disabledReason = { OptionRules.audioReason(it, state.destinations.filter { d -> d.enabled }.map { d -> d.platform.limits }) },
            enabled = enabled,
          )
        }
        PlanFeedback(state)
      }
    }
  }
}

@Composable
private fun PresetCard(state: UiState, preset: QualityPreset, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
  val plan = TierPlanner.plan(state.destinations, state.quality.copy(preset = preset), state.caps)
  val perPlatform = plan.tiers.flatMap { tier -> tier.destinations.map { "${it.platform.displayName} ${tier.format.label}" } }.distinct()
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
    shape = RoundedCornerShape(20.dp),
    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    colors = CardDefaults.cardColors(
      containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ),
  ) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      RadioButton(selected = selected, onClick = null, enabled = enabled)
      Column(Modifier.padding(start = 12.dp).weight(1f)) {
        Text(preset.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(preset.subtitle, style = MaterialTheme.typography.bodyMedium)
        if (perPlatform.isNotEmpty()) {
          Spacer(Modifier.size(6.dp))
          Text(perPlatform.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
              " Needs about ${formatMbps(plan.uploadKbps)} upload",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CustomPanel(state: UiState, enabled: Boolean, onChange: (QualitySettings) -> Unit) {
  val quality = state.quality
  val custom = quality.custom
  val active = state.destinations.filter { it.enabled }
  val hasYoutube = active.any { it.platform == Platform.YOUTUBE }
  val hasOthers = active.any { it.platform != Platform.YOUTUBE }
  val youtubeTierAvailable = hasYoutube && hasOthers && state.caps.supportsDualTier
  val youtubeSeparate = youtubeTierAvailable && custom.youtubeOwnTier
  val mainMembers = (if (youtubeSeparate) active.filter { it.platform != Platform.YOUTUBE } else active)
    .map { it.platform.limits }.distinct()
  fun update(next: CustomQuality) = onChange(quality.copy(custom = next))

  Section(
    "Frame rate",
    help = "60 fps looks smoother for games and sports. Every platform shares the same frame rate because they share one camera.",
  ) {
    ChipPicker(
      options = FrameRate.entries,
      selected = custom.main.format.frameRate,
      label = { it.label },
      onSelect = { fps ->
        update(
          custom.copy(
            main = custom.main.copy(format = custom.main.format.copy(frameRate = fps)),
            youtube = custom.youtube.copy(format = custom.youtube.format.copy(frameRate = fps)),
          ),
        )
      },
      enabled = enabled,
    )
  }

  if (youtubeTierAvailable) {
    Section(
      "Give YouTube its own quality",
      help = "Your phone can run two encoders. YouTube can then get up to 1440p while Twitch and Kick get the 1080p they support.",
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Caption("YouTube is the only platform that accepts 1440p.", modifier = Modifier.weight(1f))
        Switch(checked = custom.youtubeOwnTier, onCheckedChange = { update(custom.copy(youtubeOwnTier = it)) }, enabled = enabled)
      }
    }
  }

  TierControls(
    title = if (youtubeSeparate) "Twitch, Kick & others" else "All platforms",
    settings = custom.main,
    members = mainMembers,
    state = state,
    enabled = enabled,
    onChange = { update(custom.copy(main = it)) },
  )
  if (youtubeSeparate) {
    TierControls(
      title = "YouTube",
      settings = custom.youtube,
      members = listOf(PlatformLimits.YOUTUBE),
      state = state,
      enabled = enabled,
      onChange = { update(custom.copy(youtube = it)) },
    )
  }
}

@Composable
private fun TierControls(
  title: String,
  settings: TierSettings,
  members: List<PlatformLimits>,
  state: UiState,
  enabled: Boolean,
  onChange: (TierSettings) -> Unit,
) {
  val dial = BitrateDial.forMembers(members, settings.format)
  Section(
    title,
    help = "Higher resolution and bitrate look sharper but need more upload. The dial stops at the strictest platform's limit and is green from the recommended bitrate up.",
  ) {
    Text("Resolution", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.size(8.dp))
    SegmentedPicker(
      options = Resolution.entries,
      selected = settings.format.resolution,
      label = { it.label },
      onSelect = { resolution ->
        val format = settings.format.copy(resolution = resolution)
        onChange(TierSettings(format, BitrateDial.forMembers(members, format).let { it.snap(maxOf(settings.videoKbps, it.recommendedKbps)) }))
      },
      disabledReason = { OptionRules.resolutionReason(it, members, state.caps, settings.format.frameRate) },
      enabled = enabled,
    )
    Spacer(Modifier.size(16.dp))
    SnapDial(
      dial = dial,
      valueKbps = settings.videoKbps,
      onValueChange = { onChange(settings.copy(videoKbps = it)) },
      enabled = enabled,
      modifier = Modifier.fillMaxWidth(),
    )
    val outside = members.filter { it.platform != Platform.CUSTOM && it.maxVideoKbps > dial.maxKbps }
    if (outside.isNotEmpty() && members.size > 1) {
      val limiter = members.minBy { it.maxVideoKbps }.platform.displayName
      Caption(
        outside.joinToString(" ") { "${it.platform.displayName} could take ${it.maxVideoKbps} kbps, but shares an encoder capped by $limiter." },
        modifier = Modifier.padding(top = 8.dp),
      )
    }
  }
}

/** Notes from the planner plus anything that blocks or deserves a second look. */
@Composable
fun PlanFeedback(state: UiState, modifier: Modifier = Modifier) {
  val colors = LocalStatusColors.current
  val items = state.plan.notes.map { (if (it.kind == NoteKind.WARNING) colors.low else Color.Unspecified) to it.text } +
    state.validation.issues.map { (if (it.severity == Severity.ERROR) colors.failed else colors.low) to it.message }
  if (items.isEmpty()) return
  Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    for ((color, text) in items) {
      Row(verticalAlignment = Alignment.Top) {
        Icon(
          when (color) {
            colors.failed -> Icons.Filled.ErrorOutline
            colors.low -> Icons.Outlined.WarningAmber
            else -> Icons.Outlined.Info
          },
          contentDescription = null,
          tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text,
          style = MaterialTheme.typography.bodyMedium,
          color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
          modifier = Modifier.padding(start = 8.dp),
        )
      }
    }
  }
}

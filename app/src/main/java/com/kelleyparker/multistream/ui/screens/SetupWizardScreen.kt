package com.kelleyparker.multistream.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kelleyparker.multistream.core.Destination
import com.kelleyparker.multistream.core.Platform
import com.kelleyparker.multistream.ui.CheckState
import com.kelleyparker.multistream.ui.components.PlatformBadge
import com.kelleyparker.multistream.ui.components.PlatformTile
import com.kelleyparker.multistream.ui.tagline
import java.util.UUID

/**
 * First run: tap the platforms you use, paste each stream key, done. The only typing in the
 * whole app happens on the per-platform steps.
 */
@Composable
fun SetupWizardScreen(
  initial: List<Destination>,
  checks: Map<String, CheckState>,
  onCheck: (Destination) -> Unit,
  onFinish: (List<Destination>) -> Unit,
  startStep: Int = 0,
) {
  var drafts by remember {
    mutableStateOf(
      initial.ifEmpty {
        listOf(Platform.YOUTUBE, Platform.TWITCH, Platform.KICK).map { Destination(UUID.randomUUID().toString(), it, enabled = false) }
      },
    )
  }
  var step by remember { mutableIntStateOf(startStep) }
  val chosen = drafts.filter { it.enabled }
  val totalSteps = chosen.size + 2

  BackHandler(enabled = step > 0) { step-- }

  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      LinearProgressIndicator(
        progress = { (step + 1f) / totalSteps },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      )
      Column(
        Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        when {
          step == 0 -> PickPlatforms(drafts) { platform ->
            drafts = if (platform == Platform.CUSTOM && drafts.none { it.platform == Platform.CUSTOM }) {
              drafts + Destination(UUID.randomUUID().toString(), Platform.CUSTOM)
            } else {
              drafts.map { if (it.platform == platform) it.copy(enabled = !it.enabled) else it }
            }
          }
          step <= chosen.size -> {
            val current = chosen[step - 1]
            Text("Step $step of ${chosen.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlatformForm(
              destination = current,
              onChange = { updated -> drafts = drafts.map { if (it.id == updated.id) updated else it } },
              check = checks[current.id],
              onCheck = { onCheck(current) },
            )
            MissingSetupText(current)
          }
          else -> Summary(chosen)
        }
      }
      Row(
        Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (step > 0) TextButton(onClick = { step-- }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
        Spacer(Modifier.weight(1f))
        val current = chosen.getOrNull(step - 1)
        val canContinue = when {
          step == 0 -> chosen.isNotEmpty()
          current != null -> current.missingSetup() == null
          else -> true
        }
        Button(
          onClick = {
            if (step >= totalSteps - 1) onFinish(drafts.filter { it.enabled || it.streamKey.isNotBlank() }) else step++
          },
          enabled = canContinue,
          modifier = Modifier.heightIn(min = 56.dp),
        ) {
          Text(if (step >= totalSteps - 1) "Start streaming" else "Continue", style = MaterialTheme.typography.titleMedium)
        }
      }
    }
  }
}

@Composable
private fun PickPlatforms(drafts: List<Destination>, onToggle: (Platform) -> Unit) {
  Text("Where do you want to go live?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
  Text(
    "Pick every platform. Your phone streams to all of them at once, directly, for free.",
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  for (platform in Platform.entries) {
    val selected = drafts.any { it.platform == platform && it.enabled }
    PlatformTile(platform, platform.tagline, selected, onToggle = { onToggle(platform) }, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun Summary(chosen: List<Destination>) {
  Text("You're all set", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
  Text(
    "Next you'll see the camera. Pick a quality preset any time from Quality, then tap Go Live.",
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  for (destination in chosen) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      PlatformBadge(destination.platform, 40.dp)
      Text(destination.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
    }
  }
  Spacer(Modifier.size(8.dp))
}

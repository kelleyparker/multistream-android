package com.kelleyparker.multistream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kelleyparker.multistream.core.Destination
import com.kelleyparker.multistream.core.Platform
import com.kelleyparker.multistream.core.UrlScheme
import com.kelleyparker.multistream.core.limits
import com.kelleyparker.multistream.core.schemeOf
import com.kelleyparker.multistream.ui.CheckState
import com.kelleyparker.multistream.ui.components.Caption
import com.kelleyparker.multistream.ui.components.ChipPicker
import com.kelleyparker.multistream.ui.components.PasteField
import com.kelleyparker.multistream.ui.components.PlatformBadge
import com.kelleyparker.multistream.ui.tagline
import com.kelleyparker.multistream.ui.theme.LocalStatusColors

private const val OTHER_SERVER = "__other__"

/** What still blocks this destination, in plain language, or null when it's ready. */
fun Destination.missingSetup(): String? = when {
  platform != Platform.CUSTOM && streamKey.isBlank() -> "Paste your ${platform.displayName} stream key."
  schemeOf(serverUrl) == UrlScheme.INVALID ->
    if (platform == Platform.KICK) "Paste the Stream URL from your Kick dashboard." else "Enter an rtmp:// or rtmps:// server address."
  platform.limits.requiresRtmps && schemeOf(serverUrl) != UrlScheme.RTMPS -> "${platform.displayName} needs an rtmps:// address."
  else -> null
}

/**
 * Everything needed to set up one platform: where to find the key, a server picker (no typing for
 * YouTube and Twitch), the stream key with a Paste button, and a safe connection check.
 */
@Composable
fun PlatformForm(
  destination: Destination,
  onChange: (Destination) -> Unit,
  check: CheckState?,
  onCheck: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val limits = destination.platform.limits
  val uriHandler = LocalUriHandler.current
  Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      PlatformBadge(destination.platform, 56.dp)
      Column(Modifier.padding(start = 16.dp)) {
        Text(destination.platform.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(destination.platform.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
      Column(Modifier.padding(16.dp)) {
        Text("Where to find your key", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(4.dp))
        Text(limits.keyHelpText, style = MaterialTheme.typography.bodyMedium)
        limits.keyHelpUrl?.let { url ->
          Spacer(Modifier.size(8.dp))
          OutlinedButton(onClick = { runCatching { uriHandler.openUri(url) } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Public, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open ${destination.platform.displayName} dashboard")
          }
        }
      }
    }

    ServerPicker(destination, onChange)

    PasteField(
      label = if (destination.platform == Platform.KICK) "Stream Key" else "Stream key",
      value = destination.streamKey,
      onValueChange = { onChange(destination.copy(streamKey = it)) },
      secret = true,
      supportingText = if (destination.platform == Platform.CUSTOM) "Optional. Leave empty if the address already includes it." else "Kept encrypted on this phone.",
    )

    if (destination.platform == Platform.TWITCH) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Test mode", style = MaterialTheme.typography.titleSmall)
          Caption("Twitch's bandwidth test: connects and measures, but never goes public.")
        }
        Switch(checked = destination.twitchBandwidthTest, onCheckedChange = { onChange(destination.copy(twitchBandwidthTest = it)) })
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(
        onClick = onCheck,
        enabled = schemeOf(destination.serverUrl) != UrlScheme.INVALID && check != CheckState.Running,
        modifier = Modifier.heightIn(min = 48.dp),
      ) {
        if (check == CheckState.Running) {
          CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
          Icon(Icons.Filled.NetworkCheck, contentDescription = null)
        }
        Spacer(Modifier.width(8.dp))
        Text("Check connection")
      }
      if (check is CheckState.Done) {
        val colors = LocalStatusColors.current
        Text(
          check.message,
          style = MaterialTheme.typography.bodySmall,
          color = if (check.ok) colors.live else colors.failed,
          modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
      }
    }
  }
}

@Composable
private fun ServerPicker(destination: Destination, onChange: (Destination) -> Unit) {
  val options = destination.platform.limits.ingestOptions
  if (options.isEmpty()) {
    PasteField(
      label = if (destination.platform == Platform.KICK) "Stream URL" else "Server address",
      value = destination.serverUrl,
      onValueChange = { onChange(destination.copy(serverUrl = it)) },
      placeholder = if (destination.platform == Platform.KICK) "rtmps://…live-video.net:443/app" else "rtmp://192.168.1.20:1935/live",
    )
    return
  }
  val matching = options.firstOrNull { it.url == destination.serverUrl }
  var showOther by remember { mutableStateOf(matching == null) }
  Column {
    Text("Server", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.size(8.dp))
    ChipPicker(
      options = options.map { it.url } + OTHER_SERVER,
      selected = if (showOther) OTHER_SERVER else destination.serverUrl,
      label = { url -> if (url == OTHER_SERVER) "Other server…" else options.first { it.url == url }.let { if (it.recommended) "${it.label} ★" else it.label } },
      onSelect = { url ->
        showOther = url == OTHER_SERVER
        if (!showOther) onChange(destination.copy(serverUrl = url))
      },
    )
    if (showOther) {
      Spacer(Modifier.size(8.dp))
      PasteField(
        label = "Server address",
        value = destination.serverUrl,
        onValueChange = { onChange(destination.copy(serverUrl = it)) },
        placeholder = "rtmp://192.168.1.20:1935/live",
        supportingText = "For test servers or a specific ingest. ★ marks the recommended choice.",
      )
    }
  }
}

@Composable
fun MissingSetupText(destination: Destination, modifier: Modifier = Modifier) {
  destination.missingSetup()?.let {
    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = modifier.fillMaxWidth())
  }
}

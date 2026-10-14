@file:OptIn(ExperimentalMaterial3Api::class)

package com.kelleyparker.multistream.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kelleyparker.multistream.core.DestinationStatus
import com.kelleyparker.multistream.core.Platform
import com.kelleyparker.multistream.ui.brandColor
import com.kelleyparker.multistream.ui.mark
import com.kelleyparker.multistream.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

/** A round brand-coloured badge with the platform's short mark. */
@Composable
fun PlatformBadge(platform: Platform, size: Dp = 40.dp) {
  Box(
    modifier = Modifier
      .size(size)
      .background(platform.brandColor, CircleShape)
      .semantics { contentDescription = platform.displayName },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      platform.mark,
      color = Color.White,
      fontWeight = FontWeight.Bold,
      fontSize = (size.value * if (platform.mark.length > 2) 0.24f else 0.36f).sp,
    )
  }
}

/** A big tappable platform card used in the setup wizard. */
@Composable
fun PlatformTile(platform: Platform, subtitle: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  val border = if (selected) platform.brandColor else MaterialTheme.colorScheme.outlineVariant
  Card(
    modifier = modifier
      .heightIn(min = 72.dp)
      .border(if (selected) 3.dp else 1.dp, border, RoundedCornerShape(16.dp))
      .clickable(role = Role.Checkbox, onClickLabel = if (selected) "Turn off" else "Turn on", onClick = onToggle),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (selected) platform.brandColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
    ),
  ) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      PlatformBadge(platform, 48.dp)
      Column(Modifier.padding(start = 16.dp).weight(1f)) {
        Text(platform.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = platform.brandColor, modifier = Modifier.size(32.dp))
    }
  }
}

/** Grey idle, amber connecting, green live, red failed. */
@Composable
fun StatusLight(status: DestinationStatus, modifier: Modifier = Modifier) {
  val colors = LocalStatusColors.current
  val (color, label) = when (status) {
    DestinationStatus.Idle -> colors.idle to "Ready"
    DestinationStatus.Connecting -> colors.connecting to "Connecting…"
    is DestinationStatus.Live -> colors.live to if (status.bitrateKbps > 0) "Live · ${status.bitrateKbps} kbps" else "Live"
    is DestinationStatus.Reconnecting -> colors.connecting to "Reconnecting (try ${status.attempt})"
    is DestinationStatus.Failed -> colors.failed to "Failed"
  }
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(12.dp).background(color, CircleShape))
    Spacer(Modifier.width(8.dp))
    Text(label, style = MaterialTheme.typography.labelLarge, color = color)
  }
}

/**
 * Segmented buttons. Options with a [disabledReason] are dimmed; tapping one explains why
 * instead of silently doing nothing.
 */
@Composable
fun <T> SegmentedPicker(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  disabledReason: (T) -> String? = { null },
  enabled: Boolean = true,
) {
  var explanation by remember { mutableStateOf<String?>(null) }
  Column(modifier) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      options.forEachIndexed { index, option ->
        val reason = disabledReason(option)
        SegmentedButton(
          selected = option == selected,
          onClick = {
            if (reason == null) {
              explanation = null
              onSelect(option)
            } else {
              explanation = reason
            }
          },
          enabled = enabled,
          shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
          modifier = Modifier.heightIn(min = 48.dp).alpha(if (reason == null) 1f else 0.45f),
          label = { Text(label(option)) },
        )
      }
    }
    ReasonText(explanation)
  }
}

/** A row of chips for small choices like frame rate or audio bitrate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipPicker(
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  disabledReason: (T) -> String? = { null },
  enabled: Boolean = true,
) {
  var explanation by remember { mutableStateOf<String?>(null) }
  Column(modifier) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      options.forEach { option ->
        val reason = disabledReason(option)
        FilterChip(
          selected = option == selected,
          onClick = {
            if (reason == null) {
              explanation = null
              onSelect(option)
            } else {
              explanation = reason
            }
          },
          enabled = enabled,
          label = { Text(label(option), modifier = Modifier.padding(vertical = 10.dp)) },
          modifier = Modifier.alpha(if (reason == null) 1f else 0.45f),
        )
      }
    }
    ReasonText(explanation)
  }
}

@Composable
private fun ReasonText(reason: String?) {
  AnimatedVisibility(reason != null) {
    Text(
      reason.orEmpty(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
      modifier = Modifier.padding(top = 6.dp),
    )
  }
}

/** The one place users type: a masked stream-key or server field with a Paste button. */
@Composable
fun PasteField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  secret: Boolean = false,
  placeholder: String = "",
  supportingText: String? = null,
  isError: Boolean = false,
) {
  var revealed by remember { mutableStateOf(false) }
  val clipboard = LocalClipboard.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
    singleLine = true,
    isError = isError,
    supportingText = supportingText?.let { { Text(it) } },
    visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri, autoCorrectEnabled = false),
    trailingIcon = {
      Row {
        if (secret) {
          IconButton(onClick = { revealed = !revealed }) {
            Icon(
              if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
              contentDescription = if (revealed) "Hide $label" else "Show $label",
            )
          }
        }
        IconButton(onClick = {
          scope.launch {
            val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!text.isNullOrBlank()) onValueChange(text.trim())
          }
        }) {
          Icon(Icons.Filled.ContentPaste, contentDescription = "Paste $label")
        }
      }
    },
    modifier = modifier.fillMaxWidth(),
  )
}

/** A "?" button that explains a setting in plain language. */
@Composable
fun HelpButton(title: String, text: String) {
  var open by remember { mutableStateOf(false) }
  IconButton(onClick = { open = true }) {
    Icon(Icons.Outlined.Info, contentDescription = "About $title")
  }
  if (open) {
    AlertDialog(
      onDismissRequest = { open = false },
      title = { Text(title) },
      text = { Text(text) },
      confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
    )
  }
}

/** A titled card section with an optional help button. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, help: String? = null, content: @Composable () -> Unit) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (help != null) HelpButton(title, help)
      }
      Spacer(Modifier.size(8.dp))
      content()
    }
  }
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier, textAlign = TextAlign.Start)
}

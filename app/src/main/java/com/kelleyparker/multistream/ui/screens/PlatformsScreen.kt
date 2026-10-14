@file:OptIn(ExperimentalMaterial3Api::class)

package com.kelleyparker.multistream.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.kelleyparker.multistream.ui.UiState
import com.kelleyparker.multistream.ui.components.Caption
import com.kelleyparker.multistream.ui.components.PlatformBadge
import com.kelleyparker.multistream.ui.components.PlatformTile
import com.kelleyparker.multistream.ui.tagline
import java.util.UUID

/** All destinations with on/off switches; tap one to edit, or add another platform. */
@Composable
fun PlatformsScreen(
  state: UiState,
  onBack: () -> Unit,
  onToggle: (String, Boolean) -> Unit,
  onEdit: (String) -> Unit,
  onAdd: (Destination) -> Unit,
) {
  var adding by remember { mutableStateOf(false) }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Platforms") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to camera") } },
      )
    },
    floatingActionButton = {
      if (!state.isLive) {
        ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add platform") })
      }
    },
  ) { padding ->
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (state.isLive) item { Caption("Stop streaming to change platforms.") }
      items(state.destinations, key = { it.id }) { destination ->
        val tier = state.plan.tierOf(destination.id)
        DestinationRow(
          destination = destination,
          subtitle = destination.missingSetup()
            ?: tier?.let { "${it.format.label} · ${it.videoKbps} kbps" + if (state.plan.isDualTier) " · ${it.id.label} encoder" else "" }
            ?: "Off",
          enabled = !state.isLive,
          onToggle = { onToggle(destination.id, it) },
          onClick = { onEdit(destination.id) },
        )
      }
    }
  }
  if (adding) {
    AlertDialog(
      onDismissRequest = { adding = false },
      title = { Text("Add a platform") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
          for (platform in Platform.entries) {
            PlatformTile(platform, platform.tagline, selected = false, onToggle = {
              adding = false
              onAdd(Destination(UUID.randomUUID().toString(), platform))
            })
          }
        }
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun DestinationRow(
  destination: Destination,
  subtitle: String,
  enabled: Boolean,
  onToggle: (Boolean) -> Unit,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClickLabel = "Edit", onClick = onClick),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Row(Modifier.padding(16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
      PlatformBadge(destination.platform, 48.dp)
      Column(Modifier.padding(horizontal = 16.dp).weight(1f)) {
        Text(destination.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Switch(checked = destination.enabled, onCheckedChange = onToggle, enabled = enabled)
    }
  }
}

/** Edit one destination with the same form the wizard uses. */
@Composable
fun EditPlatformScreen(
  destination: Destination,
  check: CheckState?,
  onCheck: (Destination) -> Unit,
  onSave: (Destination) -> Unit,
  onDelete: () -> Unit,
  onBack: () -> Unit,
) {
  var draft by remember(destination.id) { mutableStateOf(destination) }
  var confirmDelete by remember { mutableStateOf(false) }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(destination.platform.displayName) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back without saving") } },
        actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${destination.label}") } },
      )
    },
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(Modifier.widthIn(max = 560.dp)) {
        PlatformForm(draft, onChange = { draft = it }, check = check, onCheck = { onCheck(draft) })
        Spacer(Modifier.size(16.dp))
        MissingSetupText(draft)
        Spacer(Modifier.size(16.dp))
        Row {
          OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancel") }
          Spacer(Modifier.width(12.dp))
          Button(onClick = { onSave(draft) }, enabled = draft.missingSetup() == null, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Text("Save")
          }
        }
      }
    }
  }
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Remove ${destination.label}?") },
      text = { Text("Its stream key will be deleted from this phone.") },
      confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove") } },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
    )
  }
}


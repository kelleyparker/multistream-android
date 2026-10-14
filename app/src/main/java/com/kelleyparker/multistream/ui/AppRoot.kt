package com.kelleyparker.multistream.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kelleyparker.multistream.core.Destination
import com.kelleyparker.multistream.ui.screens.EditPlatformScreen
import com.kelleyparker.multistream.ui.screens.LiveActions
import com.kelleyparker.multistream.ui.screens.LiveScreen
import com.kelleyparker.multistream.ui.screens.PlatformsScreen
import com.kelleyparker.multistream.ui.screens.QualityScreen
import com.kelleyparker.multistream.ui.screens.SetupWizardScreen

private sealed interface Screen {
  data object Live : Screen
  data object Platforms : Screen
  data object Quality : Screen
  /** [draft] is set for a platform being added, so nothing is saved until the user taps Save. */
  data class Edit(val id: String, val draft: Destination? = null) : Screen
}

private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

@Composable
fun AppRoot(viewModel: MainViewModel) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var granted by remember { mutableStateOf(hasPermissions(context)) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
    granted = result.values.all { it }
  }
  var screen by remember { mutableStateOf<Screen>(Screen.Live) }

  LaunchedEffect(granted) { viewModel.onPermissionsResult(granted) }

  if (!state.loaded) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    return
  }
  if (!state.setupDone) {
    SetupWizardScreen(
      initial = state.destinations,
      checks = state.checks,
      onCheck = viewModel::checkConnection,
      onFinish = { destinations ->
        viewModel.finishSetup(destinations)
        if (!granted) launcher.launch(PERMISSIONS)
      },
    )
    return
  }

  // Streaming is 16:9, so the console locks to landscape; settings screens follow the phone.
  LockOrientation(landscape = screen == Screen.Live)
  BackHandler(enabled = screen != Screen.Live) { screen = Screen.Live }

  when (val current = screen) {
    Screen.Live -> {
      KeepScreenOn(state.isLive)
      LiveScreen(
        state = state,
        permissionsGranted = granted,
        actions = LiveActions(
          onGoLive = viewModel::goLive,
          onStopAll = viewModel::stopAll,
          onStopOne = viewModel::stopOne,
          onRetryOne = viewModel::retryOne,
          onToggleMute = viewModel::toggleMute,
          onSwitchCamera = viewModel::switchCamera,
          onOpenPlatforms = { screen = Screen.Platforms },
          onOpenQuality = { screen = Screen.Quality },
          onRequestPermissions = { launcher.launch(PERMISSIONS) },
        ),
        preview = { modifier ->
          AndroidView(
            factory = { SurfaceView(it).also(viewModel::attachPreview) },
            onRelease = { viewModel.detachPreview() },
            modifier = modifier,
          )
        },
      )
    }
    Screen.Platforms -> PlatformsScreen(
      state = state,
      onBack = { screen = Screen.Live },
      onToggle = viewModel::setEnabled,
      onEdit = { screen = Screen.Edit(it) },
      onAdd = { destination -> screen = Screen.Edit(destination.id, draft = destination) },
    )
    Screen.Quality -> QualityScreen(state, onBack = { screen = Screen.Live }, onChange = viewModel::setQuality)
    is Screen.Edit -> {
      val destination = state.destinations.firstOrNull { it.id == current.id } ?: current.draft
      if (destination == null) {
        LaunchedEffect(current) { screen = Screen.Platforms }
      } else {
        EditPlatformScreen(
          destination = destination,
          check = state.checks[destination.id],
          onCheck = viewModel::checkConnection,
          onSave = {
            viewModel.upsertDestination(it)
            screen = Screen.Platforms
          },
          onDelete = {
            viewModel.removeDestination(destination.id)
            screen = Screen.Platforms
          },
          onBack = { screen = Screen.Platforms },
        )
      }
    }
  }
}

private fun hasPermissions(context: Context) =
  PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

// Streams are 16:9 landscape, so the console must be landscape too; settings screens stay unlocked.
@SuppressLint("SourceLockedOrientationActivity")
@Composable
private fun LockOrientation(landscape: Boolean) {
  val activity = LocalContext.current.findActivity() ?: return
  DisposableEffect(landscape) {
    activity.requestedOrientation =
      if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    onDispose { }
  }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
  val view = LocalView.current
  DisposableEffect(on) {
    view.keepScreenOn = on
    onDispose { view.keepScreenOn = false }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

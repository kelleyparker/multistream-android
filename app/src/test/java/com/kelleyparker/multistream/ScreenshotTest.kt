package com.kelleyparker.multistream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import com.github.takahirom.roborazzi.captureRoboImage
import com.kelleyparker.multistream.core.CustomQuality
import com.kelleyparker.multistream.core.Destination
import com.kelleyparker.multistream.core.DestinationStatus
import com.kelleyparker.multistream.core.EncoderCaps
import com.kelleyparker.multistream.core.Platform
import com.kelleyparker.multistream.core.QualityPreset
import com.kelleyparker.multistream.core.QualitySettings
import com.kelleyparker.multistream.ui.CheckState
import com.kelleyparker.multistream.ui.UiState
import com.kelleyparker.multistream.ui.screens.LiveActions
import com.kelleyparker.multistream.ui.screens.LiveScreen
import com.kelleyparker.multistream.ui.screens.PlatformsScreen
import com.kelleyparker.multistream.ui.screens.QualityScreen
import com.kelleyparker.multistream.ui.screens.SetupWizardScreen
import com.kelleyparker.multistream.ui.theme.MultistreamTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every main screen in light and dark so the UI can be reviewed without a phone.
 * Run `./gradlew :app:recordRoborazziDebug`; images land in app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

  private val youtube = Destination("yt", Platform.YOUTUBE, streamKey = "abcd-efgh-ijkl")
  private val twitch = Destination("tw", Platform.TWITCH, streamKey = "live_123456")
  private val kick = Destination(
    "kk", Platform.KICK,
    serverUrl = "rtmps://fa723fc1b171.global-contribute.live-video.net:443/app",
    streamKey = "sk_us-west-2_abc",
  )

  private val ready = UiState(
    loaded = true,
    setupDone = true,
    destinations = listOf(youtube, twitch, kick),
    quality = QualitySettings(QualityPreset.MAX),
    caps = EncoderCaps.FLAGSHIP,
  )

  private fun shoot(name: String, content: @Composable () -> Unit) {
    for (dark in listOf(false, true)) {
      captureRoboImage("build/screenshots/${name}_${if (dark) "dark" else "light"}.png") {
        MultistreamTheme(darkTheme = dark) { content() }
      }
    }
  }

  @Test
  fun wizardPickPlatforms() = shoot("01_wizard_pick") {
    SetupWizardScreen(
      initial = listOf(youtube.copy(enabled = true), twitch.copy(enabled = true), kick.copy(enabled = false)),
      checks = emptyMap(),
      onCheck = {},
      onFinish = {},
    )
  }

  @Test
  fun wizardKickStep() = shoot("02_wizard_kick") {
    SetupWizardScreen(
      initial = listOf(kick.copy(enabled = true)),
      checks = mapOf("kk" to CheckState.Done(true, "Server reachable (84 ms). Nothing was broadcast.")),
      onCheck = {},
      onFinish = {},
      startStep = 1,
    )
  }

  @Test
  fun qualityPresets() = shoot("03_quality_presets") {
    QualityScreen(ready, onBack = {}, onChange = {})
  }

  @Test
  fun qualityCustomDial() = shoot("04_quality_custom") {
    QualityScreen(ready.copy(quality = QualitySettings(QualityPreset.CUSTOM, CustomQuality())), onBack = {}, onChange = {})
  }

  @Test
  fun platforms() = shoot("05_platforms") {
    PlatformsScreen(ready, onBack = {}, onToggle = { _, _ -> }, onEdit = {}, onAdd = {})
  }

  @Test
  @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
  fun liveReady() = shoot("06_live_ready") {
    LiveScreen(ready, permissionsGranted = true, actions = LiveActions(), preview = { FakeCamera(it) })
  }

  @Test
  @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
  fun liveStreaming() = shoot("07_live_streaming") {
    LiveScreen(
      ready.copy(
        isLive = true,
        statuses = mapOf(
          "yt" to DestinationStatus.Live(23_870),
          "tw" to DestinationStatus.Live(5_990),
          "kk" to DestinationStatus.Reconnecting(2, "The connection dropped."),
        ),
      ),
      permissionsGranted = true,
      actions = LiveActions(),
      preview = { FakeCamera(it) },
    )
  }
}

/** Stands in for the camera, which Robolectric can't open. */
@Composable
private fun FakeCamera(modifier: Modifier) {
  Box(
    modifier.background(Brush.linearGradient(listOf(Color(0xFF283C86), Color(0xFF45A247)))),
    contentAlignment = Alignment.Center,
  ) {
    Text("Camera preview", color = Color.White)
  }
}

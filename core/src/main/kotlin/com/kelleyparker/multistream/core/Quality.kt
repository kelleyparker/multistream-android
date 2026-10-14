package com.kelleyparker.multistream.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class QualityPreset(val title: String, val subtitle: String) {
  TEST("Test", "1080p60 to every destination. Use with a local test server."),
  STANDARD("Standard", "1080p30. Works on most phones and connections."),
  MAX("Max", "Each platform's best: YouTube 1440p60, Twitch and Kick 1080p60."),
  CUSTOM("Custom", "Pick resolution, frame rate and bitrate yourself."),
}

@Serializable
data class TierSettings(val format: VideoFormat, val videoKbps: Int)

/**
 * What the Custom preset uses. [main] goes to every destination; when [youtubeOwnTier]
 * is on and the phone can run two encoders, YouTube gets [youtube] instead.
 */
@Serializable
data class CustomQuality(
  val main: TierSettings = TierSettings(VideoFormat(Resolution.P1080, FrameRate.FPS60), 6_000),
  val youtubeOwnTier: Boolean = true,
  val youtube: TierSettings = TierSettings(VideoFormat(Resolution.P1440, FrameRate.FPS60), 34_000),
)

@Serializable
data class QualitySettings(
  val preset: QualityPreset = QualityPreset.STANDARD,
  val custom: CustomQuality = CustomQuality(),
  val audio: AudioBitrate = AudioBitrate.K128,
)

object PresetFormats {
  val TEST = TierSettings(VideoFormat(Resolution.P1080, FrameRate.FPS60), 6_000)
  // 5000, not Twitch's 4500, because YouTube's H.264 minimum for 1080p30 is 5 Mbps.
  val STANDARD = TierSettings(VideoFormat(Resolution.P1080, FrameRate.FPS30), 5_000)
}

object QualityJson {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  fun encode(settings: QualitySettings): String = json.encodeToString(QualitySettings.serializer(), settings)

  fun decode(text: String?): QualitySettings =
    if (text.isNullOrBlank()) QualitySettings()
    else runCatching { json.decodeFromString(QualitySettings.serializer(), text) }.getOrDefault(QualitySettings())
}

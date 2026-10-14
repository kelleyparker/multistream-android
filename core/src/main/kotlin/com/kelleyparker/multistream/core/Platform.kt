package com.kelleyparker.multistream.core

import kotlinx.serialization.Serializable

@Serializable
enum class Platform(val displayName: String) {
  YOUTUBE("YouTube"),
  TWITCH("Twitch"),
  KICK("Kick"),
  CUSTOM("Custom server");
}

/** A choice in the server dropdown, so YouTube and Twitch never need a typed URL. */
data class IngestOption(val label: String, val url: String, val recommended: Boolean = false)

/**
 * Ingest rules for one platform. Values come from each platform's published guidance
 * (see README "Platform limits") and live here so there is one place to update them.
 */
data class PlatformLimits(
  val platform: Platform,
  val maxResolution: Resolution,
  val maxFrameRate: FrameRate,
  val minVideoKbps: Int,
  val maxVideoKbps: Int,
  /** Per-format minimums where the platform publishes them (YouTube does); otherwise [minVideoKbps]. */
  val minKbpsByFormat: Map<VideoFormat, Int> = emptyMap(),
  /** Bitrate the platform recommends per format; going lower is allowed but warned about. */
  val recommendedKbps: Map<VideoFormat, Int>,
  val maxAudio: AudioBitrate,
  val requiresRtmps: Boolean,
  val prefersRtmps: Boolean,
  /** Kick's guide asks for H.264 Main profile; the others accept Main or High. */
  val mainProfileOnly: Boolean = false,
  /** What the Max preset sends to this platform. */
  val maxPreset: VideoFormat,
  val maxPresetKbps: Int,
  val ingestOptions: List<IngestOption>,
  /** Where the user finds their stream key, opened from the setup wizard. */
  val keyHelpUrl: String?,
  val keyHelpText: String,
) {
  fun allows(resolution: Resolution) = resolution.ordinal <= maxResolution.ordinal

  fun allows(frameRate: FrameRate) = frameRate.ordinal <= maxFrameRate.ordinal

  fun allows(format: VideoFormat) = allows(format.resolution) && allows(format.frameRate)

  fun recommendedFor(format: VideoFormat): Int? = recommendedKbps[format]

  fun minFor(format: VideoFormat): Int = minKbpsByFormat[format] ?: minVideoKbps

  companion object {
    private fun table(vararg entries: Pair<String, Int>): Map<VideoFormat, Int> =
      entries.associate { (key, kbps) ->
        val resolution = Resolution.entries.first { key.startsWith(it.label) }
        val frameRate = FrameRate.entries.first { key.endsWith(it.fps.toString()) }
        VideoFormat(resolution, frameRate) to kbps
      }

    // YouTube Help, "Choose live encoder settings": the H.264 columns (H.265/AV1 need less).
    val YOUTUBE = PlatformLimits(
      platform = Platform.YOUTUBE,
      maxResolution = Resolution.P1440,
      maxFrameRate = FrameRate.FPS60,
      minVideoKbps = 3_000,
      maxVideoKbps = 50_000,
      minKbpsByFormat = table(
        "720p30" to 3_000, "720p60" to 3_000,
        "1080p30" to 5_000, "1080p60" to 6_000,
        "1440p30" to 7_000, "1440p60" to 8_000,
      ),
      recommendedKbps = table(
        "720p30" to 8_000, "720p60" to 8_000,
        "1080p30" to 14_000, "1080p60" to 17_000,
        "1440p30" to 21_000, "1440p60" to 34_000,
      ),
      maxAudio = AudioBitrate.K160,
      requiresRtmps = false,
      prefersRtmps = true,
      maxPreset = VideoFormat(Resolution.P1440, FrameRate.FPS60),
      maxPresetKbps = 34_000,
      ingestOptions = listOf(
        IngestOption("Auto, encrypted (RTMPS)", "rtmps://a.rtmps.youtube.com:443/live2", recommended = true),
        IngestOption("Auto, unencrypted (RTMP)", "rtmp://a.rtmp.youtube.com/live2"),
      ),
      keyHelpUrl = "https://www.youtube.com/livestreaming",
      keyHelpText = "YouTube Studio → Create → Go live → Stream. Copy the \"Stream key\".",
    )

    // Twitch Help, "Broadcasting Guidelines": CBR, 2 s keyframes, Main/High profile.
    val TWITCH = PlatformLimits(
      platform = Platform.TWITCH,
      maxResolution = Resolution.P1080,
      maxFrameRate = FrameRate.FPS60,
      minVideoKbps = 1_000,
      maxVideoKbps = 6_000,
      recommendedKbps = table(
        "720p30" to 3_000, "720p60" to 4_500,
        "1080p30" to 4_500, "1080p60" to 6_000,
      ),
      maxAudio = AudioBitrate.K160,
      requiresRtmps = false,
      prefersRtmps = false,
      maxPreset = VideoFormat(Resolution.P1080, FrameRate.FPS60),
      maxPresetKbps = 6_000,
      ingestOptions = listOf(
        IngestOption("Auto, closest server", "rtmp://live.twitch.tv/app", recommended = true),
        IngestOption("Auto, encrypted (RTMPS)", "rtmps://live.twitch.tv:443/app"),
      ),
      keyHelpUrl = "https://dashboard.twitch.tv/settings/stream",
      keyHelpText = "Twitch Creator Dashboard → Settings → Stream. Copy the \"Primary Stream key\".",
    )

    // Kick Help, OBS settings: H.264 CBR 1000–8000 kbps, 2 s keyframes, Main profile, no B-frames.
    val KICK = PlatformLimits(
      platform = Platform.KICK,
      maxResolution = Resolution.P1080,
      maxFrameRate = FrameRate.FPS60,
      minVideoKbps = 1_000,
      maxVideoKbps = 8_000,
      recommendedKbps = table(
        "720p30" to 3_000, "720p60" to 4_500,
        "1080p30" to 4_500, "1080p60" to 6_000,
      ),
      maxAudio = AudioBitrate.K160,
      requiresRtmps = true,
      prefersRtmps = true,
      mainProfileOnly = true,
      maxPreset = VideoFormat(Resolution.P1080, FrameRate.FPS60),
      maxPresetKbps = 8_000,
      // Kick gives every account its own server URL, so there is nothing to pick from.
      ingestOptions = emptyList(),
      keyHelpUrl = "https://kick.com/dashboard",
      keyHelpText = "Kick Dashboard → Settings → Stream URL & Key. Copy both the \"Stream URL\" and the \"Stream Key\".",
    )

    val CUSTOM = PlatformLimits(
      platform = Platform.CUSTOM,
      maxResolution = Resolution.P1440,
      maxFrameRate = FrameRate.FPS60,
      minVideoKbps = 500,
      maxVideoKbps = 50_000,
      recommendedKbps = emptyMap(),
      maxAudio = AudioBitrate.K160,
      requiresRtmps = false,
      prefersRtmps = false,
      maxPreset = VideoFormat(Resolution.P1080, FrameRate.FPS60),
      maxPresetKbps = 8_000,
      ingestOptions = emptyList(),
      keyHelpUrl = null,
      keyHelpText = "Paste the RTMP or RTMPS server address and stream key from your server.",
    )

    fun of(platform: Platform): PlatformLimits = when (platform) {
      Platform.YOUTUBE -> YOUTUBE
      Platform.TWITCH -> TWITCH
      Platform.KICK -> KICK
      Platform.CUSTOM -> CUSTOM
    }
  }
}

val Platform.limits: PlatformLimits get() = PlatformLimits.of(this)

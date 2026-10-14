package com.kelleyparker.multistream.core

import kotlinx.serialization.Serializable

/** Every output is 16:9 so the two encoder tiers can share one camera pipeline. */
@Serializable
enum class Resolution(val width: Int, val height: Int, val label: String) {
  P720(1280, 720, "720p"),
  P1080(1920, 1080, "1080p"),
  P1440(2560, 1440, "1440p");
}

@Serializable
enum class FrameRate(val fps: Int) {
  FPS30(30),
  FPS60(60);

  val label: String get() = "$fps fps"
}

@Serializable
data class VideoFormat(val resolution: Resolution, val frameRate: FrameRate) {
  val label: String get() = "${resolution.label}${frameRate.fps}"
}

/** Audio is shared by every destination: AAC-LC, stereo, 48 kHz. Only the bitrate is a choice. */
@Serializable
enum class AudioBitrate(val kbps: Int) {
  K96(96),
  K128(128),
  K160(160);

  val label: String get() = "$kbps kbps"
}

const val AUDIO_SAMPLE_RATE = 48_000
const val KEYFRAME_INTERVAL_SECONDS = 2

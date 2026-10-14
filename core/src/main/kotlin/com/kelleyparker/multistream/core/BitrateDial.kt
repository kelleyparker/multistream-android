package com.kelleyparker.multistream.core

enum class DialZone { LOW, GOOD }

/** A platform's limit drawn on the dial, e.g. Twitch's logo at 6000. */
data class DialMarker(val platform: Platform, val kbps: Int)

/**
 * The math behind the bitrate dial: a range limited to what every member platform accepts,
 * 500 kbps snap points, colour zones and per-platform limit markers.
 */
data class BitrateDial(
  val minKbps: Int,
  val maxKbps: Int,
  /** Below this the dial turns amber. */
  val recommendedKbps: Int,
  val markers: List<DialMarker>,
  val step: Int = STEP,
) {
  fun snap(kbps: Int): Int {
    val rounded = ((kbps + step / 2).floorDiv(step)) * step
    return rounded.coerceIn(minKbps, maxKbps)
  }

  fun increment(kbps: Int) = snap(kbps + step)

  fun decrement(kbps: Int) = snap(kbps - step)

  fun zone(kbps: Int): DialZone = if (kbps >= recommendedKbps) DialZone.GOOD else DialZone.LOW

  /** Position along the dial, 0..1. */
  fun fraction(kbps: Int): Float =
    if (maxKbps == minKbps) 1f else (kbps.coerceIn(minKbps, maxKbps) - minKbps).toFloat() / (maxKbps - minKbps)

  fun fromFraction(fraction: Float): Int = snap(minKbps + Math.round(fraction.coerceIn(0f, 1f) * (maxKbps - minKbps)))

  companion object {
    const val STEP = 500

    /** Builds the dial for one tier's member platforms at a given format. */
    fun forMembers(members: List<PlatformLimits>, format: VideoFormat): BitrateDial {
      val effective = members.ifEmpty { listOf(PlatformLimits.CUSTOM) }
      val min = effective.maxOf { it.minFor(format) }
      val max = effective.minOf { it.maxVideoKbps }
      // Recommended = the highest recommendation that still fits, so green means "what platforms ask for".
      val recommended = effective.mapNotNull { it.recommendedFor(format) }.maxOrNull()?.coerceAtMost(max)
        ?: defaultRecommendation(format).coerceIn(min, max)
      val markers = members.filter { it.platform != Platform.CUSTOM }.map { DialMarker(it.platform, it.maxVideoKbps) }
      return BitrateDial(min, max, recommended, markers)
    }

    private fun defaultRecommendation(format: VideoFormat): Int = when (format.resolution) {
      Resolution.P720 -> if (format.frameRate == FrameRate.FPS60) 4_500 else 3_000
      Resolution.P1080 -> if (format.frameRate == FrameRate.FPS60) 6_000 else 4_500
      Resolution.P1440 -> if (format.frameRate == FrameRate.FPS60) 12_000 else 9_000
    }
  }
}

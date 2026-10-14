package com.kelleyparker.multistream.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BitrateDialTest {
  private val p1080p60 = VideoFormat(Resolution.P1080, FrameRate.FPS60)

  @Test
  fun `snaps to 500 kbps steps`() {
    val dial = BitrateDial.forMembers(listOf(PlatformLimits.KICK), p1080p60)
    assertEquals(4_500, dial.snap(4_620))
    assertEquals(5_000, dial.snap(4_750))
    assertEquals(5_000, dial.increment(4_500))
    assertEquals(4_000, dial.decrement(4_500))
  }

  @Test
  fun `clamps at the strictest platform max`() {
    val dial = BitrateDial.forMembers(listOf(PlatformLimits.TWITCH, PlatformLimits.KICK), p1080p60)
    assertEquals(6_000, dial.maxKbps)
    assertEquals(6_000, dial.snap(9_000))
    assertEquals(6_000, dial.increment(6_000))
    assertEquals(1_000, dial.snap(0))
  }

  @Test
  fun `zones follow the recommendation`() {
    val dial = BitrateDial.forMembers(listOf(PlatformLimits.YOUTUBE), VideoFormat(Resolution.P1440, FrameRate.FPS60))
    assertEquals(24_000, dial.recommendedKbps)
    assertEquals(DialZone.LOW, dial.zone(12_000))
    assertEquals(DialZone.GOOD, dial.zone(24_000))
  }

  @Test
  fun `markers show each platform limit`() {
    val dial = BitrateDial.forMembers(listOf(PlatformLimits.TWITCH, PlatformLimits.KICK, PlatformLimits.CUSTOM), p1080p60)
    assertEquals(
      listOf(DialMarker(Platform.TWITCH, 6_000), DialMarker(Platform.KICK, 8_000)),
      dial.markers,
    )
  }

  @Test
  fun `fraction round trips`() {
    val dial = BitrateDial.forMembers(listOf(PlatformLimits.KICK), p1080p60)
    assertEquals(0f, dial.fraction(1_000))
    assertEquals(1f, dial.fraction(8_000))
    assertEquals(4_500, dial.fromFraction(dial.fraction(4_500)))
  }
}

package com.kelleyparker.multistream.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TierPlannerTest {
  private val p1440p60 = VideoFormat(Resolution.P1440, FrameRate.FPS60)
  private val p1080p60 = VideoFormat(Resolution.P1080, FrameRate.FPS60)

  @Test
  fun `max on a capable phone splits youtube into its own 1440p tier`() {
    val plan = TierPlanner.plan(listOf(youtube, twitch, kick), quality(QualityPreset.MAX), EncoderCaps.FLAGSHIP)

    assertEquals(2, plan.tiers.size)
    val high = plan.tiers.single { it.id == TierId.HIGH }
    val main = plan.tiers.single { it.id == TierId.MAIN }
    assertEquals(listOf(youtube), high.destinations)
    assertEquals(p1440p60, high.format)
    assertEquals(24_000, high.videoKbps)
    assertEquals(listOf(twitch, kick), main.destinations)
    assertEquals(p1080p60, main.format)
    assertEquals(6_000, main.videoKbps)
    assertTrue(plan.notes.any { "Kick is capped at 6000" in it.text && "Twitch" in it.text })
  }

  @Test
  fun `max on a basic phone falls back to one 1080p60 tier`() {
    val plan = TierPlanner.plan(listOf(youtube, twitch, kick), quality(QualityPreset.MAX), EncoderCaps.BASIC)

    assertEquals(1, plan.tiers.size)
    assertEquals(p1080p60, plan.tiers[0].format)
    assertEquals(6_000, plan.tiers[0].videoKbps)
    assertEquals(3, plan.tiers[0].destinations.size)
    assertTrue(plan.notes.any { "two encoders" in it.text })
  }

  @Test
  fun `kick alone on max gets its full 8000`() {
    val plan = TierPlanner.plan(listOf(kick), quality(QualityPreset.MAX), EncoderCaps.FLAGSHIP)

    assertEquals(1, plan.tiers.size)
    assertEquals(p1080p60, plan.tiers[0].format)
    assertEquals(8_000, plan.tiers[0].videoKbps)
    assertTrue(plan.notes.isEmpty())
  }

  @Test
  fun `youtube alone on max uses 1440p60 with one encoder`() {
    val plan = TierPlanner.plan(listOf(youtube), quality(QualityPreset.MAX), EncoderCaps.FLAGSHIP)
    assertEquals(1, plan.tiers.size)
    assertEquals(p1440p60, plan.tiers[0].format)
    assertEquals(24_000, plan.tiers[0].videoKbps)
  }

  @Test
  fun `test preset sends 1080p60 at 6000 to everyone in one tier`() {
    val plan = TierPlanner.plan(listOf(youtube, twitch, kick), quality(QualityPreset.TEST), EncoderCaps.FLAGSHIP)

    assertEquals(1, plan.tiers.size)
    assertEquals(p1080p60, plan.tiers[0].format)
    assertEquals(6_000, plan.tiers[0].videoKbps)
    assertEquals(3, plan.tiers[0].destinations.size)
  }

  @Test
  fun `disabled destinations are left out`() {
    val plan = TierPlanner.plan(listOf(youtube.copy(enabled = false), twitch), quality(QualityPreset.MAX), EncoderCaps.FLAGSHIP)
    assertEquals(listOf(twitch), plan.tiers.single().destinations)
  }

  @Test
  fun `upload estimate counts every destination`() {
    val plan = TierPlanner.plan(listOf(youtube, twitch, kick), quality(QualityPreset.MAX), EncoderCaps.FLAGSHIP)
    // YouTube 24000 + 128, Twitch and Kick 6000 + 128 each.
    assertEquals(24_128 + 2 * 6_128, plan.uploadKbps)
    assertEquals("36.4 Mbps", formatMbps(plan.uploadKbps))
  }

  @Test
  fun `custom keeps youtube tier only when a second encoder exists`() {
    val custom = CustomQuality(
      main = TierSettings(VideoFormat(Resolution.P1080, FrameRate.FPS30), 4_500),
      youtubeOwnTier = true,
      youtube = TierSettings(p1440p60, 20_000),
    )
    val dual = TierPlanner.plan(listOf(youtube, twitch), quality(QualityPreset.CUSTOM, custom), EncoderCaps.FLAGSHIP)
    assertEquals(2, dual.tiers.size)
    assertEquals(20_000, dual.tierOf("yt")!!.videoKbps)

    val single = TierPlanner.plan(listOf(youtube, twitch), quality(QualityPreset.CUSTOM, custom), EncoderCaps.BASIC)
    assertEquals(1, single.tiers.size)
    assertEquals(4_500, single.tiers[0].videoKbps)
  }
}

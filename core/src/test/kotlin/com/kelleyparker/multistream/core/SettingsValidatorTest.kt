package com.kelleyparker.multistream.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidatorTest {
  private val p1080p60 = VideoFormat(Resolution.P1080, FrameRate.FPS60)

  private fun planOf(vararg tiers: Tier) = TierPlan(tiers.toList(), AudioBitrate.K128)

  @Test
  fun `kick above 8000 is an error`() {
    val result = SettingsValidator.validate(planOf(Tier(TierId.HIGH, p1080p60, 8_500, listOf(kick))), EncoderCaps.FLAGSHIP)
    assertFalse(result.canGoLive)
    assertTrue(result.errors.any { "Kick allows up to 8000 kbps" in it.message })
  }

  @Test
  fun `kick over plain rtmp is an error`() {
    val insecure = kick.copy(serverUrl = "rtmp://fa723fc1b171.global-contribute.live-video.net/app")
    val result = SettingsValidator.validate(planOf(Tier(TierId.HIGH, p1080p60, 6_000, listOf(insecure))), EncoderCaps.FLAGSHIP)
    assertTrue(result.errors.any { "rtmps://" in it.message && it.destinationId == "kk" })
  }

  @Test
  fun `youtube at 1440p below its recommendation is only a warning`() {
    val tier = Tier(TierId.HIGH, VideoFormat(Resolution.P1440, FrameRate.FPS60), 10_000, listOf(youtube))
    val result = SettingsValidator.validate(planOf(tier), EncoderCaps.FLAGSHIP)
    assertTrue(result.canGoLive)
    assertTrue(result.warnings.any { "recommends 24000 kbps" in it.message })
  }

  @Test
  fun `youtube over plain rtmp is a warning`() {
    val insecure = youtube.copy(serverUrl = "rtmp://a.rtmp.youtube.com/live2")
    val result = SettingsValidator.validate(planOf(Tier(TierId.HIGH, p1080p60, 12_000, listOf(insecure))), EncoderCaps.FLAGSHIP)
    assertTrue(result.canGoLive)
    assertTrue(result.warnings.any { "RTMPS" in it.message })
  }

  @Test
  fun `twitch cannot take 1440p`() {
    val tier = Tier(TierId.HIGH, VideoFormat(Resolution.P1440, FrameRate.FPS60), 6_000, listOf(twitch))
    val result = SettingsValidator.validate(planOf(tier), EncoderCaps.FLAGSHIP)
    assertTrue(result.errors.any { it.message == "Twitch supports up to 1080p." })
  }

  @Test
  fun `missing stream key blocks going live`() {
    val result = SettingsValidator.validate(planOf(Tier(TierId.HIGH, p1080p60, 6_000, listOf(twitch.copy(streamKey = "")))), EncoderCaps.FLAGSHIP)
    assertTrue(result.errors.any { "needs a stream key" in it.message })
  }

  @Test
  fun `1440p60 on a phone that cannot encode it is an error`() {
    val tier = Tier(TierId.HIGH, VideoFormat(Resolution.P1440, FrameRate.FPS60), 24_000, listOf(youtube))
    val result = SettingsValidator.validate(planOf(tier), EncoderCaps.BASIC)
    assertFalse(result.canGoLive)
  }

  @Test
  fun `max preset on a flagship validates cleanly apart from the upload warning`() {
    val plan = TierPlanner.plan(listOf(youtube, twitch, kick), quality(QualityPreset.MAX), EncoderCaps.FLAGSHIP)
    val result = SettingsValidator.validate(plan, EncoderCaps.FLAGSHIP)
    assertTrue(result.errors.toString(), result.canGoLive)
    assertEquals(1, result.warnings.size)
    assertTrue("upload" in result.warnings.single().message)
  }

  @Test
  fun `disabled option reasons name the limiting platform`() {
    assertEquals("Kick supports up to 1080p.", OptionRules.resolutionReason(Resolution.P1440, listOf(PlatformLimits.KICK)))
    assertNull(OptionRules.resolutionReason(Resolution.P1440, listOf(PlatformLimits.YOUTUBE)))
    assertEquals(
      "This phone can't encode 1440p60.",
      OptionRules.resolutionReason(Resolution.P1440, listOf(PlatformLimits.YOUTUBE), EncoderCaps.BASIC, FrameRate.FPS60),
    )
  }

  @Test
  fun `no destinations is an error`() {
    assertFalse(SettingsValidator.validate(TierPlan.EMPTY, EncoderCaps.FLAGSHIP).canGoLive)
  }

  @Test
  fun `two tiers must share a frame rate`() {
    val plan = TierPlan(
      listOf(
        Tier(TierId.HIGH, VideoFormat(Resolution.P1440, FrameRate.FPS60), 24_000, listOf(youtube)),
        Tier(TierId.MAIN, VideoFormat(Resolution.P1080, FrameRate.FPS30), 4_500, listOf(twitch)),
      ),
      AudioBitrate.K128,
    )
    assertTrue(SettingsValidator.validate(plan, EncoderCaps.FLAGSHIP).errors.any { "same frame rate" in it.message })
  }

  @Test
  fun `quality settings survive a save and load`() {
    val settings = QualitySettings(QualityPreset.CUSTOM, CustomQuality(youtubeOwnTier = false), AudioBitrate.K160)
    assertEquals(settings, QualityJson.decode(QualityJson.encode(settings)))
    assertEquals(QualitySettings(), QualityJson.decode("{broken"))
  }
}

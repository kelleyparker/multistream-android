package com.kelleyparker.multistream.core

/** What the phone's hardware H.264 encoder can do, measured on the device. */
data class EncoderCaps(
  /** One encoder at 1440p60. */
  val supports1440p60: Boolean,
  /** Two encoders at once: 1440p60 plus 1080p60. */
  val supportsDualTier: Boolean,
) {
  companion object {
    val FLAGSHIP = EncoderCaps(supports1440p60 = true, supportsDualTier = true)
    val BASIC = EncoderCaps(supports1440p60 = false, supportsDualTier = false)
  }
}

enum class TierId(val label: String) {
  /** The phone's main encoder. Carries YouTube at 1440p when two tiers run. */
  HIGH("High"),
  /** The second encoder, used for Twitch, Kick and custom servers when two tiers run. */
  MAIN("Main"),
}

data class Tier(
  val id: TierId,
  val format: VideoFormat,
  val videoKbps: Int,
  val destinations: List<Destination>,
) {
  val limits: List<PlatformLimits> get() = destinations.map { it.platform.limits }.distinct()

  /** The highest bitrate every member accepts. */
  val maxAllowedKbps: Int get() = limits.minOfOrNull { it.maxVideoKbps } ?: PlatformLimits.CUSTOM.maxVideoKbps

  val minAllowedKbps: Int get() = limits.maxOfOrNull { it.minVideoKbps } ?: PlatformLimits.CUSTOM.minVideoKbps

  /** The platform that sets [maxAllowedKbps], when it is not the only member. */
  val limitingPlatform: Platform? get() = limits.minByOrNull { it.maxVideoKbps }?.platform
}

enum class NoteKind { INFO, WARNING }

data class PlanNote(val kind: NoteKind, val text: String)

data class TierPlan(
  val tiers: List<Tier>,
  val audio: AudioBitrate,
  val notes: List<PlanNote> = emptyList(),
) {
  val isDualTier: Boolean get() = tiers.size == 2

  fun tierOf(destinationId: String): Tier? = tiers.firstOrNull { tier -> tier.destinations.any { it.id == destinationId } }

  /** Total upload in kbps: every destination receives its tier's video plus the shared audio. */
  val uploadKbps: Int get() = tiers.sumOf { (it.videoKbps + audio.kbps) * it.destinations.size }

  val encodeTier: Tier? get() = tiers.firstOrNull { it.id == TierId.HIGH } ?: tiers.firstOrNull()

  companion object {
    val EMPTY = TierPlan(emptyList(), AudioBitrate.K128)
  }
}

/**
 * Splits the enabled destinations into one or two encoder tiers.
 *
 * One encoder means one stream, so every destination in a tier gets identical video. YouTube is
 * the only platform that takes 1440p, so with the Max (or a 1440p Custom) preset it gets its own
 * tier when the phone can run two encoders, and everyone else shares a 1080p tier whose bitrate
 * is the lowest maximum among its members.
 */
object TierPlanner {

  fun plan(destinations: List<Destination>, quality: QualitySettings, caps: EncoderCaps): TierPlan {
    val enabled = destinations.filter { it.enabled }
    if (enabled.isEmpty()) return TierPlan(emptyList(), quality.audio)
    return when (quality.preset) {
      QualityPreset.TEST -> single(enabled, PresetFormats.TEST, quality.audio)
      QualityPreset.STANDARD -> single(enabled, PresetFormats.STANDARD, quality.audio)
      QualityPreset.MAX -> planMax(enabled, quality.audio, caps)
      QualityPreset.CUSTOM -> planCustom(enabled, quality, caps)
    }
  }

  private fun single(destinations: List<Destination>, settings: TierSettings, audio: AudioBitrate, notes: List<PlanNote> = emptyList()) =
    TierPlan(listOf(Tier(TierId.HIGH, settings.format, settings.videoKbps, destinations)), audio, notes)

  private fun planMax(enabled: List<Destination>, audio: AudioBitrate, caps: EncoderCaps): TierPlan {
    val (youtube, others) = enabled.partition { it.platform == Platform.YOUTUBE }
    val notes = mutableListOf<PlanNote>()

    if (youtube.isNotEmpty() && others.isNotEmpty()) {
      if (caps.supportsDualTier) {
        val main = sharedMax(others, notes)
        return TierPlan(
          listOf(
            Tier(TierId.HIGH, PlatformLimits.YOUTUBE.maxPreset, PlatformLimits.YOUTUBE.maxPresetKbps, youtube),
            Tier(TierId.MAIN, main.format, main.videoKbps, others),
          ),
          audio,
          notes,
        )
      }
      notes += PlanNote(
        NoteKind.INFO,
        "This phone can't run two encoders at once, so YouTube gets 1080p60 like the others.",
      )
      return single(enabled, sharedMax(enabled, notes), audio, notes)
    }

    if (youtube.isNotEmpty()) {
      if (caps.supports1440p60) {
        return single(enabled, TierSettings(PlatformLimits.YOUTUBE.maxPreset, PlatformLimits.YOUTUBE.maxPresetKbps), audio)
      }
      notes += PlanNote(NoteKind.INFO, "This phone can't encode 1440p60, so YouTube gets 1080p60.")
      return single(enabled, TierSettings(VideoFormat(Resolution.P1080, FrameRate.FPS60), 12_000), audio, notes)
    }

    return single(enabled, sharedMax(enabled, notes), audio, notes)
  }

  /** 1080p60 at the lowest Max-preset bitrate among the platforms sharing one encoder. */
  private fun sharedMax(destinations: List<Destination>, notes: MutableList<PlanNote>): TierSettings {
    val targets = destinations.map { it.platform }.distinct().associateWith { platform ->
      // YouTube's own 1080p60 recommendation; its 24 000 kbps Max target is for 1440p only.
      if (platform == Platform.YOUTUBE) 12_000 else platform.limits.maxPresetKbps
    }
    val kbps = targets.values.min()
    val setters = targets.filterValues { it == kbps }.keys.joinToString(" and ") { it.displayName }
    targets.filterValues { it > kbps }.keys.forEach { capped ->
      notes += PlanNote(NoteKind.INFO, "${capped.displayName} is capped at $kbps kbps because it shares an encoder with $setters.")
    }
    return TierSettings(VideoFormat(Resolution.P1080, FrameRate.FPS60), kbps)
  }

  private fun planCustom(enabled: List<Destination>, quality: QualitySettings, caps: EncoderCaps): TierPlan {
    val custom = quality.custom
    val (youtube, others) = enabled.partition { it.platform == Platform.YOUTUBE }
    val wantsYoutubeTier = custom.youtubeOwnTier && youtube.isNotEmpty()

    if (wantsYoutubeTier && others.isNotEmpty()) {
      if (caps.supportsDualTier) {
        return TierPlan(
          listOf(
            Tier(TierId.HIGH, custom.youtube.format, custom.youtube.videoKbps, youtube),
            Tier(TierId.MAIN, custom.main.format, custom.main.videoKbps, others),
          ),
          quality.audio,
        )
      }
      return single(
        enabled, custom.main, quality.audio,
        listOf(PlanNote(NoteKind.INFO, "This phone can't run two encoders at once, so YouTube uses the main settings.")),
      )
    }
    if (wantsYoutubeTier) return single(enabled, custom.youtube, quality.audio)
    return single(enabled, custom.main, quality.audio)
  }
}

package com.kelleyparker.multistream.core

enum class Severity { ERROR, WARNING }

/** [destinationId] is null for problems that are not about one destination. */
data class Issue(val severity: Severity, val message: String, val destinationId: String? = null)

data class Validation(val issues: List<Issue>) {
  val errors: List<Issue> get() = issues.filter { it.severity == Severity.ERROR }
  val warnings: List<Issue> get() = issues.filter { it.severity == Severity.WARNING }
  val canGoLive: Boolean get() = errors.isEmpty()

  fun forDestination(id: String) = issues.filter { it.destinationId == id }
}

/** Checks a [TierPlan] against every member platform's rules before going live. */
object SettingsValidator {

  /** Above this total upload the Live screen asks for confirmation. */
  const val DEFAULT_UPLOAD_WARNING_KBPS = 20_000

  fun validate(
    plan: TierPlan,
    caps: EncoderCaps,
    uploadWarningKbps: Int = DEFAULT_UPLOAD_WARNING_KBPS,
  ): Validation {
    val issues = mutableListOf<Issue>()
    if (plan.tiers.isEmpty()) {
      issues += Issue(Severity.ERROR, "Turn on at least one destination.")
      return Validation(issues)
    }
    for (tier in plan.tiers) {
      for (destination in tier.destinations) {
        issues += checkDestination(destination, tier, plan.audio)
      }
      if (tier.format.resolution == Resolution.P1440 && tier.format.frameRate == FrameRate.FPS60 && !caps.supports1440p60) {
        issues += Issue(Severity.ERROR, "This phone can't encode 1440p60. Pick 1080p or 30 fps.")
      }
    }
    if (plan.isDualTier && plan.tiers.map { it.format.frameRate }.distinct().size > 1) {
      issues += Issue(
        Severity.ERROR,
        "Both encoders share one camera, so YouTube and the other platforms must use the same frame rate.",
      )
    }
    if (plan.uploadKbps > uploadWarningKbps) {
      issues += Issue(
        Severity.WARNING,
        "This needs about ${formatMbps(plan.uploadKbps)} of upload. Make sure your connection can keep up.",
      )
    }
    return Validation(issues)
  }

  private fun checkDestination(destination: Destination, tier: Tier, audio: AudioBitrate): List<Issue> {
    val issues = mutableListOf<Issue>()
    val limits = destination.platform.limits
    val name = destination.label
    val id = destination.id
    fun error(message: String) { issues += Issue(Severity.ERROR, message, id) }
    fun warning(message: String) { issues += Issue(Severity.WARNING, message, id) }

    if (destination.streamKey.isBlank() && destination.platform != Platform.CUSTOM) {
      error("$name needs a stream key.")
    }
    when (schemeOf(destination.serverUrl)) {
      UrlScheme.INVALID -> error("$name's server address must start with rtmp:// or rtmps://.")
      UrlScheme.RTMP -> when {
        limits.requiresRtmps -> error("${limits.platform.displayName} only accepts secure rtmps:// addresses.")
        limits.prefersRtmps -> warning("$name: the encrypted RTMPS server is recommended.")
      }
      UrlScheme.RTMPS -> Unit
    }
    OptionRules.resolutionReason(tier.format.resolution, listOf(limits))?.let(::error)
    OptionRules.frameRateReason(tier.format.frameRate, listOf(limits))?.let(::error)
    if (tier.videoKbps > limits.maxVideoKbps) {
      error("${limits.platform.displayName} allows up to ${limits.maxVideoKbps} kbps.")
    } else if (tier.videoKbps < limits.minFor(tier.format)) {
      error("${limits.platform.displayName} needs at least ${limits.minFor(tier.format)} kbps for ${tier.format.label}.")
    } else {
      val recommended = limits.recommendedFor(tier.format)
      if (recommended != null && tier.videoKbps < recommended) {
        warning(
          "${limits.platform.displayName} recommends $recommended kbps for ${tier.format.label}. " +
            "Lower works but may look softer.",
        )
      }
    }
    if (audio.kbps > limits.maxAudio.kbps) {
      error("${limits.platform.displayName} allows audio up to ${limits.maxAudio.kbps} kbps.")
    }
    return issues
  }
}

/** Explains why a choice is greyed out, e.g. "Kick supports up to 1080p". Null means allowed. */
object OptionRules {
  fun resolutionReason(resolution: Resolution, members: List<PlatformLimits>): String? =
    members.firstOrNull { !it.allows(resolution) }?.let { "${it.platform.displayName} supports up to ${it.maxResolution.label}." }

  fun frameRateReason(frameRate: FrameRate, members: List<PlatformLimits>): String? =
    members.firstOrNull { !it.allows(frameRate) }?.let { "${it.platform.displayName} supports up to ${it.maxFrameRate.fps} fps." }

  fun resolutionReason(resolution: Resolution, members: List<PlatformLimits>, caps: EncoderCaps, frameRate: FrameRate): String? =
    resolutionReason(resolution, members)
      ?: if (resolution == Resolution.P1440 && frameRate == FrameRate.FPS60 && !caps.supports1440p60) {
        "This phone can't encode 1440p60."
      } else {
        null
      }

  fun audioReason(audio: AudioBitrate, members: List<PlatformLimits>): String? =
    members.firstOrNull { audio.kbps > it.maxAudio.kbps }?.let { "${it.platform.displayName} allows audio up to ${it.maxAudio.kbps} kbps." }
}

fun formatMbps(kbps: Int): String {
  val tenths = (kbps + 50) / 100
  return "${tenths / 10}.${tenths % 10} Mbps"
}

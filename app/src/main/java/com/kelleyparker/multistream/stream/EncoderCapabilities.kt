package com.kelleyparker.multistream.stream

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.kelleyparker.multistream.core.EncoderCaps

/** Asks the phone's H.264 hardware encoder whether it can do 1440p60, and two streams at once. */
object EncoderCapabilities {

  fun probe(): EncoderCaps = runCatching { probeUnsafe() }.getOrDefault(EncoderCaps.BASIC)

  private fun probeUnsafe(): EncoderCaps {
    val encoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
      .filter { it.isEncoder && it.supportedTypes.any { type -> type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } }
      .firstOrNull { isHardware(it) }
      ?: return EncoderCaps.BASIC
    val capabilities = encoder.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val video = capabilities.videoCapabilities ?: return EncoderCaps.BASIC
    val supports1440p60 = video.areSizeAndRateSupported(2560, 1440, 60.0)
    val supports1080p60 = video.areSizeAndRateSupported(1920, 1080, 60.0)
    var dual = supports1440p60 && supports1080p60 && capabilities.maxSupportedInstances >= 2
    if (dual && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // 1440p60 + 1080p60 is about 1.35 M macroblocks/s, which fits inside a 4K45 performance point.
      val points = video.supportedPerformancePoints
      if (!points.isNullOrEmpty()) {
        val combined = MediaCodecInfo.VideoCapabilities.PerformancePoint(3840, 2160, 45)
        dual = points.any { it.covers(combined) }
      }
    }
    return EncoderCaps(supports1440p60 = supports1440p60, supportsDualTier = dual)
  }

  private fun isHardware(info: MediaCodecInfo): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      info.isHardwareAccelerated
    } else {
      val name = info.name.lowercase()
      !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
    }
}

package com.kelleyparker.multistream.ui

import androidx.compose.ui.graphics.Color
import com.kelleyparker.multistream.core.Platform

/** Brand colours and a short mark for each platform. Letters instead of logos keep trademarks out of the app. */
val Platform.brandColor: Color
  get() = when (this) {
    Platform.YOUTUBE -> Color(0xFFFF0033)
    Platform.TWITCH -> Color(0xFF9146FF)
    Platform.KICK -> Color(0xFF2FBF0A)
    Platform.CUSTOM -> Color(0xFF5F6B7A)
  }

val Platform.mark: String
  get() = when (this) {
    Platform.YOUTUBE -> "YT"
    Platform.TWITCH -> "Tw"
    Platform.KICK -> "K"
    Platform.CUSTOM -> "RTMP"
  }

val Platform.tagline: String
  get() = when (this) {
    Platform.YOUTUBE -> "Up to 1440p60"
    Platform.TWITCH -> "Up to 1080p60 · 6000 kbps"
    Platform.KICK -> "Up to 1080p60 · 8000 kbps"
    Platform.CUSTOM -> "Any RTMP/RTMPS server, e.g. a test server"
  }

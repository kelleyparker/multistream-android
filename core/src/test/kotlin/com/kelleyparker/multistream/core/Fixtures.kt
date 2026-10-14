package com.kelleyparker.multistream.core

val youtube = Destination("yt", Platform.YOUTUBE, streamKey = "yt-key")
val twitch = Destination("tw", Platform.TWITCH, streamKey = "live_123")
val kick = Destination(
  "kk", Platform.KICK,
  serverUrl = "rtmps://fa723fc1b171.global-contribute.live-video.net:443/app",
  streamKey = "sk_us-west-2_abc",
)

fun quality(preset: QualityPreset, custom: CustomQuality = CustomQuality()) = QualitySettings(preset, custom)

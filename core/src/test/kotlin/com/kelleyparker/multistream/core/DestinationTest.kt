package com.kelleyparker.multistream.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationTest {
  @Test
  fun `kick's bare ivs host gets the app segment`() {
    val host = "rtmps://fa723fc1b171.global-contribute.live-video.net"
    assertEquals("$host/app/sk_us-west-2_x", buildEndpoint(host, "sk_us-west-2_x"))
    assertEquals("$host:443/app/sk_us-west-2_x", buildEndpoint("$host:443/", "sk_us-west-2_x"))
    assertEquals("$host:443/app/sk_us-west-2_x", buildEndpoint("$host:443/app/", "sk_us-west-2_x"))
    assertEquals("rtmp://host:1935/sk_x", buildEndpoint("rtmp://host:1935", "sk_x"))
  }

  @Test
  fun `endpoint joins server and key with one slash`() {
    assertEquals("rtmp://live.twitch.tv/app/live_1", buildEndpoint("rtmp://live.twitch.tv/app/", "/live_1"))
    assertEquals("rtmp://live.twitch.tv/app/live_1", buildEndpoint(" rtmp://live.twitch.tv/app ", " live_1 "))
    assertEquals("rtmp://host:1935/live", buildEndpoint("rtmp://host:1935/live", ""))
  }

  @Test
  fun `presets fill the recommended server`() {
    assertEquals("rtmps://a.rtmps.youtube.com:443/live2", Destination("a", Platform.YOUTUBE).serverUrl)
    assertEquals("rtmp://live.twitch.tv/app", Destination("b", Platform.TWITCH).serverUrl)
    assertEquals("", Destination("c", Platform.KICK).serverUrl)
  }

  @Test
  fun `twitch bandwidth test appends the documented flag`() {
    val d = twitch.copy(twitchBandwidthTest = true)
    assertEquals("rtmp://live.twitch.tv/app/live_123?bandwidthtest=true", d.endpoint)
  }

  @Test
  fun `host and port parsing`() {
    assertEquals(HostPort("a.rtmps.youtube.com", 443, true), hostPortOf("rtmps://a.rtmps.youtube.com:443/live2"))
    assertEquals(HostPort("live.twitch.tv", 1935, false), hostPortOf("rtmp://live.twitch.tv/app"))
    assertEquals(HostPort("192.168.1.20", 1935, false), hostPortOf("rtmp://192.168.1.20:1935/live"))
    assertNull(hostPortOf("http://example.com"))
  }

  @Test
  fun `json round trip`() {
    val list = listOf(youtube, twitch.copy(enabled = false), kick)
    assertEquals(list, DestinationJson.decode(DestinationJson.encode(list)))
    assertEquals(emptyList<Destination>(), DestinationJson.decode("not json"))
  }

  @Test
  fun `custom servers are labelled by their address, never their key`() {
    assertEquals("Custom: kick", Destination("a", Platform.CUSTOM, serverUrl = "rtmp://192.168.1.20:1935/live/kick", streamKey = "secret").label)
    assertEquals("Custom: 192.168.1.20", Destination("b", Platform.CUSTOM, serverUrl = "rtmp://192.168.1.20:1935").label)
    assertEquals("Twitch", twitch.label)
  }
}

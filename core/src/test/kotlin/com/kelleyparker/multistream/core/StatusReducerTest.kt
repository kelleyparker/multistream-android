package com.kelleyparker.multistream.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusReducerTest {
  @Test
  fun `a failure in one destination leaves the others live`() {
    var s = mapOf<String, DestinationStatus>()
    for (id in listOf("yt", "tw", "kk")) {
      s = StatusReducer.reduce(s, id, ConnectionEvent.Started)
      s = StatusReducer.reduce(s, id, ConnectionEvent.Connected)
    }
    s = StatusReducer.reduce(s, "tw", ConnectionEvent.Lost("Connection refused", willRetry = true))

    assertEquals(DestinationStatus.Live(), s["yt"])
    assertEquals(DestinationStatus.Live(), s["kk"])
    assertTrue(s["tw"] is DestinationStatus.Reconnecting)
  }

  @Test
  fun `retries count up and then fail`() {
    var s = mapOf<String, DestinationStatus>()
    s = StatusReducer.reduce(s, "tw", ConnectionEvent.Lost("timeout", willRetry = true))
    s = StatusReducer.reduce(s, "tw", ConnectionEvent.Started)
    s = StatusReducer.reduce(s, "tw", ConnectionEvent.Lost("timeout", willRetry = true))
    assertEquals(2, (s["tw"] as DestinationStatus.Reconnecting).attempt)
    s = StatusReducer.reduce(s, "tw", ConnectionEvent.Lost("timeout", willRetry = false))
    assertEquals(DestinationStatus.Failed("The server didn't answer in time."), s["tw"])
    s = StatusReducer.reduce(s, "tw", ConnectionEvent.Connected)
    assertEquals(DestinationStatus.Live(), s["tw"])
  }

  @Test
  fun `bitrate only updates live destinations`() {
    var s = StatusReducer.reduce(emptyMap(), "kk", ConnectionEvent.Bitrate(6_000_000))
    assertEquals(DestinationStatus.Idle, s["kk"])
    s = StatusReducer.reduce(s, "kk", ConnectionEvent.Connected)
    s = StatusReducer.reduce(s, "kk", ConnectionEvent.Bitrate(6_000_000))
    assertEquals(DestinationStatus.Live(6_000), s["kk"])
  }

  @Test
  fun `auth errors explain the stream key`() {
    val s = StatusReducer.reduce(emptyMap(), "yt", ConnectionEvent.AuthFailed)
    assertTrue((s["yt"] as DestinationStatus.Failed).reason.contains("stream key"))
  }

  @Test
  fun `a server hang-up during publish gets plain words`() {
    val s = StatusReducer.reduce(emptyMap(), "kk", ConnectionEvent.Lost("Error configure stream, End of stream", willRetry = true))
    val status = s["kk"] as DestinationStatus.Reconnecting
    assertEquals("The server closed the connection. Check the stream key and server URL.", status.reason)
    assertNull(status.detail)
  }

  @Test
  fun `unrecognised errors keep the raw text as a detail`() {
    val s = StatusReducer.reduce(emptyMap(), "kk", ConnectionEvent.Lost("onStatus: NetStream.Publish.BadName", willRetry = false))
    assertEquals(DestinationStatus.Failed("Connection failed.", "onStatus: NetStream.Publish.BadName"), s["kk"])
  }
}

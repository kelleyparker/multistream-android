package com.kelleyparker.multistream.core

sealed interface DestinationStatus {
  data object Idle : DestinationStatus
  data object Connecting : DestinationStatus
  data class Live(val bitrateKbps: Int = 0) : DestinationStatus
  data class Reconnecting(val attempt: Int, val reason: String) : DestinationStatus
  data class Failed(val reason: String) : DestinationStatus
}

/** Events reported by one destination's RTMP connection. */
sealed interface ConnectionEvent {
  data object Started : ConnectionEvent
  data object Connected : ConnectionEvent
  data class Bitrate(val bitsPerSecond: Long) : ConnectionEvent
  data class Lost(val reason: String, val willRetry: Boolean) : ConnectionEvent
  data object AuthFailed : ConnectionEvent
  data object Stopped : ConnectionEvent
}

/**
 * Pure state update for the per-destination status map. Each event only touches its own
 * destination, which is what keeps one failing platform from affecting the others.
 */
object StatusReducer {
  fun reduce(
    statuses: Map<String, DestinationStatus>,
    destinationId: String,
    event: ConnectionEvent,
  ): Map<String, DestinationStatus> {
    val current = statuses[destinationId] ?: DestinationStatus.Idle
    val next = when (event) {
      ConnectionEvent.Started -> if (current is DestinationStatus.Reconnecting) current else DestinationStatus.Connecting
      ConnectionEvent.Connected -> DestinationStatus.Live()
      is ConnectionEvent.Bitrate ->
        if (current is DestinationStatus.Live) DestinationStatus.Live((event.bitsPerSecond / 1000).toInt()) else current
      is ConnectionEvent.Lost ->
        if (event.willRetry) {
          val attempt = (current as? DestinationStatus.Reconnecting)?.attempt?.plus(1) ?: 1
          DestinationStatus.Reconnecting(attempt, friendlyReason(event.reason))
        } else {
          DestinationStatus.Failed(friendlyReason(event.reason))
        }
      ConnectionEvent.AuthFailed -> DestinationStatus.Failed("The stream key was rejected. Re-paste it from your dashboard.")
      ConnectionEvent.Stopped -> DestinationStatus.Idle
    }
    return statuses + (destinationId to next)
  }

  /** Turns low-level socket errors into something a streamer can act on. */
  fun friendlyReason(reason: String): String {
    val lower = reason.lowercase()
    return when {
      "unknownhost" in lower || "unable to resolve" in lower -> "Can't find the server. Check the address and your internet."
      "refused" in lower -> "The server refused the connection. Check the address and port."
      "timeout" in lower || "timed out" in lower -> "The server didn't answer in time."
      "endpoint malformed" in lower -> "The server address isn't valid."
      "certificate" in lower || "ssl" in lower || "tls" in lower -> "Secure connection failed. Check that the address uses rtmps://."
      "broken pipe" in lower || "reset" in lower -> "The connection dropped."
      reason.isBlank() -> "Connection failed."
      else -> reason
    }
  }
}

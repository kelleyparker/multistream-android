package com.kelleyparker.multistream.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class Destination(
  val id: String,
  val platform: Platform,
  val name: String = platform.displayName,
  val serverUrl: String = platform.limits.ingestOptions.firstOrNull { it.recommended }?.url.orEmpty(),
  val streamKey: String = "",
  val enabled: Boolean = true,
  /** Twitch only: append Twitch's `?bandwidthtest=true` so the stream connects but never goes public. */
  val twitchBandwidthTest: Boolean = false,
) {
  /**
   * What the UI shows. Custom servers are told apart by the last part of their address
   * (e.g. "Custom: kick" for rtmp://host/live/kick); stream keys are never shown.
   */
  val label: String
    get() {
      if (platform != Platform.CUSTOM || name != platform.displayName) return name
      val path = serverUrl.trim().substringAfter("://", "").substringBefore('?').trimEnd('/')
      val tail = path.substringAfterLast('/').takeIf { path.contains('/') && it.isNotBlank() }
      return "Custom: " + (tail ?: hostPortOf(serverUrl)?.host ?: "server")
    }

  /** The full publish URL handed to the RTMP client. */
  val endpoint: String
    get() {
      val base = buildEndpoint(serverUrl, streamKey)
      return if (platform == Platform.TWITCH && twitchBandwidthTest) "$base?bandwidthtest=true" else base
    }
}

/** Joins a server URL and stream key with exactly one slash, ignoring stray whitespace. */
fun buildEndpoint(serverUrl: String, streamKey: String): String {
  val server = serverUrl.trim().trimEnd('/')
  val key = streamKey.trim().trimStart('/')
  return if (key.isEmpty()) server else "$server/$key"
}

enum class UrlScheme { RTMP, RTMPS, INVALID }

fun schemeOf(url: String): UrlScheme {
  val trimmed = url.trim().lowercase()
  return when {
    trimmed.startsWith("rtmps://") && trimmed.length > "rtmps://".length -> UrlScheme.RTMPS
    trimmed.startsWith("rtmp://") && trimmed.length > "rtmp://".length -> UrlScheme.RTMP
    else -> UrlScheme.INVALID
  }
}

/** Host and port, used by the setup wizard's "Check connection" button. */
data class HostPort(val host: String, val port: Int, val tls: Boolean)

fun hostPortOf(url: String): HostPort? {
  val scheme = schemeOf(url)
  if (scheme == UrlScheme.INVALID) return null
  val rest = url.trim().substringAfter("://").substringBefore('/').substringBefore('?')
  if (rest.isEmpty()) return null
  val host = rest.substringBefore(':')
  val explicitPort = rest.substringAfter(':', "").toIntOrNull()
  val tls = scheme == UrlScheme.RTMPS
  return HostPort(host, explicitPort ?: if (tls) 443 else 1935, tls)
}

object DestinationJson {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val serializer = ListSerializer(Destination.serializer())

  fun encode(destinations: List<Destination>): String = json.encodeToString(serializer, destinations)

  fun decode(text: String): List<Destination> =
    if (text.isBlank()) emptyList() else runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
}

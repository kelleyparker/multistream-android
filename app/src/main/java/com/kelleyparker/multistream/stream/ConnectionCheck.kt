package com.kelleyparker.multistream.stream

import com.kelleyparker.multistream.core.hostPortOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * The setup wizard's "Check connection": opens a TCP (and for rtmps, TLS) connection to the
 * ingest server without publishing anything, so it can never put you live by accident.
 */
object ConnectionCheck {

  sealed interface Result {
    data class Reachable(val millis: Long) : Result
    data class Unreachable(val message: String) : Result
  }

  suspend fun check(serverUrl: String, timeoutMillis: Int = 5_000): Result = withContext(Dispatchers.IO) {
    val target = hostPortOf(serverUrl) ?: return@withContext Result.Unreachable("That isn't an rtmp:// or rtmps:// address.")
    val started = System.nanoTime()
    try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(target.host, target.port), timeoutMillis)
        if (target.tls) {
          val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
          (factory.createSocket(socket, target.host, target.port, false) as SSLSocket).use { it.startHandshake() }
        }
      }
      Result.Reachable((System.nanoTime() - started) / 1_000_000)
    } catch (e: Exception) {
      Result.Unreachable(
        when (e) {
          is java.net.UnknownHostException -> "Can't find ${target.host}. Check the address and your internet."
          is java.net.SocketTimeoutException -> "${target.host} didn't answer in time."
          is javax.net.ssl.SSLException -> "Secure connection to ${target.host} failed."
          else -> "Couldn't reach ${target.host}:${target.port}."
        },
      )
    }
  }
}

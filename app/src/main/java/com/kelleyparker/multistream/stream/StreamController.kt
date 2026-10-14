package com.kelleyparker.multistream.stream

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodecInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import com.kelleyparker.multistream.core.AUDIO_SAMPLE_RATE
import com.kelleyparker.multistream.core.ConnectionEvent
import com.kelleyparker.multistream.core.DestinationStatus
import com.kelleyparker.multistream.core.KEYFRAME_INTERVAL_SECONDS
import com.kelleyparker.multistream.core.StatusReducer
import com.kelleyparker.multistream.core.TierId
import com.kelleyparker.multistream.core.TierPlan
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.`object`.ImageFilterRender
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The only class that talks to RootEncoder. It turns a [TierPlan] into a [TieredMultiStream],
 * drives the camera preview, and reports one [DestinationStatus] per destination.
 *
 * Each destination connects, retries and fails on its own: a dead Kick server never stops the
 * YouTube and Twitch streams that share the same encoders.
 */
class StreamController(private val context: Context) {

  private val main = Handler(Looper.getMainLooper())
  private val _statuses = MutableStateFlow<Map<String, DestinationStatus>>(emptyMap())
  val statuses: StateFlow<Map<String, DestinationStatus>> = _statuses.asStateFlow()
  private val _isLive = MutableStateFlow(false)
  val isLive: StateFlow<Boolean> = _isLive.asStateFlow()
  private val _muted = MutableStateFlow(false)
  val muted: StateFlow<Boolean> = _muted.asStateFlow()
  private val _error = MutableStateFlow<String?>(null)
  /** A problem preparing the camera or encoders, shown on the Live screen. */
  val error: StateFlow<String?> = _error.asStateFlow()

  private var stream: TieredMultiStream? = null
  private var plan: TierPlan = TierPlan.EMPTY
  private var slots: Map<String, Pair<TierId, Int>> = emptyMap()
  private var endpoints: Map<String, String> = emptyMap()
  private val wantsLive = mutableSetOf<String>()
  private var previewView: SurfaceView? = null
  private var overlay: ImageFilterRender? = null

  /** Rebuilds the encoders for [newPlan]. Ignored while live; returns false if the phone refused the settings. */
  fun configure(newPlan: TierPlan): Boolean {
    if (_isLive.value) return false
    if (newPlan == plan && stream != null) return true
    stream?.let { old ->
      if (old.isOnPreview) old.stopPreview()
      old.release()
    }
    stream = null
    plan = newPlan
    endpoints = newPlan.tiers.flatMap { it.destinations }.associate { it.id to it.endpoint }
    _statuses.value = endpoints.keys.associateWith { DestinationStatus.Idle }
    if (newPlan.tiers.isEmpty()) return true

    val high = newPlan.tiers.first { it.id == TierId.HIGH }
    val second = newPlan.tiers.firstOrNull { it.id == TierId.MAIN }
    slots = buildMap {
      high.destinations.forEachIndexed { i, d -> put(d.id, TierId.HIGH to i) }
      second?.destinations?.forEachIndexed { i, d -> put(d.id, TierId.MAIN to i) }
    }
    val created = TieredMultiStream(
      context,
      high.destinations.map { checkerFor(it.id) },
      second?.destinations?.map { checkerFor(it.id) }.orEmpty(),
      Camera2Source(context),
      MicrophoneSource(),
    )
    val fps = high.format.frameRate.fps
    fun prepareWith(profile: Int) = runCatching {
      created.prepare(
        high.format.resolution.width, high.format.resolution.height, high.videoKbps * 1000, fps, KEYFRAME_INTERVAL_SECONDS,
        second?.format?.resolution?.width ?: 0, second?.format?.resolution?.height ?: 0,
        (second?.videoKbps ?: 0) * 1000, profile,
      )
    }.getOrElse { Log.w(TAG, "prepareVideo failed", it); false }
    // Kick asks for Main profile; otherwise High looks better per bit. Both tiers share the setting.
    // Fall back to the encoder's default if the phone refuses it.
    val profile = if (newPlan.useMainProfile) MediaCodecInfo.CodecProfileLevel.AVCProfileMain else MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
    created.forceBt709Color(true) // Rec. 709, as YouTube recommends for SDR
    val videoReady = prepareWith(profile) || prepareWith(-1)
    val audioReady = runCatching {
      created.prepareAudio(AUDIO_SAMPLE_RATE, true, newPlan.audio.kbps * 1000, echoCanceler = false, noiseSuppressor = true)
    }.getOrDefault(false)
    if (!videoReady || !audioReady) {
      _error.value = if (!videoReady) {
        "This phone couldn't start the video encoder with these settings. Try the Standard preset."
      } else {
        "The microphone couldn't start. Check that no other app is using it."
      }
      created.release()
      return false
    }
    _error.value = null
    created.getGlInterface().forceOrientation(OrientationForced.LANDSCAPE)
    if (_muted.value) (created.audioSource as? MicrophoneSource)?.mute()
    // Retries are driven from onConnectionFailed so each destination backs off on its own.
    for ((tier, index) in slots.values) created.client(tier, index).setReTries(MAX_RETRIES)
    stream = created
    previewView?.let { startPreview(it) }
    return true
  }

  fun attachPreview(view: SurfaceView) {
    previewView = view
    startPreview(view)
  }

  fun detachPreview() {
    previewView = null
    stream?.let { if (it.isOnPreview && !_isLive.value) it.stopPreview(removeCallbacks = true) }
  }

  private fun startPreview(view: SurfaceView) {
    val s = stream ?: return
    if (!s.isOnPreview) runCatching { s.startPreview(view, autoHandle = true) }.onFailure { Log.w(TAG, "preview failed", it) }
  }

  fun goLive() {
    val s = stream ?: return
    _isLive.value = true
    for (id in slots.keys) startOne(s, id)
  }

  fun stopAll() {
    wantsLive.clear()
    stream?.stopAllClients()
    _statuses.update { current -> current.mapValues { DestinationStatus.Idle } }
    _isLive.value = false
  }

  fun stopOne(id: String) {
    val s = stream ?: return
    val slot = slots[id] ?: return
    wantsLive -= id
    s.stopClient(slot.first, slot.second)
    report(id, ConnectionEvent.Stopped)
    if (wantsLive.isEmpty()) _isLive.value = false
  }

  /** Restarts one destination (after a failure, or after it was stopped) while the others keep going. */
  fun retryOne(id: String) {
    val s = stream ?: return
    val slot = slots[id] ?: return
    if (id in wantsLive) s.stopClient(slot.first, slot.second)
    s.client(slot.first, slot.second).setReTries(MAX_RETRIES)
    _isLive.value = true
    startOne(s, id)
  }

  private fun startOne(s: TieredMultiStream, id: String) {
    val slot = slots[id] ?: return
    val endpoint = endpoints[id] ?: return
    wantsLive += id
    report(id, ConnectionEvent.Started)
    s.startClient(slot.first, slot.second, endpoint)
  }

  fun switchCamera() {
    (stream?.videoSource as? Camera2Source)?.let { runCatching { it.switchCamera() } }
  }

  fun setMuted(muted: Boolean) {
    _muted.value = muted
    (stream?.audioSource as? MicrophoneSource)?.let { if (muted) it.mute() else it.unMute() }
  }

  /** v2 overlay hook: draws a full-frame transparent bitmap over the camera in every tier. */
  fun setOverlay(bitmap: Bitmap?) {
    val gl = stream?.getGlInterface() ?: return
    overlay?.let { gl.removeFilter(it) }
    overlay = null
    if (bitmap == null) return
    overlay = ImageFilterRender().apply {
      setImage(bitmap)
      setScale(100f, 100f)
      setPosition(0f, 0f)
    }.also { gl.addFilter(it) }
  }

  fun release() {
    stopAll()
    stream?.release()
    stream = null
    plan = TierPlan.EMPTY
  }

  private fun report(id: String, event: ConnectionEvent) {
    _statuses.update { StatusReducer.reduce(it, id, event) }
  }

  private fun checkerFor(id: String) = object : ConnectChecker {
    override fun onConnectionStarted(url: String) = Unit

    override fun onConnectionSuccess() = report(id, ConnectionEvent.Connected)

    override fun onConnectionFailed(reason: String) {
      main.post {
        val s = stream ?: return@post
        val slot = slots[id] ?: return@post
        if (id !in wantsLive) return@post
        val attempt = (_statuses.value[id] as? DestinationStatus.Reconnecting)?.attempt ?: 0
        val delay = (RETRY_BASE_MS shl attempt.coerceAtMost(4)).coerceAtMost(RETRY_MAX_MS)
        val willRetry = s.client(slot.first, slot.second).reTry(delay, reason)
        report(id, ConnectionEvent.Lost(reason, willRetry))
        if (!willRetry) giveUp(s, id)
      }
    }

    override fun onNewBitrate(bitrate: Long) = report(id, ConnectionEvent.Bitrate(bitrate))

    override fun onDisconnect() = Unit

    override fun onAuthError() {
      main.post {
        val s = stream ?: return@post
        report(id, ConnectionEvent.AuthFailed)
        giveUp(s, id)
      }
    }

    override fun onAuthSuccess() = Unit
  }

  /** Stops one destination after it has failed for good; the others are untouched. */
  private fun giveUp(s: TieredMultiStream, id: String) {
    val slot = slots[id] ?: return
    if (wantsLive.remove(id)) s.stopClient(slot.first, slot.second)
    if (wantsLive.isEmpty()) _isLive.value = false
  }

  private companion object {
    const val TAG = "StreamController"
    const val MAX_RETRIES = 10
    const val RETRY_BASE_MS = 2_000L
    const val RETRY_MAX_MS = 30_000L
  }
}

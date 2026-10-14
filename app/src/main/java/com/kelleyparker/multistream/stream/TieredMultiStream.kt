package com.kelleyparker.multistream.stream

import android.content.Context
import android.media.MediaCodec
import android.util.Size
import com.kelleyparker.multistream.core.TierId
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.VideoSource
import com.pedro.library.base.StreamBase
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.library.util.streamclient.StreamBaseClient
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.rtmp.rtmp.RtmpClient
import java.nio.ByteBuffer

/**
 * One camera, up to two H.264 encoders, any number of RTMP(S) destinations per encoder.
 *
 * Modeled on RootEncoder's `MultiStream`: the [TierId.HIGH] clients get the main encoder, the
 * [TierId.MAIN] clients get StreamBase's second encoder through [TierForwarder]. Encoders start
 * with the first destination and stop after the last, so destinations come and go independently.
 */
class TieredMultiStream(
  context: Context,
  highCheckers: List<ConnectChecker>,
  mainCheckers: List<ConnectChecker>,
  videoSource: VideoSource = Camera2Source(context),
  audioSource: AudioSource = MicrophoneSource(),
) : StreamBase(context, videoSource, audioSource) {

  private val keyframeListener = object : StreamClientListener {
    override fun onRequestKeyframe() {
      requestKeyframe()
    }
  }

  private val highClients = highCheckers.map { RtmpClient(it) }
  private val mainClients = mainCheckers.map { RtmpClient(it) }
  private val highStreamClients = highClients.map { RtmpStreamClient(it, keyframeListener) }
  private val mainStreamClients = mainClients.map { RtmpStreamClient(it, keyframeListener) }
  private val active = mutableSetOf<Pair<TierId, Int>>()
  private var mainSize: Size? = null

  init {
    setRecordController(TierForwarder(mainClients))
  }

  /**
   * Prepares the encoders. [mainWidth]/[mainHeight] > 0 turns on the second encoder; both tiers
   * must be 16:9 and share [fps] because they are drawn from the same camera frames.
   */
  fun prepare(
    highWidth: Int, highHeight: Int, highBitrate: Int, fps: Int, keyframeSeconds: Int,
    mainWidth: Int, mainHeight: Int, mainBitrate: Int, profile: Int,
  ): Boolean {
    val dual = mainWidth > 0 && mainHeight > 0
    mainSize = if (dual) Size(mainWidth, mainHeight) else null
    return prepareVideo(
      highWidth, highHeight, highBitrate, fps, keyframeSeconds,
      rotation = 0, profile = profile, level = -1,
      recordWidth = if (dual) mainWidth else 0,
      recordHeight = if (dual) mainHeight else 0,
      recordBitrate = if (dual) mainBitrate else highBitrate,
    )
  }

  fun client(tier: TierId, index: Int): StreamBaseClient = when (tier) {
    TierId.HIGH -> highStreamClients[index]
    TierId.MAIN -> mainStreamClients[index]
  }

  fun startClient(tier: TierId, index: Int, endpoint: String) {
    if (!isStreaming) super.startStream("")
    val rtmp = rtmpClient(tier, index)
    val size = if (tier == TierId.MAIN) mainSize ?: getVideoResolution() else getVideoResolution()
    rtmp.setVideoResolution(size.width, size.height)
    rtmp.setFps(getVideoFps())
    active += tier to index
    rtmp.connect(endpoint)
    requestKeyframe()
  }

  fun stopClient(tier: TierId, index: Int) {
    active -= tier to index
    rtmpClient(tier, index).disconnect()
    if (active.isEmpty() && isStreaming) super.stopStream()
  }

  fun stopAllClients() {
    for ((tier, index) in active.toList()) stopClient(tier, index)
  }

  private fun rtmpClient(tier: TierId, index: Int): RtmpClient = when (tier) {
    TierId.HIGH -> highClients[index]
    TierId.MAIN -> mainClients[index]
  }

  private val allClients get() = highClients + mainClients

  override fun getStreamClient(): StreamBaseClient =
    throw IllegalStateException("Use client(tier, index) with TieredMultiStream")

  override fun setVideoCodecImp(codec: VideoCodec) {
    for (client in allClients) client.setVideoCodec(codec)
  }

  override fun setAudioCodecImp(codec: AudioCodec) {
    for (client in allClients) client.setAudioCodec(codec)
  }

  override fun onAudioInfoImp(sampleRate: Int, isStereo: Boolean) {
    for (client in allClients) client.setAudioInfo(sampleRate, isStereo)
  }

  // Main-encoder output goes to the HIGH tier. Audio goes to HIGH here and to MAIN via TierForwarder.
  override fun onVideoInfoImp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    for (client in highClients) client.setVideoInfo(sps.duplicate(), pps?.duplicate(), vps?.duplicate())
  }

  override fun getVideoDataImp(videoBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    for (client in highClients) client.sendVideo(videoBuffer.duplicate(), info)
  }

  override fun getAudioDataImp(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
    for (client in highClients) client.sendAudio(audioBuffer.duplicate(), info)
  }

  override fun startStreamImp(endPoint: String) = Unit

  override fun stopStreamImp() = Unit
}

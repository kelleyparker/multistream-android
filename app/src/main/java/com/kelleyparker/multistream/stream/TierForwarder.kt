package com.kelleyparker.multistream.stream

import android.media.MediaCodec
import android.media.MediaFormat
import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.encoder.video.VideoEncoderHelper
import com.pedro.library.base.recording.RecordController
import com.pedro.rtmp.rtmp.RtmpClient
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Sends RootEncoder's second ("record") encoder to RTMP clients instead of an MP4 file.
 *
 * StreamBase already runs a second hardware encoder at a different resolution and bitrate when
 * `prepareVideo` gets record dimensions, and hands its output to the installed [RecordController].
 * Plugging this in gives a second stream tier (e.g. Twitch and Kick at 1080p while YouTube gets
 * 1440p) that shares the camera, GL pipeline and AAC audio with the main encoder.
 *
 * [isRunning] stays false so StreamBase never treats the forwarder as an active recording.
 */
class TierForwarder(private val clients: List<RtmpClient>) : RecordController {

  @Volatile private var hasVideoInfo = false
  private var videoCodec = VideoCodec.H264
  private var audioCodec = AudioCodec.AAC

  override fun recordVideo(videoBuffer: ByteBuffer, videoInfo: MediaCodec.BufferInfo) {
    if (clients.isEmpty()) return
    // Some encoders never report a format change; their SPS/PPS only appears in the first buffer.
    if (!hasVideoInfo) {
      VideoEncoderHelper.decodeSpsPpsFromBuffer(videoBuffer.duplicate(), videoInfo.size)?.let { buffers ->
        sendVideoInfo(buffers.first, buffers.second)
      }
    }
    for (client in clients) client.sendVideo(videoBuffer.duplicate(), videoInfo)
  }

  override fun recordAudio(audioBuffer: ByteBuffer, audioInfo: MediaCodec.BufferInfo) {
    for (client in clients) client.sendAudio(audioBuffer.duplicate(), audioInfo)
  }

  override fun setVideoFormat(videoFormat: MediaFormat) {
    val sps = videoFormat.getByteBuffer("csd-0") ?: return
    sendVideoInfo(sps, videoFormat.getByteBuffer("csd-1"))
  }

  private fun sendVideoInfo(sps: ByteBuffer, pps: ByteBuffer?) {
    for (client in clients) client.setVideoInfo(sps.duplicate(), pps?.duplicate(), null)
    hasVideoInfo = true
  }

  override fun resetFormats() {
    hasVideoInfo = false
  }

  override fun setAudioFormat(audioFormat: MediaFormat) = Unit
  override fun startRecord(path: String, listener: RecordController.Listener?, tracks: RecordController.RecordTracks) = Unit
  override fun startRecord(fd: FileDescriptor, listener: RecordController.Listener?, tracks: RecordController.RecordTracks) = Unit
  override fun stopRecord() = Unit
  override fun pauseRecord() = Unit
  override fun resumeRecord() = Unit
  override fun isRunning(): Boolean = false
  override fun isRecording(): Boolean = false
  override fun getStatus(): RecordController.Status = RecordController.Status.STOPPED
  override fun setRequestKeyFrame(requestKeyFrame: RecordController.RequestKeyFrame?) = Unit

  override fun setVideoCodec(videoCodec: VideoCodec) {
    this.videoCodec = videoCodec
  }

  override fun setAudioCodec(audioCodec: AudioCodec) {
    this.audioCodec = audioCodec
  }

  override fun updateInfo(videoCodec: VideoCodec, audioCodec: AudioCodec) {
    this.videoCodec = videoCodec
    this.audioCodec = audioCodec
  }

  override fun getVideoCodec(): VideoCodec = videoCodec
  override fun getAudioCodec(): AudioCodec = audioCodec
}

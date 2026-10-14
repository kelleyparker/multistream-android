package com.kelleyparker.multistream.stream

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.kelleyparker.multistream.core.CameraMode

/** Reads what each camera can deliver to the GL pipeline (a SurfaceTexture), per size. */
object CameraModesProbe {

  /** Modes of the first back camera, the one RootEncoder opens. Empty if unknown. */
  fun back(context: Context): List<CameraMode> = runCatching {
    val manager = context.getSystemService(CameraManager::class.java)
    val id = manager.cameraIdList.firstOrNull {
      manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } ?: return emptyList()
    val map = manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
      ?: return emptyList()
    map.getOutputSizes(SurfaceTexture::class.java).orEmpty().map { size ->
      val frameNanos = map.getOutputMinFrameDuration(SurfaceTexture::class.java, size)
      val maxFps = if (frameNanos > 0) Math.round(1e9 / frameNanos).toInt() else 30
      CameraMode(size.width, size.height, maxFps)
    }
  }.getOrDefault(emptyList())
}

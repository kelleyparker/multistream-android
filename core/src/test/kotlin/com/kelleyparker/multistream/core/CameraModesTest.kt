package com.kelleyparker.multistream.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraModesTest {
  // Nothing Phone (4a) Pro back camera: no 1440p, and 4K only reaches 30 fps.
  private val nothing = listOf(
    CameraMode(640, 360, 30),
    CameraMode(1280, 720, 60),
    CameraMode(1920, 1080, 60),
    CameraMode(1920, 1440, 60),
    CameraMode(3840, 2160, 30),
    CameraMode(4096, 2304, 30),
  )

  @Test
  fun `1440p60 rounded up to a 30 fps 4K mode is replaced by 1080p60`() {
    assertEquals(CameraMode(1920, 1080, 60), pickCaptureMode(nothing, 2560, 1440, 60))
  }

  @Test
  fun `a mode that keeps up is left to RootEncoder`() {
    assertNull(pickCaptureMode(nothing, 2560, 1440, 30))
    assertNull(pickCaptureMode(nothing, 1920, 1080, 60))
    assertNull(pickCaptureMode(nothing + CameraMode(2560, 1440, 60), 2560, 1440, 60))
  }

  @Test
  fun `a slow exact mode moves to the smallest bigger one that keeps up`() {
    val modes = listOf(CameraMode(1920, 1080, 30), CameraMode(2560, 1440, 60), CameraMode(3840, 2160, 60))
    assertEquals(CameraMode(2560, 1440, 60), pickCaptureMode(modes, 1920, 1080, 60))
  }

  @Test
  fun `nothing fast enough leaves it to RootEncoder`() {
    assertNull(pickCaptureMode(listOf(CameraMode(1920, 1080, 30)), 1920, 1080, 60))
    assertNull(pickCaptureMode(emptyList(), 1920, 1080, 60))
  }
}

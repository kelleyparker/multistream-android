package com.kelleyparker.multistream.core

/** One size the camera can deliver, and the highest frame rate it reaches at that size. */
data class CameraMode(val width: Int, val height: Int, val maxFps: Int)

/**
 * The camera size to force for a [width]x[height] stream at [fps], or null to keep RootEncoder's own
 * choice. RootEncoder uses the exact size, else the next bigger (or smaller) one of the same shape,
 * and asks for [fps] regardless; a size that can't run that fast delivers no frames at all (1440p60
 * rounded up to a 4K mode that tops out at 30 fps), so every stream goes out with audio only.
 *
 * Only when RootEncoder's choice can't keep up, this takes the smallest same-shape size at least as
 * big as the stream that does, else the biggest one that does (the GL pipeline upscales).
 */
fun pickCaptureMode(modes: List<CameraMode>, width: Int, height: Int, fps: Int): CameraMode? {
  val sameShape = modes.filter { it.width.toLong() * height == it.height.toLong() * width }
  val default = sameShape.firstOrNull { it.width == width }
    ?: sameShape.filter { it.width > width }.minByOrNull { it.width }
    ?: sameShape.maxByOrNull { it.width }
    ?: return null
  if (default.maxFps >= fps) return null
  val usable = sameShape.filter { it.maxFps >= fps }
  return usable.filter { it.width >= width }.minByOrNull { it.width } ?: usable.maxByOrNull { it.width }
}

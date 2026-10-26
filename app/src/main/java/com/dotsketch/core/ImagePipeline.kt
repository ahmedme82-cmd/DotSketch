package com.dotsketch.core

import android.graphics.Bitmap
import android.graphics.Color

object ImagePipeline {

    /** bitmap -> grayscale -> (optional blur) -> threshold -> contours -> noise filter. */
    fun process(bitmap: Bitmap, options: ProcessOptions): ProcessResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = Color.alpha(p)
            // transparent pixels count as white paper
            val lum = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
            gray[i] = (lum * a + 255 * (255 - a)) / 255
        }

        if (options.denoise) gray = boxBlur3(gray, w, h)

        val binary = BooleanArray(w * h)
        for (i in gray.indices) {
            val dark = gray[i] < options.threshold
            binary[i] = if (options.invert) !dark else dark
        }

        val raw = ContourTracer.trace(binary, w, h)
        val filtered = ContourFilter.filterByArea(raw, options.minContourAreaPx)
        return ProcessResult(filtered, w, h, if (filtered.isEmpty()) "No contours found." else null)
    }

    private fun boxBlur3(src: IntArray, w: Int, h: Int): IntArray {
        val dst = IntArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0
                var n = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        sum += src[yy * w + xx]
                        n++
                    }
                }
                dst[y * w + x] = sum / n
            }
        }
        return dst
    }
}

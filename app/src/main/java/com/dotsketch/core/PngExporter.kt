package com.dotsketch.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PngExporter {

    /** Renders the page at up to [maxDpi], capped so the long side stays under [maxSidePx]. */
    fun export(layout: PageLayoutResult, out: OutputStream, maxDpi: Float = 300f, maxSidePx: Int = 4000) {
        val longInches = max(layout.pageWidthMm, layout.pageHeightMm) / 25.4f
        val dpi = min(maxDpi, maxSidePx / longInches)
        val pxPerMm = dpi / 25.4f

        val wPx = (layout.pageWidthMm * pxPerMm).roundToInt().coerceAtLeast(1)
        val hPx = (layout.pageHeightMm * pxPerMm).roundToInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        try {
            bmp.density = dpi.roundToInt()
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            for (d in layout.dots) {
                canvas.drawCircle(d.xMm * pxPerMm, d.yMm * pxPerMm, d.radiusMm * pxPerMm, paint)
            }
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            bmp.recycle()
        }
    }
}

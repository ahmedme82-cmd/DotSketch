package com.dotsketch.core

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.roundToInt

object PdfExporter {

    /** One-page PDF in points (1 pt = 1/72 in). Dot positions are converted from true millimetres. */
    fun export(layout: PageLayoutResult, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val wPt = PageLayout.mmToPt(layout.pageWidthMm).roundToInt()
            val hPt = PageLayout.mmToPt(layout.pageHeightMm).roundToInt()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(wPt, hPt, 1).create())

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            val canvas = page.canvas
            for (d in layout.dots) {
                canvas.drawCircle(
                    PageLayout.mmToPt(d.xMm),
                    PageLayout.mmToPt(d.yMm),
                    PageLayout.mmToPt(d.radiusMm),
                    paint
                )
            }
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}

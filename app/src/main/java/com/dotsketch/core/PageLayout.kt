package com.dotsketch.core

import kotlin.math.max
import kotlin.math.min

object PageLayout {

    private const val MM_PER_INCH = 25.4f
    private const val PT_PER_INCH = 72f
    private const val CSS_PX_MM = 25.4f / 96f   // 1 image px = 1 CSS px when "fit to page" is off

    fun mmToPt(mm: Float): Float = mm * PT_PER_INCH / MM_PER_INCH

    fun pageDimensionsMm(settings: PageSettings): Pair<Float, Float> {
        val w = settings.paperSize.widthMm
        val h = settings.paperSize.heightMm
        return if (settings.orientation == PageOrientation.PORTRAIT) w to h else h to w
    }

    fun layoutFromContours(
        contours: List<List<Point2f>>,
        imageWidthPx: Int,
        imageHeightPx: Int,
        pageSettings: PageSettings,
        dotStyle: DotStyle
    ): PageLayoutResult {
        val (pageW, pageH) = pageDimensionsMm(pageSettings)

        fun empty(msg: String) = PageLayoutResult(emptyList(), pageW, pageH, msg)

        if (contours.isEmpty()) return empty("No contours detected. Try a different threshold.")
        if (dotStyle.spacingMm <= 0f || dotStyle.diameterMm <= 0f) {
            return empty("Dot spacing and diameter must be positive.")
        }
        if (imageWidthPx <= 0 || imageHeightPx <= 0) return empty("Image has no size.")

        val g = computeGeometry(imageWidthPx, imageHeightPx, pageSettings, dotStyle.diameterMm, pageW, pageH)
            ?: return empty("Margins are too large for this page and dot size.")

        val spacingPx = dotStyle.spacingMm / g.scaleMmPerPixel
        val sourceDots = DotSampler.sampleClosed(contours, spacingPx)
        if (sourceDots.isEmpty()) return empty("No dots generated. Try a smaller spacing.")

        val radius = dotStyle.diameterMm / 2f
        val dots = sourceDots.map {
            PageDot(
                xMm = g.offsetXMm + it.x * g.scaleMmPerPixel,
                yMm = g.offsetYMm + it.y * g.scaleMmPerPixel,
                radiusMm = radius
            )
        }
        return PageLayoutResult(dots, pageW, pageH, g.warning)
    }

    data class Geometry(
        val scaleMmPerPixel: Float,
        val offsetXMm: Float,
        val offsetYMm: Float,
        val warning: String? = null
    )

    /**
     * Dots are centred on contours, so the usable box is inset by one dot radius
     * on every side: no dot ever crosses the margin.
     */
    fun computeGeometry(
        imageWidthPx: Int,
        imageHeightPx: Int,
        pageSettings: PageSettings,
        dotDiameterMm: Float,
        pageWidthMm: Float,
        pageHeightMm: Float
    ): Geometry? {
        val radius = dotDiameterMm / 2f
        val inset = pageSettings.marginMm + radius
        val availW = pageWidthMm - 2f * inset
        val availH = pageHeightMm - 2f * inset
        if (availW <= 0f || availH <= 0f) return null

        val fitScale = min(availW / imageWidthPx, availH / imageHeightPx)
        val userScale = pageSettings.userScale.coerceIn(0.05f, 1f)
        val base = if (pageSettings.fitToPage) fitScale else CSS_PX_MM
        var scale = base * userScale
        var warning: String? = null

        if (scale > fitScale) {
            scale = fitScale
            warning = "Image is larger than the page at this scale, so it was shrunk to fit."
        }

        val contentW = imageWidthPx * scale
        val contentH = imageHeightPx * scale
        val offX = if (pageSettings.center) inset + (availW - contentW) / 2f else inset
        val offY = if (pageSettings.center) inset + (availH - contentH) / 2f else inset
        return Geometry(max(scale, 1e-6f), offX, offY, warning)
    }
}

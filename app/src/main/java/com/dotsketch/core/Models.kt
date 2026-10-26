package com.dotsketch.core

data class Point2f(val x: Float, val y: Float)

enum class PaperSize(val widthMm: Float, val heightMm: Float, val label: String) {
    A4(210f, 297f, "A4"),
    A3(297f, 420f, "A3"),
    US_LETTER(215.9f, 279.4f, "US Letter")
}

enum class PageOrientation { PORTRAIT, LANDSCAPE }

data class PageSettings(
    val paperSize: PaperSize,
    val orientation: PageOrientation,
    val marginMm: Float,
    val center: Boolean,
    val fitToPage: Boolean,
    val userScale: Float
)

data class DotStyle(val spacingMm: Float, val diameterMm: Float)

data class ProcessOptions(
    val threshold: Int,
    val invert: Boolean,
    val denoise: Boolean,
    val minContourAreaPx: Float
)

data class ProcessResult(
    val contours: List<List<Point2f>>,
    val widthPx: Int,
    val heightPx: Int,
    val message: String? = null
)

data class PageDot(val xMm: Float, val yMm: Float, val radiusMm: Float)

data class PageLayoutResult(
    val dots: List<PageDot>,
    val pageWidthMm: Float,
    val pageHeightMm: Float,
    val warning: String? = null
)

/** Everything the user can tweak in the UI. Persisted in SharedPreferences. */
data class UserSettings(
    val threshold: Int = 128,
    val invert: Boolean = false,
    val denoise: Boolean = true,
    val minContourAreaPx: Float = 40f,
    val spacingMm: Float = 3f,
    val diameterMm: Float = 1f,
    val paperSize: PaperSize = PaperSize.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val marginMm: Float = 10f,
    val center: Boolean = true,
    val fitToPage: Boolean = true,
    val userScale: Float = 1f
) {
    fun toProcessOptions() = ProcessOptions(threshold, invert, denoise, minContourAreaPx)
    fun toPageSettings() = PageSettings(paperSize, orientation, marginMm, center, fitToPage, userScale)
    fun toDotStyle() = DotStyle(spacingMm, diameterMm)
}

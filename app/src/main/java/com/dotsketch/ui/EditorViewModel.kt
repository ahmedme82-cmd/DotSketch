package com.dotsketch.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotsketch.core.BitmapLoader
import com.dotsketch.core.ImagePipeline
import com.dotsketch.core.PageLayout
import com.dotsketch.core.PageLayoutResult
import com.dotsketch.core.PdfExporter
import com.dotsketch.core.PngExporter
import com.dotsketch.core.PreferencesRepository
import com.dotsketch.core.ProcessOptions
import com.dotsketch.core.ProcessResult
import com.dotsketch.core.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ExportFormat(val extension: String, val mime: String) {
    PDF("pdf", "application/pdf"),
    PNG("png", "image/png")
}

enum class EditorMode { PHOTO, DRAW }

data class ShareRequest(val path: String, val mime: String)

/** One freehand stroke as a list of points (Compose coordinates). */
data class Stroke(val points: List<Offset>)

data class EditorUiState(
    val settings: UserSettings = UserSettings(),
    val mode: EditorMode = EditorMode.PHOTO,
    val bitmap: Bitmap? = null,
    val strokes: List<Stroke> = emptyList(),
    val currentStroke: List<Offset> = emptyList(),
    val layout: PageLayoutResult? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val share: ShareRequest? = null
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository(app)
    private val _state = MutableStateFlow(EditorUiState(settings = prefs.load()))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var job: Job? = null

    // Contours only depend on the image + ProcessOptions, so layout-only changes reuse them.
    private var cachedBitmap: Bitmap? = null
    private var cachedOptions: ProcessOptions? = null
    private var cachedResult: ProcessResult? = null

    fun setMode(mode: EditorMode) {
        _state.update {
            it.copy(
                mode = mode,
                message = null,
                // clear the other source when switching
                bitmap = if (mode == EditorMode.DRAW) null else it.bitmap,
                strokes = if (mode == EditorMode.PHOTO) emptyList() else it.strokes,
                currentStroke = emptyList(),
                layout = null
            )
        }
        if (mode == EditorMode.PHOTO && _state.value.bitmap != null) recompute()
    }

    fun update(transform: (UserSettings) -> UserSettings) {
        val newSettings = transform(_state.value.settings)
        _state.update { it.copy(settings = newSettings) }
        prefs.save(newSettings)
        // Only recompute if we already have something to process
        if (_state.value.bitmap != null || _state.value.strokes.isNotEmpty()) {
            recompute()
        }
    }

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, mode = EditorMode.PHOTO) }
            val bmp = try {
                withContext(Dispatchers.IO) { BitmapLoader.load(getApplication(), uri) }
            } catch (e: Exception) {
                null
            }
            if (bmp == null) {
                _state.update { it.copy(busy = false, message = "Couldn't open that image.") }
            } else {
                _state.update {
                    it.copy(
                        bitmap = bmp,
                        strokes = emptyList(),
                        currentStroke = emptyList()
                    )
                }
                recompute()
            }
        }
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    fun startStroke(point: Offset) {
        _state.update { it.copy(currentStroke = listOf(point), message = null) }
    }

    fun addPoint(point: Offset) {
        _state.update { it.copy(currentStroke = it.currentStroke + point) }
    }

    fun endStroke() {
        val pts = _state.value.currentStroke
        if (pts.size >= 2) {
            _state.update {
                it.copy(
                    strokes = it.strokes + Stroke(pts),
                    currentStroke = emptyList()
                )
            }
        } else {
            _state.update { it.copy(currentStroke = emptyList()) }
        }
    }

    fun undoStroke() {
        _state.update {
            if (it.currentStroke.isNotEmpty()) {
                it.copy(currentStroke = emptyList())
            } else if (it.strokes.isNotEmpty()) {
                it.copy(strokes = it.strokes.dropLast(1), layout = null)
            } else it
        }
    }

    fun clearStrokes() {
        _state.update {
            it.copy(
                strokes = emptyList(),
                currentStroke = emptyList(),
                layout = null,
                message = null
            )
        }
    }

    /** Rasterise the freehand strokes into a Bitmap and run the normal pipeline. */
    fun convertDrawing() {
        val snapshot = _state.value
        if (snapshot.strokes.isEmpty() && snapshot.currentStroke.isEmpty()) {
            _state.update { it.copy(message = "Draw something first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                val bmp = withContext(Dispatchers.Default) {
                    // Fixed working resolution – high enough for clean contours
                    val size = 1200
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = 8f          // ~2 mm-ish at this resolution
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }

                    // We don't know the exact canvas size the user drew on,
                    // so we normalise all points into [0..1] then scale to the bitmap.
                    // For simplicity we assume the drawing canvas was square and
                    // we just map the bounding box.
                    val allPoints = (snapshot.strokes.flatMap { it.points } + snapshot.currentStroke)
                    if (allPoints.isEmpty()) return@withContext null

                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = Float.MIN_VALUE
                    var maxY = Float.MIN_VALUE
                    allPoints.forEach { p ->
                        if (p.x < minX) minX = p.x
                        if (p.y < minY) minY = p.y
                        if (p.x > maxX) maxX = p.x
                        if (p.y > maxY) maxY = p.y
                    }
                    val pad = 40f
                    val w = (maxX - minX).coerceAtLeast(1f) + 2 * pad
                    val h = (maxY - minY).coerceAtLeast(1f) + 2 * pad
                    val scale = size / maxOf(w, h)

                    fun map(p: Offset): android.graphics.PointF {
                        val x = (p.x - minX + pad) * scale
                        val y = (p.y - minY + pad) * scale
                        return android.graphics.PointF(x, y)
                    }

                    fun drawStroke(points: List<Offset>) {
                        if (points.size < 2) return
                        val path = AndroidPath()
                        val first = map(points.first())
                        path.moveTo(first.x, first.y)
                        for (i in 1 until points.size) {
                            val pt = map(points[i])
                            path.lineTo(pt.x, pt.y)
                        }
                        canvas.drawPath(path, paint)
                    }

                    snapshot.strokes.forEach { drawStroke(it.points) }
                    if (snapshot.currentStroke.isNotEmpty()) drawStroke(snapshot.currentStroke)

                    bitmap
                }

                if (bmp == null) {
                    _state.update { it.copy(busy = false, message = "Nothing to convert.") }
                    return@launch
                }

                // Treat the rasterised drawing exactly like a loaded photo
                cachedBitmap = null
                cachedResult = null
                _state.update {
                    it.copy(
                        bitmap = bmp,
                        // keep the strokes so the user can still see / edit them
                        mode = EditorMode.DRAW
                    )
                }
                recompute()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = "Convert failed: ${e.message}") }
            }
        }
    }

    // ── Processing ───────────────────────────────────────────────────────────

    private fun recompute() {
        job?.cancel()
        job = viewModelScope.launch {
            delay(120) // debounce slider drags
            val snapshot = _state.value
            val bmp = snapshot.bitmap ?: return@launch
            _state.update { it.copy(busy = true) }
            try {
                val layout = withContext(Dispatchers.Default) {
                    val s = snapshot.settings
                    val opts = s.toProcessOptions()
                    val result = if (cachedBitmap === bmp && cachedOptions == opts && cachedResult != null) {
                        cachedResult!!
                    } else {
                        ImagePipeline.process(bmp, opts).also {
                            cachedBitmap = bmp
                            cachedOptions = opts
                            cachedResult = it
                        }
                    }
                    PageLayout.layoutFromContours(
                        result.contours, result.widthPx, result.heightPx,
                        s.toPageSettings(), s.toDotStyle()
                    )
                }
                _state.update { it.copy(layout = layout, busy = false, message = layout.warning) }
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(busy = false, message = "Out of memory. Try a larger dot spacing.") }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = "Processing failed: ${e.message}") }
            }
        }
    }

    fun export(format: ExportFormat) {
        val layout = _state.value.layout
        if (layout == null || layout.dots.isEmpty()) {
            _state.update { it.copy(message = "Nothing to export yet. Draw or pick an image first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                    val f = File(dir, "dotsketch.${format.extension}")
                    f.outputStream().use { out ->
                        when (format) {
                            ExportFormat.PDF -> PdfExporter.export(layout, out)
                            ExportFormat.PNG -> PngExporter.export(layout, out)
                        }
                    }
                    f
                }
                _state.update { it.copy(busy = false, share = ShareRequest(file.absolutePath, format.mime)) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = "Export failed: ${e.message}") }
            }
        }
    }

    fun shareHandled() {
        _state.update { it.copy(share = null) }
    }
}

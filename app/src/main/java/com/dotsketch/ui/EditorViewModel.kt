package com.dotsketch.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
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

data class ShareRequest(val path: String, val mime: String)

data class EditorUiState(
    val settings: UserSettings = UserSettings(),
    val bitmap: Bitmap? = null,
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

    fun update(transform: (UserSettings) -> UserSettings) {
        val newSettings = transform(_state.value.settings)
        _state.update { it.copy(settings = newSettings) }
        prefs.save(newSettings)
        recompute()
    }

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val bmp = try {
                withContext(Dispatchers.IO) { BitmapLoader.load(getApplication(), uri) }
            } catch (e: Exception) {
                null
            }
            if (bmp == null) {
                _state.update { it.copy(busy = false, message = "Couldn't open that image.") }
            } else {
                _state.update { it.copy(bitmap = bmp) }
                recompute()
            }
        }
    }

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
            _state.update { it.copy(message = "Nothing to export yet. Pick an image first.") }
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

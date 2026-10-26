package com.dotsketch.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dotsketch.core.PageLayout
import com.dotsketch.core.PageOrientation
import com.dotsketch.core.PaperSize
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val s = state.settings

    val captureFile = File(File(context.cacheDir, "captures").apply { mkdirs() }, "capture.jpg")
    val captureUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.loadImage(uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) vm.loadImage(captureUri)
    }

    LaunchedEffect(state.share) {
        val req = state.share ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(req.path))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = req.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share DotSketch"))
        vm.shareHandled()
    }

    val (pageW, pageH) = state.layout?.let { it.pageWidthMm to it.pageHeightMm }
        ?: PageLayout.pageDimensionsMm(s.toPageSettings())

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("DotSketch", style = MaterialTheme.typography.headlineSmall)

        // Mode switcher
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == EditorMode.PHOTO,
                onClick = { vm.setMode(EditorMode.PHOTO) },
                label = { Text("Photo") }
            )
            FilterChip(
                selected = state.mode == EditorMode.DRAW,
                onClick = { vm.setMode(EditorMode.DRAW) },
                label = { Text("Draw") }
            )
        }

        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        // ── Preview / Drawing surface ──────────────────────────────────────
        if (state.mode == EditorMode.DRAW && state.layout == null) {
            // Freehand drawing canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.White)
                    .border(1.dp, Color.Gray)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> vm.startStroke(offset) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    vm.addPoint(change.position)
                                },
                                onDragEnd = { vm.endStroke() },
                                onDragCancel = { vm.endStroke() }
                            )
                        }
                ) {
                    val strokeStyle = Stroke(
                        width = 6f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    // committed strokes
                    state.strokes.forEach { stroke ->
                        if (stroke.points.size >= 2) {
                            val path = Path()
                            path.moveTo(stroke.points[0].x, stroke.points[0].y)
                            for (i in 1 until stroke.points.size) {
                                path.lineTo(stroke.points[i].x, stroke.points[i].y)
                            }
                            drawPath(path, Color.Black, style = strokeStyle)
                        }
                    }
                    // current in-progress stroke
                    if (state.currentStroke.size >= 2) {
                        val path = Path()
                        path.moveTo(state.currentStroke[0].x, state.currentStroke[0].y)
                        for (i in 1 until state.currentStroke.size) {
                            path.lineTo(state.currentStroke[i].x, state.currentStroke[i].y)
                        }
                        drawPath(path, Color.Black, style = strokeStyle)
                    }
                }
            }

            // Drawing controls
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.undoStroke() }) { Text("Undo") }
                OutlinedButton(onClick = { vm.clearStrokes() }) { Text("Clear") }
                Button(
                    onClick = { vm.convertDrawing() },
                    enabled = !state.busy && (state.strokes.isNotEmpty() || state.currentStroke.size >= 2)
                ) { Text("Convert to dots") }
            }
        } else {
            // Dotted preview (photo mode or after convert)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(pageW / pageH)
                    .background(Color.White)
                    .border(1.dp, Color.Gray)
            ) {
                val k = size.width / pageW
                state.layout?.dots?.forEach { d ->
                    drawCircle(
                        color = Color.Black,
                        radius = (d.radiusMm * k).coerceAtLeast(0.6f),
                        center = Offset(d.xMm * k, d.yMm * k)
                    )
                }
            }
        }

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.layout?.let { Text("${it.dots.size} dots", style = MaterialTheme.typography.bodySmall) }

        // Photo controls (only relevant in PHOTO mode)
        if (state.mode == EditorMode.PHOTO) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickImage.launch("image/*") }) { Text("Pick image") }
                OutlinedButton(onClick = { takePhoto.launch(captureUri) }) { Text("Take photo") }
            }
        }

        // Shared processing controls
        Text("Image", style = MaterialTheme.typography.titleSmall)
        LabeledSlider("Threshold: ${s.threshold}", s.threshold.toFloat(), 1f..254f) { v ->
            vm.update { it.copy(threshold = v.roundToInt()) }
        }
        LabeledSlider("Ignore specks smaller than: ${s.minContourAreaPx.roundToInt()} px²", s.minContourAreaPx, 0f..500f) { v ->
            vm.update { it.copy(minContourAreaPx = v) }
        }
        SwitchRow("Invert (trace light areas)", s.invert) { v -> vm.update { it.copy(invert = v) } }
        SwitchRow("Smooth noise", s.denoise) { v -> vm.update { it.copy(denoise = v) } }

        Text("Dots", style = MaterialTheme.typography.titleSmall)
        LabeledSlider("Spacing: ${fmt(s.spacingMm)} mm", s.spacingMm, 1f..10f) { v ->
            vm.update { it.copy(spacingMm = v) }
        }
        LabeledSlider("Diameter: ${fmt(s.diameterMm)} mm", s.diameterMm, 0.3f..3f) { v ->
            vm.update { it.copy(diameterMm = v) }
        }

        Text("Page", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperSize.values().forEach { p ->
                FilterChip(selected = s.paperSize == p, onClick = { vm.update { it.copy(paperSize = p) } }, label = { Text(p.label) })
            }
            PageOrientation.values().forEach { o ->
                FilterChip(
                    selected = s.orientation == o,
                    onClick = { vm.update { it.copy(orientation = o) } },
                    label = { Text(if (o == PageOrientation.PORTRAIT) "Portrait" else "Landscape") }
                )
            }
        }
        LabeledSlider("Margin: ${fmt(s.marginMm)} mm", s.marginMm, 0f..40f) { v ->
            vm.update { it.copy(marginMm = v) }
        }
        LabeledSlider("Scale: ${(s.userScale * 100).roundToInt()}%", s.userScale, 0.1f..1f) { v ->
            vm.update { it.copy(userScale = v) }
        }
        SwitchRow("Fit to page", s.fitToPage) { v -> vm.update { it.copy(fitToPage = v) } }
        SwitchRow("Center on page", s.center) { v -> vm.update { it.copy(center = v) } }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.export(ExportFormat.PDF) }, enabled = !state.busy) { Text("Export PDF") }
            OutlinedButton(
                onClick = { vm.export(ExportFormat.PNG) },
                enabled = !state.busy,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) { Text("Export PNG") }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun fmt(v: Float): String = String.format(Locale.US, "%.1f", v)

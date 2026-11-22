package online.apst.h8clipstudio

import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.setPadding
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.Locale

@UnstableApi
class MainActivity : ComponentActivity() {
    private val maxBytes = 5L * 1024 * 1024 * 1024
    private var selectedUri: Uri? = null
    private var durationMs = 0L
    private var clipSeconds = 60
    private var nextStartMs = 0L
    private var totalClips = 0
    private var completedClips = 0
    private var transformer: Transformer? = null
    private var currentTemp: File? = null
    private var isRunning = false

    private lateinit var fileLabel: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var chooseButton: Button
    private lateinit var startButton: Button
    private lateinit var cancelButton: Button
    private lateinit var durationSpinner: Spinner

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            selectedUri = uri
            val size = querySize(uri)
            if (size > maxBytes) {
                selectedUri = null
                fileLabel.text = "File 5 GB limit se badi hai."
                startButton.isEnabled = false
            } else {
                val name = queryName(uri) ?: "Selected video"
                fileLabel.text = "$name  •  ${formatBytes(size)}"
                durationMs = readDuration(uri)
                status.text = if (durationMs > 0) "Video ready • ${durationMs / 1000}s" else "Video duration read nahi hui. Dusri file try karein."
                startButton.isEnabled = durationMs > 0
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24)
            setBackgroundColor(0xFF080D1D.toInt())
        }
        fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(this).apply {
                text = value; textSize = size; setTextColor(color)
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 12, 0, 12)
            }
        root.addView(text("▶  H8 Clip Studio", 26f, 0xFF62C8FF.toInt(), true))
        root.addView(text("DEVICE-ONLY VIDEO PROCESSING", 13f, 0xFFB58CFF.toInt(), true))
        root.addView(text("Movie phone se select hogi. Processing phone ke media engine par hogi — movie server par upload nahi hogi.", 16f, 0xFFE6ECFF.toInt()))
        val note = text("5 GB tak file select kar sakte hain, lekin successful export phone ke free storage, RAM, codec aur hardware encoder par depend karega. Phone ko charging par rakhein aur app khula rakhein.", 14f, 0xFFB9D1EA.toInt())
        note.setBackgroundColor(0xFF142D49.toInt()); note.setPadding(16)
        root.addView(note)
        chooseButton = Button(this).apply { text = "Choose movie"; setOnClickListener { picker.launch(arrayOf("video/*")) } }
        root.addView(chooseButton)
        fileLabel = text("No movie selected", 14f, 0xFFFFFFFF.toInt())
        root.addView(fileLabel)
        root.addView(text("Clip duration", 16f, 0xFFFFFFFF.toInt(), true))
        durationSpinner = Spinner(this)
        durationSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("60 seconds", "90 seconds", "120 seconds"))
        root.addView(durationSpinner)
        startButton = Button(this).apply {
            text = "Start local processing"; isEnabled = false
            setOnClickListener { startProcessing() }
        }
        root.addView(startButton)
        cancelButton = Button(this).apply { text = "Cancel"; isEnabled = false; setOnClickListener { cancelProcessing() } }
        root.addView(cancelButton)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        root.addView(progress)
        status = text("Ready. Output: Movies/H8 Clip Studio", 14f, 0xFFB9D1EA.toInt())
        root.addView(status)
        root.addView(text("Privacy: selected movie is read locally and is not sent to APST.online. Clips are saved in phone storage.", 13f, 0xFF8FA5C8.toInt()))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun startProcessing() {
        val uri = selectedUri ?: return
        clipSeconds = when (durationSpinner.selectedItemPosition) { 1 -> 90; 2 -> 120; else -> 60 }
        nextStartMs = 0L
        completedClips = 0
        totalClips = ((durationMs + clipSeconds * 1000L - 1) / (clipSeconds * 1000L)).toInt()
        isRunning = true
        chooseButton.isEnabled = false; startButton.isEnabled = false; cancelButton.isEnabled = true
        progress.progress = 0
        status.text = "Preparing $totalClips clips…"
        exportNext(uri)
    }

    private fun exportNext(uri: Uri) {
        if (!isRunning) return
        if (nextStartMs >= durationMs) {
            isRunning = false
            chooseButton.isEnabled = true; startButton.isEnabled = true; cancelButton.isEnabled = false
            progress.progress = 100
            status.text = "Completed: $completedClips clips saved in Movies/H8 Clip Studio"
            return
        }
        val endMs = minOf(nextStartMs + clipSeconds * 1000L, durationMs)
        val index = completedClips + 1
        val temp = File(cacheDir, "h8_clip_${System.currentTimeMillis()}_$index.mp4")
        currentTemp = temp
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(nextStartMs)
                    .setEndPositionMs(endMs)
                    .build()
            ).build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                if (!isRunning) { temp.delete(); return }
                try {
                    saveToDownloads(temp, index)
                    completedClips++
                    nextStartMs = endMs
                    val overall = ((completedClips.toFloat() / totalClips) * 100).toInt()
                    progress.progress = overall
                    status.text = "Clip $completedClips / $totalClips saved • $overall%"
                    temp.delete()
                    currentTemp = null
                    transformer = null
                    exportNext(uri)
                } catch (e: Exception) { fail("Save error: ${e.message}") }
            }

            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                fail("Clip $index export failed: ${exception.message ?: "Unsupported video/codec"}")
            }
        }
        try {
            transformer = Transformer.Builder(this).addListener(listener).build()
            transformer!!.start(mediaItem, temp.absolutePath)
            status.text = "Processing clip $index / $totalClips…"
            pollProgress()
        } catch (e: Exception) {
            fail("Start error: ${e.message}")
        }
    }

    private fun pollProgress() {
        val active = transformer ?: return
        val holder = ProgressHolder()
        val handler = android.os.Handler(mainLooper)
        val tick = object : Runnable {
            override fun run() {
                if (!isRunning || transformer !== active) return
                try {
                    if (active.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val within = holder.progress.coerceIn(0, 100)
                        val overall = (((completedClips + within / 100f) / totalClips) * 100).toInt()
                        progress.progress = overall.coerceIn(0, 99)
                    }
                    handler.postDelayed(this, 700)
                } catch (_: Exception) { }
            }
        }
        handler.post(tick)
    }

    private fun saveToDownloads(file: File, index: Int) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "H8_Clip_%03d.mp4".format(Locale.US, index))
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/H8 Clip Studio")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val outputUri = contentResolver.insert(collection, values) ?: error("Phone storage unavailable")
        try {
            contentResolver.openOutputStream(outputUri)?.use { out ->
                FileInputStream(file).use { input -> input.copyTo(out, 1024 * 1024) }
            } ?: error("Cannot open output file")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            contentResolver.update(outputUri, done, null, null)
        } catch (e: Exception) {
            contentResolver.delete(outputUri, null, null)
            throw e
        }
    }

    private fun cancelProcessing() {
        isRunning = false
        try { transformer?.cancel() } catch (_: Exception) { }
        currentTemp?.delete()
        transformer = null
        chooseButton.isEnabled = true; startButton.isEnabled = selectedUri != null; cancelButton.isEnabled = false
        status.text = "Processing cancelled. Already saved clips remain in Movies/H8 Clip Studio."
    }

    private fun fail(message: String) {
        isRunning = false
        currentTemp?.delete()
        transformer = null
        chooseButton.isEnabled = true; startButton.isEnabled = selectedUri != null; cancelButton.isEnabled = false
        status.text = message
    }

    private fun readDuration(uri: Uri): Long = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, uri)
        val result = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
        result
    } catch (_: Exception) { 0L }

    private fun querySize(uri: Uri): Long = try {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        } ?: 0L
    } catch (_: Exception) { 0L }

    private fun queryName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) { null }

    private fun formatBytes(bytes: Long): String = if (bytes <= 0) "Size unknown" else "%.2f GB".format(Locale.US, bytes / (1024.0 * 1024 * 1024))

    override fun onDestroy() {
        if (isRunning) cancelProcessing()
        super.onDestroy()
    }
}

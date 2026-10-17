package com.aetherengineers.onarg.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.aetherengineers.onarg.audio.AacEncoder
import com.aetherengineers.onarg.audio.AudioBuffer
import com.aetherengineers.onarg.audio.Wav
import org.json.JSONObject
import java.io.File

data class Recording(
    val id: String,
    val title: String,
    val text: String,
    val effect: String,
    val createdAt: Long,
    val durationSeconds: Double,
    val file: File,
)

/** Private on-device library of rendered narrations (lossless WAV masters + small JSON sidecars). */
class Library(private val context: Context) {
    private val dir = File(context.filesDir, "library").apply { mkdirs() }

    fun list(): List<Recording> = dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { meta ->
        runCatching {
            val o = JSONObject(meta.readText())
            val wav = File(dir, meta.nameWithoutExtension + ".wav")
            if (!wav.exists()) return@runCatching null
            Recording(meta.nameWithoutExtension, o.getString("title"), o.optString("text"), o.optString("effect"),
                o.getLong("createdAt"), o.optDouble("duration"), wav)
        }.getOrNull()
    }.sortedByDescending { it.createdAt }

    fun save(buffer: AudioBuffer, title: String, text: String, effect: String): Recording {
        val now = System.currentTimeMillis()
        val id = "rec_$now"
        val wav = File(dir, "$id.wav")
        Wav.write(buffer, wav)
        File(dir, "$id.json").writeText(
            JSONObject().put("title", title).put("text", text).put("effect", effect)
                .put("createdAt", now).put("duration", buffer.durationSeconds).toString()
        )
        return Recording(id, title, text, effect, now, buffer.durationSeconds, wav)
    }

    fun rename(rec: Recording, title: String) {
        val meta = File(dir, rec.id + ".json")
        val o = JSONObject(meta.readText()).put("title", title)
        meta.writeText(o.toString())
    }

    fun delete(rec: Recording) {
        File(dir, rec.id + ".json").delete()
        rec.file.delete()
    }

    /** Encodes [rec] into the share cache in [format] and returns the file. */
    fun exportFile(rec: Recording, format: ExportFormat): File {
        val out = File(context.cacheDir, "share").apply { mkdirs() }
        out.listFiles()?.forEach { it.delete() }
        val file = File(out, safeName(rec.title) + "." + format.extension)
        when (format) {
            ExportFormat.WAV -> rec.file.copyTo(file, overwrite = true)
            ExportFormat.M4A -> AacEncoder.encode(Wav.read(rec.file), file)
        }
        return file
    }

    fun shareUri(file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)

    /** Copies an exported file into the public Music/ONARG folder. Returns the visible path. */
    fun saveToDevice(file: File, format: ExportFormat): String {
        val folder = Environment.DIRECTORY_MUSIC + "/ONARG"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.MIME_TYPE, format.mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, folder)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create file")
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "ONARG").apply { mkdirs() }
            file.copyTo(File(target, file.name), overwrite = true)
        }
        return "$folder/${file.name}"
    }

    companion object {
        fun safeName(title: String) = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(48).ifEmpty { "narration" }
    }
}

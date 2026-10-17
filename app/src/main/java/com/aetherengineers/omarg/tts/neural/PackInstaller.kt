package com.aetherengineers.omarg.tts.neural

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InterruptedIOException

/** Unpacks sherpa-onnx voice archives (.tar.bz2, .tar.gz or .tar) into the app's private voices folder. */
class PackInstaller(private val voicesDir: File) {

    init { voicesDir.mkdirs() }

    fun installed(): List<VoicePack> = voicesDir.listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.startsWith(".") }
        .mapNotNull { runCatching { VoicePack.detect(it) }.getOrNull() }
        .sortedBy { it.displayName }

    /**
     * Extracts [input] and returns the detected pack. [onProgress] receives compressed bytes read so far.
     * [isCancelled] is polled between reads; on cancel or failure nothing is left behind.
     */
    fun install(input: InputStream, onProgress: (Long) -> Unit = {}, isCancelled: () -> Boolean = { false }): VoicePack {
        val staging = File(voicesDir, ".staging-${System.nanoTime()}").apply { mkdirs() }
        try {
            val counting = CountingStream(BufferedInputStream(input, 1 shl 16), onProgress, isCancelled)
            counting.mark(4)
            val magic = ByteArray(3).also { counting.read(it) }
            counting.reset()
            val decompressed: InputStream = when {
                magic[0] == 'B'.code.toByte() && magic[1] == 'Z'.code.toByte() && magic[2] == 'h'.code.toByte() ->
                    BZip2CompressorInputStream(counting, true)
                magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte() -> GzipCompressorInputStream(counting, true)
                else -> counting
            }
            val root = staging.canonicalFile
            TarArchiveInputStream(BufferedInputStream(decompressed, 1 shl 16)).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val target = File(staging, entry.name).canonicalFile
                    require(target.path.startsWith(root.path + File.separator) || target == root) { "Unsafe path in archive: ${entry.name}" }
                    when {
                        entry.isDirectory -> target.mkdirs()
                        entry.isSymbolicLink || entry.isLink -> continue
                        else -> {
                            target.parentFile?.mkdirs()
                            target.outputStream().buffered(1 shl 16).use { tar.copyTo(it, 1 shl 16) }
                        }
                    }
                }
            }
            // Archives contain one top-level folder named after the model.
            val top = staging.listFiles().orEmpty()
            val packRoot = if (top.size == 1 && top[0].isDirectory) top[0] else staging
            val name = if (packRoot == staging) "voice-pack-${System.currentTimeMillis()}" else packRoot.name
            VoicePack.detect(packRoot) // validate before moving into place
            val dest = File(voicesDir, name)
            if (dest.exists()) dest.deleteRecursively()
            check(packRoot.renameTo(dest)) { "Could not move the voice pack into place" }
            return VoicePack.detect(dest)
        } finally {
            staging.deleteRecursively()
        }
    }

    fun delete(pack: VoicePack) { pack.dir.deleteRecursively() }

    fun sizeOnDisk(pack: VoicePack): Long = pack.dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private class CountingStream(input: InputStream, val onProgress: (Long) -> Unit, val isCancelled: () -> Boolean) : FilterInputStream(input) {
        private var count = 0L
        private var marked = 0L
        private var lastReport = 0L

        private fun advance(n: Long) {
            if (isCancelled()) throw InterruptedIOException("Cancelled")
            if (n > 0) count += n
            if (count - lastReport > 256 * 1024) { lastReport = count; onProgress(count) }
        }

        override fun read(): Int = super.read().also { if (it >= 0) advance(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { advance(it.toLong()) }
        override fun skip(n: Long): Long = super.skip(n).also { advance(it) }
        override fun mark(readlimit: Int) { super.mark(readlimit); marked = count }
        override fun reset() { super.reset(); count = marked }
    }
}

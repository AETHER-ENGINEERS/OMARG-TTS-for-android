package com.aetherengineers.onarg.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal RIFF/WAVE reader and writer (PCM 8/16/24/32-bit and 32-bit float, any channel count). */
object Wav {

    fun read(file: File): AudioBuffer = file.inputStream().buffered().use { read(it) }

    fun read(input: InputStream): AudioBuffer {
        val bytes = input.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Not a WAV file" }

        var format = 1
        var channels = 1
        var sampleRate = 22050
        var bits = 16
        var dataOffset = -1
        var dataSize = 0
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            var size = bb.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    format = bb.getShort(body).toInt() and 0xFFFF
                    channels = bb.getShort(body + 2).toInt().coerceAtLeast(1)
                    sampleRate = bb.getInt(body + 4)
                    bits = bb.getShort(body + 14).toInt()
                    if (format == 0xFFFE && size >= 26) format = bb.getShort(body + 24).toInt() and 0xFFFF
                }
                "data" -> {
                    // Some engines stream WAV with a placeholder (0 or -1) data size.
                    if (size <= 0 || body + size > bytes.size) size = bytes.size - body
                    dataOffset = body
                    dataSize = size
                }
            }
            if (dataOffset >= 0) break
            pos = body + size + (size and 1)
        }
        if (dataOffset < 0) return AudioBuffer(FloatArray(0), sampleRate)

        val bytesPerSample = bits / 8
        val frameSize = bytesPerSample * channels
        val frames = dataSize / frameSize
        val out = FloatArray(frames)
        for (f in 0 until frames) {
            var acc = 0f
            for (c in 0 until channels) {
                val p = dataOffset + f * frameSize + c * bytesPerSample
                acc += when {
                    format == 3 && bits == 32 -> bb.getFloat(p)
                    bits == 8 -> ((bytes[p].toInt() and 0xFF) - 128) / 128f
                    bits == 16 -> bb.getShort(p) / 32768f
                    bits == 24 -> ((bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8) or
                        (bytes[p + 2].toInt() shl 16)) / 8388608f
                    bits == 32 -> bb.getInt(p) / 2147483648f
                    else -> 0f
                }
            }
            out[f] = acc / channels
        }
        return AudioBuffer(out, sampleRate)
    }

    fun write(buffer: AudioBuffer, file: File) = file.outputStream().buffered().use { write(buffer, it) }

    fun write(buffer: AudioBuffer, out: OutputStream) {
        val pcm = toPcm16(buffer.samples)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(buffer.sampleRate)
            putInt(buffer.sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }
        out.write(header.array())
        out.write(pcm)
    }

    fun toBytes(buffer: AudioBuffer): ByteArray = ByteArrayOutputStream().also { write(buffer, it) }.toByteArray()

    fun toPcm16(samples: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) bb.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        return bb.array()
    }
}

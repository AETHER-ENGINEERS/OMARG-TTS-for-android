package com.aetherengineers.omarg.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Encodes mono audio to an AAC-LC .m4a file using the device's hardware/software encoder. */
object AacEncoder {

    fun encode(buffer: AudioBuffer, file: File, bitRate: Int = 128_000) {
        // AAC encoders reliably support these rates; pick the closest at or above the source.
        val supported = intArrayOf(16000, 22050, 24000, 32000, 44100, 48000)
        val rate = supported.firstOrNull { it >= buffer.sampleRate } ?: 48000
        val samples = if (rate == buffer.sampleRate) buffer.samples else Resampler.convert(buffer.samples, buffer.sampleRate, rate)

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        file.delete()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()
        var pos = 0
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val frames = minOf(inBuf.capacity() / 2, samples.size - pos, 4096)
                        val pts = pos * 1_000_000L / rate
                        if (frames <= 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val bb = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until frames) bb.putShort((samples[pos + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                            inBuf.put(bb.array())
                            codec.queueInputBuffer(inIdx, 0, frames * 2, pts, 0)
                            pos += frames
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, outBuf, info)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
    }
}

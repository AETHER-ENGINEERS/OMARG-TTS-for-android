package com.aetherengineers.omarg.tts.neural

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.Locale

class VoicePackTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun archive(root: String, files: List<String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(bytes)).use { tar ->
            for (f in files) {
                val data = "x".toByteArray()
                val e = TarArchiveEntry("$root/$f").apply { size = data.size.toLong() }
                tar.putArchiveEntry(e); tar.write(data); tar.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test fun installsPiperPack() {
        val installer = PackInstaller(tmp.newFolder("voices"))
        val pack = installer.install(archive("vits-piper-en_US-amy-medium",
            listOf("en_US-amy-medium.onnx", "en_US-amy-medium.onnx.json", "tokens.txt", "espeak-ng-data/phontab")).inputStream())
        assertEquals(PackKind.VITS, pack.kind)
        assertEquals("en_US-amy-medium.onnx", pack.model)
        assertEquals("espeak-ng-data", pack.dataDir)
        assertEquals(Locale.US, pack.voiceList(1).single().locale)
        assertEquals("Amy (medium)", pack.voiceList(1).single().label)
        assertEquals(listOf("vits-piper-en_US-amy-medium"), installer.installed().map { it.id })
    }

    @Test fun detectsKokoroMultiLang() {
        val installer = PackInstaller(tmp.newFolder("voices"))
        val pack = installer.install(archive("kokoro-multi-lang-v1_0", listOf("model.onnx", "voices.bin", "tokens.txt",
            "lexicon-us-en.txt", "lexicon-gb-en.txt", "lexicon-zh.txt", "date-zh.fst", "phone-zh.fst", "number-zh.fst",
            "espeak-ng-data/phontab")).inputStream())
        assertEquals(PackKind.KOKORO, pack.kind)
        assertEquals(listOf("lexicon-us-en.txt", "lexicon-zh.txt"), pack.lexicons)
        assertEquals(listOf("phone-zh.fst", "date-zh.fst", "number-zh.fst"), pack.ruleFsts)
        val voices = pack.voiceList(54)
        assertEquals("Heart (female)", voices[3].label)
        assertEquals("fr", voices.first { it.label.startsWith("Siwis") }.lang)
        assertEquals("pt-br", voices.first { it.id.endsWith("#42") }.lang)
    }

    @Test fun detectsKittenAndSupertonic() {
        val installer = PackInstaller(tmp.newFolder("voices"))
        val kitten = installer.install(archive("kitten-nano-en-v0_8-int8",
            listOf("model.int8.onnx", "voices.bin", "tokens.txt", "espeak-ng-data/phontab")).inputStream())
        assertEquals(PackKind.KITTEN, kitten.kind)
        assertEquals("Voice 2 (female)", kitten.voiceList(8)[1].label)
        val st = installer.install(archive("sherpa-onnx-supertonic-3-tts-int8-2026-05-11", listOf("duration_predictor.int8.onnx",
            "text_encoder.int8.onnx", "vector_estimator.int8.onnx", "vocoder.int8.onnx", "tts.json", "unicode_indexer.bin", "voice.bin")).inputStream())
        assertEquals(PackKind.SUPERTONIC, st.kind)
        assertEquals(31 * 2, st.voiceList(2).size)
        assertEquals(2, installer.installed().size)
    }

    @Test fun rejectsUnsafeAndUnknownArchives() {
        val voices = tmp.newFolder("voices")
        val installer = PackInstaller(voices)
        assertTrue(runCatching { installer.install(archive("junk", listOf("readme.md")).inputStream()) }.isFailure)
        assertTrue(runCatching { installer.install(archive("..", listOf("evil.onnx", "tokens.txt")).inputStream()) }.isFailure)
        assertFalse(voices.listFiles()!!.any { it.name.startsWith(".staging") })
        assertTrue(installer.installed().isEmpty())
    }
}

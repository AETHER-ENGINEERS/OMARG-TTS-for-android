package com.aetherengineers.onarg.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptTest {
    @Test fun parsesPausesAndVoices() {
        val parts = Script.parse("Hello there. [pause 1.5] [2] Who goes? [pause 300ms][1] Me.")
        assertEquals(
            listOf(
                ScriptPart.Speech("Hello there.", 0),
                ScriptPart.Pause(1.5),
                ScriptPart.Speech("Who goes?", 1),
                ScriptPart.Pause(0.3),
                ScriptPart.Speech("Me.", 0),
            ),
            parts,
        )
    }

    @Test fun chunksLongTextAtSentenceBoundaries() {
        val sentence = "This is a fairly ordinary sentence for testing. "
        val chunks = Script.chunk(sentence.repeat(100), 500)
        assertTrue(chunks.size > 5)
        assertTrue(chunks.all { it.length <= 500 && it.endsWith(".") })
        assertEquals(sentence.repeat(100).trim().replace(" ", ""), chunks.joinToString("").replace(" ", ""))
    }

    @Test fun plainStripsTags() {
        assertEquals("Hi there", Script.plain("[1] Hi [pause 2s] there"))
    }
}

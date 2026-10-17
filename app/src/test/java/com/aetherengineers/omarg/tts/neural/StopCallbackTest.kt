package com.aetherengineers.omarg.tts.neural

import org.junit.Assert.assertEquals
import org.junit.Test

class StopCallbackTest {
    /** sherpa-onnx's JNI looks up exactly `invoke([F)Ljava/lang/Integer;` — anything else aborts the app. */
    @Test fun exposesTheSignatureNativeCodeExpects() {
        val method = StopCallback::class.java.getMethod("invoke", FloatArray::class.java)
        assertEquals(Integer::class.java, method.returnType)
        var stop = false
        val cb = StopCallback { stop }
        assertEquals(1, method.invoke(cb, FloatArray(0)))
        stop = true
        assertEquals(0, method.invoke(cb, FloatArray(0)))
    }
}

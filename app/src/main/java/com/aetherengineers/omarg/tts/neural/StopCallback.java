package com.aetherengineers.omarg.tts.neural;

import java.util.function.BooleanSupplier;

import kotlin.jvm.functions.Function1;

/**
 * Progress callback for sherpa-onnx generation, written in Java on purpose: the native code looks up exactly
 * {@code Integer invoke(float[])}, which Kotlin 2 lambdas (compiled via invokedynamic) don't provide.
 * Returning 0 tells the engine to stop, 1 to continue.
 */
public final class StopCallback implements Function1<float[], Integer> {
    private final BooleanSupplier shouldStop;

    public StopCallback(BooleanSupplier shouldStop) {
        this.shouldStop = shouldStop;
    }

    @Override
    public Integer invoke(float[] samples) {
        return shouldStop.getAsBoolean() ? 0 : 1;
    }
}

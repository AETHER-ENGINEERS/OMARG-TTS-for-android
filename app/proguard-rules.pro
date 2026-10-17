# sherpa-onnx's native code reads and constructs these classes by name through JNI.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# Called from native code by exact name and signature.
-keep class com.aetherengineers.omarg.tts.neural.StopCallback { *; }

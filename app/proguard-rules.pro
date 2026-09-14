# Keep JNI entry points used by whisper_jni.cpp
-keepclasseswithmembers class ai.z.livescript.stt.WhisperTranscriber {
    native <methods>;
}

# Vosk / JNA
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }

# MediaPipe tasks-genai
-keep class com.google.mediapipe.** { *; }

# Room
-keep class ai.z.livescript.data.** { *; }

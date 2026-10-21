# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ---------------------------------------------------------------------------
# 1. KYAWGYI AI — CANONICAL PIPELINE, ROOM ENTITIES & MOSHI SERIALIZATION
# ---------------------------------------------------------------------------
-keep class com.kyawgyi.pipeline.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.audit.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ---------------------------------------------------------------------------
# 2. JNI / NATIVE AUDIO & OFFLINE SPEECH ENGINES (SHERPA-ONNX / VOSK)
# ---------------------------------------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class org.vosk.** { *; }

# ---------------------------------------------------------------------------
# 3. RETROFIT, OKHTTP & GEMINI REST PAYLOADS
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

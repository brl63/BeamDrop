# BeamDrop Production ProGuard & R8 Optimization Rules

# Preserve line numbers for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve BeamDrop Data Models and Swarm Frames
-keep class com.example.model.** { *; }
-keep class com.example.network.swarm.SwarmProtocol$SwarmFrame** { *; }
-keep class com.example.network.swarm.TopologyState { *; }
-keep class com.example.service.TransferForegroundService { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# Moshi JSON serialization
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# ZXing QR Code
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep native methods and reflection annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

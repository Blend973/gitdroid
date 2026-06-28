# =============================================================================
# Gitdroid — ProGuard / R8 Rules
# =============================================================================
# AGP 9.1.1 + R8 full mode. These rules prevent removal of code accessed
# via reflection, code gen, or Proxy-based libraries.
# =============================================================================

# ---------------------------------------------------------------------------
# Moshi — @JsonClass code-gen adapters
# ---------------------------------------------------------------------------
# Moshi generates adapters (e.g. GitHubUserJsonAdapter) at compile time via
# KSP. R8 cannot statically trace these—they're loaded by name at runtime.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass *;
}
# Keep the generated adapter classes themselves
-keep class **JsonAdapter { *; }

# ---------------------------------------------------------------------------
# Retrofit — Proxy-based service interfaces
# ---------------------------------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retrofit interfaces are instantiated via java.lang.reflect.Proxy at runtime.
# Without this, R8 strips them thinking they have no concrete implementations.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Retrofit models that appear as generic type arguments need their footprint
# kept so the converter (Moshi) can see them.
-keep,allowobfuscation,allowshrinking class com.example.data.api.*

# ---------------------------------------------------------------------------
# Room — Database, DAOs, Entities
# ---------------------------------------------------------------------------
# Room uses compile-time code gen (KSP). The generated implementations
# (_Impl classes) extend the abstract DAO/Database classes.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Kotlin Coroutines — dispatcher internals
# ---------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ---------------------------------------------------------------------------
# Coil — image loader (reflective cache config)
# ---------------------------------------------------------------------------
-keep class coil.** { *; }

# ---------------------------------------------------------------------------
# OkHttp — platform-specific TLS engines
# ---------------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# General — entry point + keep annotations for debugging
# ---------------------------------------------------------------------------
-keep class com.example.MainActivity { *; }

# Keep useful annotations for crash reporting
-keepattributes *Annotation*

# ---------------------------------------------------------------------------
# Compose Foundation — R8 internal NPE on ContentInViewNode.launchAnimation()
# Seen with AGP 9.1.1 + Compose BOM 2024.09.00. R8's API-level database
# returns null when processing this class; keep prevents R8 from visiting it.
# ---------------------------------------------------------------------------
-keep class androidx.compose.foundation.gestures.ContentInViewNode { *; }

# Keep source file name + line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable

# ═══════════════════════════════════════════════════════
# BedCloud VPN - ProGuard / R8 Rules
# Maximum obfuscation + anti-reverse-engineering
# ═══════════════════════════════════════════════════════

# ── AGGRESSIVE OBFUSCATION ─────────────────────────────
-optimizationpasses 5
-dontpreverify
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-repackageclasses 'o'
-flattenpackagehierarchy 'o'

# Remove all logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Remove debug info
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ── KEEP RULES (required for functionality) ────────────

# Keep required annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonQualifier interface *
-keepnames @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-dontwarn org.conscrypt.**

# Keep API data models (needed for JSON serialization)
-keep class org.bdcloud.clash.api.** { *; }

# VPN Service (referenced by manifest)
-keep class org.bdcloud.clash.core.BdCloudVpnService { *; }

# JNI native helper (method names must match C function names)
-keep class org.bdcloud.clash.core.NativeHelper { *; }

# Security classes (integrity checks rely on reflection)
-keep class org.bdcloud.clash.util.SecurityChecker { *; }
-keep class org.bdcloud.clash.util.IntegrityChecker { *; }

# Activities referenced in manifest
-keep class org.bdcloud.clash.ui.BlockedActivity { *; }
-keep class org.bdcloud.clash.ui.login.LoginActivity { *; }
-keep class org.bdcloud.clash.ui.main.MainActivity { *; }

# Application class
-keep class org.bdcloud.clash.BdCloudApp { *; }

# ── ANTI-REVERSE ENGINEERING ───────────────────────────

# Remove kotlin metadata (makes decompilation harder)
-dontwarn kotlin.**
-dontwarn kotlinx.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
}

# Encrypt string constants (R8 full mode)
-dontwarn java.lang.invoke.StringConcatFactory

# Remove unused code aggressively
-dontwarn javax.**
-dontwarn sun.**

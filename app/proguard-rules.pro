# FILE PATH: app/proguard-rules.pro
#
# Firestore deserializes documents via reflection using exact field names
# that match Kotlin data class properties. Without these rules, R8 renames
# or strips these classes and the app crashes with:
#   "no-argument constructor not found" or silently returns null objects.

-keep class com.vaulto.data.model.** { *; }

-keepclassmembers class com.vaulto.data.model.** {
    public <init>();
}

# ✅ FIX: Keep the SpaceType enum. R8 strips enum entries it thinks are unused,
#    but Firestore stores spaceType as a String ("FAMILY" / "PERSONAL") and
#    reads it back via Enum.valueOf(). Without this rule the app crashes at
#    runtime with an IllegalArgumentException on documents that contain
#    spaceType="FAMILY" or spaceType="PERSONAL".
-keepclassmembers enum com.vaulto.data.model.SpaceType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Firebase ─────────────────────────────────────────────────────────────────
# Firebase ships consumer proguard rules via AAR — no extra rules needed here.

# ── Credential Manager / Google ID ───────────────────────────────────────────
-keep class com.google.android.libraries.identity.googleid.** { *; }

# ── Kotlin Coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Compose ──────────────────────────────────────────────────────────────────
# Compose ships its own rules — no extras needed.

# ── General ──────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-dontwarn kotlin.**
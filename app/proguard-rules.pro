# ── Vaulto ProGuard Rules ────────────────────────────────────────────────────
#
# Firebase / Firestore data model classes must NOT be renamed or stripped.
# Firestore deserializes documents via reflection using the exact field names
# that match the Kotlin data class properties. Without these rules the app
# will crash with "no-argument constructor not found" or return null objects.

-keep class com.vaulto.data.model.** { *; }

# Firestore uses a no-arg constructor + setter reflection pattern.
# Keep all classes that Firestore can deserialize.
-keepclassmembers class com.vaulto.data.model.** {
    public <init>();
}

# ── Firebase ─────────────────────────────────────────────────────────────────
# Firebase ships its own consumer proguard rules via AAR — no extra rules needed.

# ── Credential Manager / Google ID ───────────────────────────────────────────
# These libraries also ship consumer rules, but keep the credential bundle class
# to be safe.
-keep class com.google.android.libraries.identity.googleid.** { *; }

# ── Kotlin Coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Compose ──────────────────────────────────────────────────────────────────
# Compose ships its own rules. No extra rules needed.

# ── General Android rules ────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-dontwarn kotlin.**
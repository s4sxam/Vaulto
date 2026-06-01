// FILE PATH: app/src/main/java/com/vaulto/auth/AuthRepository.kt

package com.vaulto.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.vaulto.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

class AuthRepository(context: Context) {

    // Keep applicationContext for non-UI Firestore/Auth operations only.
    // Never pass appContext to CredentialManager — it requires an Activity.
    private val appContext: Context = context.applicationContext

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    // ─── SIGN IN ────────────────────────────────────────────────────────────

    /**
     * Signs the user in with Google via the Credential Manager API.
     *
     * FIX 1 — Activity context:
     *   CredentialManager.create() must receive the ACTIVITY, not applicationContext.
     *   The bottom-sheet account picker is an Activity overlay; passing appContext
     *   causes a WindowManager BadTokenException or a silent NoCredentialException
     *   on many OEM ROMs (Xiaomi, OPPO, Samsung), which maps to "Sign-in failed."
     *
     * FIX 2 — Two-step flow:
     *   Step 1 tries filterByAuthorizedAccounts=true (fastest, no UI if already
     *   consented). Step 2 falls back to filterByAuthorizedAccounts=false (shows
     *   full account picker). This matches Google's recommended pattern and avoids
     *   a NoCredentialException on first-time sign-in or after clearing app data.
     *
     * FIX 3 — Nonce:
     *   A SHA-256 nonce is required by Google Identity Services when the app targets
     *   API 26+ and uses the Credential Manager path. Without it some Google
     *   accounts (especially those with Advanced Protection) silently reject the
     *   token request, producing a NoCredentialException that surfaces as
     *   "Sign-in failed."
     */
    suspend fun signInWithGoogle(activity: Activity, webClientId: String): Result<FirebaseUser> {
        return try {
            // ✅ FIX 1: Pass activity, NOT appContext.
            val credentialManager = CredentialManager.create(activity)

            // ✅ FIX 3: Generate a fresh SHA-256 nonce per sign-in attempt.
            val rawNonce    = UUID.randomUUID().toString()
            val hashedNonce = sha256(rawNonce)

            // ✅ FIX 2a: Step 1 — try previously authorized accounts (silent / fast path).
            val idToken: String? = try {
                val authorizedOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(webClientId)
                    .setNonce(hashedNonce)
                    .build()

                val authorizedRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(authorizedOption)
                    .build()

                val result = credentialManager.getCredential(activity, authorizedRequest)
                GoogleIdTokenCredential.createFrom(result.credential.data).idToken

            } catch (e: NoCredentialException) {
                // No previously authorized account — fall through to Step 2.
                null
            } catch (e: GetCredentialCancellationException) {
                // User explicitly cancelled the picker.
                return Result.failure(e)
            }

            // ✅ FIX 2b: Step 2 — full account picker (new user or new device).
            val finalToken: String = idToken ?: run {
                val allAccountsOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setNonce(hashedNonce)
                    .build()

                val allAccountsRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(allAccountsOption)
                    .build()

                val result = credentialManager.getCredential(activity, allAccountsRequest)
                GoogleIdTokenCredential.createFrom(result.credential.data).idToken
            }

            // Exchange the Google ID token for a Firebase credential.
            val firebaseCredential = GoogleAuthProvider.getCredential(finalToken, null)
            val authResult         = auth.signInWithCredential(firebaseCredential).await()
            val user               = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase user was null after sign-in"))

            // ✅ MERGE: Only mutable display fields refreshed; familyId, emoji,
            //    and createdAt are preserved on every subsequent sign-in.
            val profileUpdates = mapOf(
                "uid"      to user.uid,
                "name"     to (user.displayName ?: "Family Member"),
                "email"    to (user.email ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: "")
            )
            db.collection("users").document(user.uid)
                .set(profileUpdates, SetOptions.merge())
                .await()

            // First-ever sign-in: initialise emoji + createdAt only if absent.
            val snap = db.collection("users").document(user.uid).get().await()
            if (snap.getString("emoji").isNullOrBlank()) {
                db.collection("users").document(user.uid)
                    .update(
                        mapOf(
                            "emoji"     to pickEmoji(user.displayName),
                            "createdAt" to System.currentTimeMillis()
                        )
                    ).await()
            }

            Result.success(user)

        } catch (e: GetCredentialCancellationException) {
            // User tapped "Back" — not an error, not a crash.
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── PROFILE ────────────────────────────────────────────────────────────

    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            db.collection("users")
                .document(uid)
                .get()
                .await()
                .toObject(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUserProfile(profile: UserProfile) {
        db.collection("users").document(profile.uid)
            .set(profile, SetOptions.merge())
            .await()
    }

    // ─── SIGN OUT ───────────────────────────────────────────────────────────

    fun signOut() {
        auth.signOut()
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────

    private fun pickEmoji(name: String?): String {
        val emojis = listOf("👨", "👩", "👦", "👧", "👴", "👵", "🧑", "🧒")
        return emojis[(name?.length ?: 0) % emojis.size]
    }

    /**
     * Returns the hex-encoded SHA-256 digest of the input string.
     * Used to hash the nonce before sending to Google Identity Services.
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

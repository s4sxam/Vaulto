// FILE PATH: app/src/main/java/com/vaulto/auth/AuthRepository.kt

package com.vaulto.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
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

class AuthRepository(context: Context) {

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

    suspend fun signInWithGoogle(activity: Activity, webClientId: String): Result<FirebaseUser> {
        return try {
            val credentialManager = CredentialManager.create(appContext)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activity, request)

            val googleIdToken = GoogleIdTokenCredential
                .createFrom(result.credential.data)
                .idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult         = auth.signInWithCredential(firebaseCredential).await()
            val user               = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase user was null after sign-in"))

            // ✅ CRITICAL FIX: Use SetOptions.merge() so that pre-existing fields
            //    (most importantly `familyId`) are NEVER overwritten on subsequent
            //    sign-ins. Without merge(), every login reset familyId to "" in
            //    Firestore, silently breaking the family-sharing feature entirely.
            //
            //    Only mutable display fields (name, email, photoUrl) are refreshed;
            //    familyId, emoji, and createdAt are left untouched if they exist.
            val profileUpdates = mapOf(
                "uid"      to user.uid,
                "name"     to (user.displayName ?: "Family Member"),
                "email"    to (user.email ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: "")
                // ↑ familyId, emoji, createdAt intentionally omitted — merge() keeps existing values
            )
            db.collection("users").document(user.uid)
                .set(profileUpdates, SetOptions.merge())
                .await()

            // If this is the very first sign-in (new user), initialise the fields
            // that merge() won't touch if the document doesn't exist yet.
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
        // Use merge so partial updates never clobber fields we didn't touch.
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
}

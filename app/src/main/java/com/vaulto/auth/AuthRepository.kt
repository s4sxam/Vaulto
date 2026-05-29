package com.vaulto.auth

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
import com.vaulto.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(context: Context) {

    // ⚠️  Always hold applicationContext, never an Activity context.
    //     Storing a raw Activity context here would leak the entire Activity
    //     for the lifetime of this repository (which lives in the ViewModel).
    private val appContext: Context = context.applicationContext

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    init {
        // Keep _currentUser in sync with Firebase's own auth state so that
        // sign-in from another device / token expiry is reflected immediately.
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    // ─── SIGN IN ────────────────────────────────────────────────────────────

    suspend fun signInWithGoogle(webClientId: String): Result<FirebaseUser> {
        return try {
            val credentialManager = CredentialManager.create(appContext)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)   // show all Google accounts
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // This suspends until the user picks an account or cancels.
            val result = credentialManager.getCredential(appContext, request)

            val googleIdToken = GoogleIdTokenCredential
                .createFrom(result.credential.data)
                .idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult         = auth.signInWithCredential(firebaseCredential).await()
            val user               = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase user was null after sign-in"))

            // Upsert the user's profile in Firestore on every sign-in so that
            // display name / photo URL changes are always kept fresh.
            val profile = UserProfile(
                uid      = user.uid,
                name     = user.displayName ?: "Family Member",
                email    = user.email       ?: "",
                photoUrl = user.photoUrl?.toString() ?: "",
                emoji    = pickEmoji(user.displayName)
            )
            db.collection("users").document(user.uid).set(profile).await()

            Result.success(user)

        } catch (e: GetCredentialException) {
            // User cancelled, no accounts available, etc.
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
        db.collection("users").document(profile.uid).set(profile).await()
    }

    // ─── SIGN OUT ───────────────────────────────────────────────────────────

    fun signOut() {
        auth.signOut()
        // _currentUser will be updated automatically by the AuthStateListener above.
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────

    private fun pickEmoji(name: String?): String {
        val emojis = listOf("👨", "👩", "👦", "👧", "👴", "👵", "🧑", "🧒")
        return emojis[(name?.length ?: 0) % emojis.size]
    }
}

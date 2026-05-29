package com.vaulto.auth

import android.content.Context
import androidx.credentials.*
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

class AuthRepository(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    init {
        auth.addAuthStateListener { _currentUser.value = it.currentUser }
    }

    suspend fun signInWithGoogle(webClientId: String): Result<FirebaseUser> {
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(context, request)
            val googleIdToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user!!

            // Save/update profile in Firestore
            val profile = UserProfile(
                uid = user.uid,
                name = user.displayName ?: "Family Member",
                email = user.email ?: "",
                photoUrl = user.photoUrl?.toString() ?: "",
                emoji = pickEmoji(user.displayName)
            )
            db.collection("users").document(user.uid).set(profile).await()

            Result.success(user)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
        } catch (e: Exception) { null }
    }

    suspend fun updateUserProfile(profile: UserProfile) {
        db.collection("users").document(profile.uid).set(profile).await()
    }

    fun signOut() { auth.signOut() }

    private fun pickEmoji(name: String?): String {
        val emojis = listOf("👨","👩","👦","👧","👴","👵","🧑","🧒")
        return emojis[(name?.length ?: 0) % emojis.size]
    }
}

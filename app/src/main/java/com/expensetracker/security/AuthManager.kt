package com.expensetracker.security

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth
) {
    private companion object {
        const val TAG = "ExpenseAuth"
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val webClientId = getWebClientId()
        android.util.Log.d(TAG, "Building GoogleSignInClient with webClientId=$webClientId")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webClientId)
            .requestProfile()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val currentUser: FirebaseUser? get() = auth.currentUser

    val isSignedIn: Boolean get() = auth.currentUser != null

    val userEmail: String? get() = auth.currentUser?.email

    val userName: String? get() = auth.currentUser?.displayName

    val userPhotoUrl: String? get() = auth.currentUser?.photoUrl?.toString()

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    suspend fun firebaseAuthWithGoogle(idToken: String): Boolean {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val current = auth.currentUser
            if (current != null && current.isAnonymous) {
                // The user backed up data under an anonymous UID before signing in.
                // Link (upgrade) that anon account to Google so the SAME UID — and all
                // its Firestore data — carries over, instead of stranding it under the
                // old anon UID and starting fresh under a new Google UID.
                try {
                    current.linkWithCredential(credential).await()
                    return true
                } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                    // This Google account already has its own Firebase user (e.g. from a
                    // previous install). We can't merge two UIDs client-side, so fall
                    // through and sign into the existing Google account. The anon data
                    // stays reachable only if it was already synced there; the user's
                    // real history lives under the Google account, which is the correct
                    // one to keep signed in.
                    android.util.Log.w(TAG, "Anon link collided; signing into existing Google account", e)
                }
            }
            auth.signInWithCredential(credential).await()
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Firebase credential exchange failed", e)
            false
        }
    }

    suspend fun signInAnonymously(): Boolean {
        return try {
            auth.signInAnonymously().await()
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Anonymous sign-in failed", e)
            false
        }
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
        googleSignInClient.revokeAccess()
    }

    fun clearCachedSignIn() {
        googleSignInClient.signOut()
    }

    private fun getWebClientId(): String {
        // client_type 3 (Web OAuth client) from google-services.json — shared across both
        // package entries. Hardcoded because resource lookup fails when namespace differs
        // from applicationId.
        return "670083029799-t1i03glk05mc0f19cjqepq351hrrgn0v.apps.googleusercontent.com"
    }
}

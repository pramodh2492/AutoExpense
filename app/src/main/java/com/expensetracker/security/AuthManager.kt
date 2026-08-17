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
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
        if (webClientId.isNotBlank()) {
            builder.requestIdToken(webClientId)
        }
        GoogleSignIn.getClient(context, builder.build())
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
    }

    private fun getWebClientId(): String {
        // R class is generated under the namespace (com.expensetracker), not the applicationId
        // (com.lazysloth.autoexpense), so getIdentifier with packageName fails. Use the
        // namespace directly.
        val resources = context.resources
        var id = resources.getIdentifier("default_web_client_id", "string", "com.expensetracker")
        if (id == 0) {
            id = resources.getIdentifier("default_web_client_id", "string", context.packageName)
        }
        return if (id != 0) resources.getString(id) else ""
    }
}

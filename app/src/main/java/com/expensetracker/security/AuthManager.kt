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
            auth.signInWithCredential(credential).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun signInAnonymously(): Boolean {
        return try {
            auth.signInAnonymously().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
    }

    private fun getWebClientId(): String {
        // This comes from google-services.json → oauth_client → client_type: 3
        val resources = context.resources
        val id = resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id != 0) resources.getString(id) else ""
    }
}

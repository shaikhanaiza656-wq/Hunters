package com.globalmmorpg.game.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

/**
 * Real authentication repository backed by Firebase Auth.
 * No mock/dummy logic - every path calls the actual Google, Facebook or Firebase SDKs.
 */
class AuthRepository(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val facebookCallbackManager: CallbackManager = CallbackManager.Factory.create()
) {

    fun getCallbackManager(): CallbackManager = facebookCallbackManager

    /** Guest login using Firebase Anonymous Auth (real, no fake local-only account). */
    suspend fun signInAsGuest(): AuthResult {
        return try {
            val result = firebaseAuth.signInAnonymously().await()
            val user = result.user ?: return AuthResult.Failure("No user returned")
            // Fire-and-forget: this is background bookkeeping for the 30-day
            // inactive-guest cleanup job, not something the login screen should
            // ever block on. It still runs for real against Firestore.
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    GuestAccountManager(firebaseAuth).touchLastActive(user.uid)
                } catch (_: Exception) {
                    // Non-fatal: activity tracking failing must never affect login.
                }
            }
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Guest sign-in failed")
        }
    }

    /** Google login using the current androidx.credentials Credential Manager API. */
    suspend fun signInWithGoogle(webClientId: String): AuthResult {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(context)
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(
                    googleIdTokenCredential.idToken, null
                )
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user ?: return AuthResult.Failure("No user returned")
                AuthResult.Success(user)
            } else {
                AuthResult.Failure("Unexpected credential type")
            }
        } catch (e: GoogleIdTokenParsingException) {
            AuthResult.Failure("Invalid Google ID token: ${e.message}")
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Google sign-in failed")
        }
    }

    /** Facebook login using the official Facebook Login SDK, bridged into Firebase Auth. */
    fun signInWithFacebook(
        activity: androidx.activity.ComponentActivity,
        onResult: (AuthResult) -> Unit
    ) {
        LoginManager.getInstance().registerCallback(
            facebookCallbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    firebaseAuthWithFacebook(result.accessToken, onResult)
                }

                override fun onCancel() {
                    onResult(AuthResult.Failure("Facebook login cancelled"))
                }

                override fun onError(error: FacebookException) {
                    onResult(AuthResult.Failure(error.message ?: "Facebook login error"))
                }
            }
        )
        LoginManager.getInstance().logInWithReadPermissions(activity, listOf("email", "public_profile"))
    }

    private fun firebaseAuthWithFacebook(token: AccessToken, onResult: (AuthResult) -> Unit) {
        val credential = FacebookAuthProvider.getCredential(token.token)
        firebaseAuth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) onResult(AuthResult.Success(user))
                else onResult(AuthResult.Failure("No user returned"))
            }
            .addOnFailureListener { e ->
                onResult(AuthResult.Failure(e.message ?: "Firebase Facebook auth failed"))
            }
    }

    fun currentUser(): FirebaseUser? = firebaseAuth.currentUser

    fun signOut() {
        firebaseAuth.signOut()
        LoginManager.getInstance().logOut()
    }
}

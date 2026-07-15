package com.enigma.tv.data.firebase

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleSignInHelper {
    
    // Replace with your Web Client ID from Firebase
    const val WEB_CLIENT_ID = "176690386204-ag60eekolrog9qtk8mpb5h7vaf276ihv.apps.googleusercontent.com"

    suspend fun getGoogleIdToken(context: Context): Result<String> = withContext(Dispatchers.Main) {
        try {
            val credentialManager = CredentialManager.create(context)
            
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is androidx.credentials.CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleIdTokenCredential.idToken)
            } else {
                Result.failure(Exception("Unexpected credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Sign in cancelled"))
        } catch (e: androidx.credentials.exceptions.NoCredentialException) {
            Result.failure(Exception("No Google accounts found on this device. Please add a Google account in your Android settings first."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Google Sign-In failed. Please try again."))
        }
    }
}

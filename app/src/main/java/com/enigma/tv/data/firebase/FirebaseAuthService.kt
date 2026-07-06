package com.enigma.tv.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthService {
    private val auth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await().user!!
    }

    suspend fun signUp(email: String, password: String, displayName: String): Result<FirebaseUser> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
        if (displayName.isNotBlank()) {
            result.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build()).await()
        }
        result
    }

    suspend fun signInGuest(): Result<FirebaseUser> = runCatching {
        auth.signInAnonymously().await().user!!
    }

    fun signOut() = auth.signOut()
}

package com.kptm.SmartQRA

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth = Firebase.auth) {

    private val db = FirebaseFirestore.getInstance()

    // role = portal yang user pilih ("student" / "admin"). Hanya untuk akaun BARU.
    // Akaun sedia ada kekalkan role asal. Tiada kod admin diperlukan.
    suspend fun signInWithGoogle(
        activityContext: Context,
        role: String = "student"
    ): Result<FirebaseUser> = runCatching {
        val firebaseCredential = getGoogleFirebaseCredential(activityContext)
        val user = auth.signInWithCredential(firebaseCredential).await().user!!
        ensureUserProfile(user, role)
        user
    }

    // Sahkan semula identiti (contoh: padam akaun). TIDAK cipta akaun atau profil baru.
    suspend fun reauthenticateWithGoogle(
        activityContext: Context,
        user: FirebaseUser
    ): Result<Unit> = runCatching {
        user.reauthenticate(getGoogleFirebaseCredential(activityContext)).await()
        Unit
    }

    private suspend fun getGoogleFirebaseCredential(activityContext: Context): AuthCredential {
        val option = GetSignInWithGoogleOption.Builder(
            activityContext.getString(R.string.default_web_client_id)
        ).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val credential = CredentialManager.create(activityContext)
            .getCredential(activityContext, request).credential

        require(
            credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Invalid credential" }

        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }

    private suspend fun ensureUserProfile(user: FirebaseUser, role: String) {
        val docRef = db.collection("users").document(user.uid)
        if (docRef.get().await().exists()) return // akaun lama: jangan sentuh role

        val safeRole = if (role == "admin") "admin" else "student"
        val prefix = if (safeRole == "admin") "ADM-" else "USR-"
        val newUser = hashMapOf(
            "uid" to user.uid,
            "userId" to prefix + System.currentTimeMillis().toString().takeLast(5),
            "name" to (user.displayName ?: user.email ?: "Google User"),
            "email" to (user.email ?: ""),
            "role" to safeRole,
            "createdAt" to System.currentTimeMillis()
        )
        docRef.set(newUser).await()
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await().user!!
    }

    suspend fun register(email: String, password: String): Result<FirebaseUser> = runCatching {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user!!
        user.sendEmailVerification().await()
        user
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }
}
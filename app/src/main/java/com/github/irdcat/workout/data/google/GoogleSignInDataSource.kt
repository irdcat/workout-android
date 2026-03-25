package com.github.irdcat.workout.data.google

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.tasks.await

class GoogleSignInDataSource(context: Context) {

    private val googleSignInClient = run {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(googleSignInClient.applicationContext)?.run {
            if (requestedScopes.contains(Scope(SheetsScopes.SPREADSHEETS))) {
                this
            } else {
                null
            }
        }

    fun getSignInIntent() = googleSignInClient.signInIntent

    suspend fun handleSignResult(data: Intent): GoogleSignInAccount =
        GoogleSignIn.getSignedInAccountFromIntent(data).await()!!
}
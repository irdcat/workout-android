package com.github.irdcat.workout.infrastructure.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.github.irdcat.workout.domain.auth.AuthRepository
import com.github.irdcat.workout.domain.auth.AuthorizationResult
import com.github.irdcat.workout.domain.auth.SignInResult
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import com.google.android.gms.auth.api.identity.AuthorizationResult as GmsAuthorizationResult

private const val GOOGLE_ACCOUNT_TYPE = "com.google"

private val SheetsAccessScopes = GoogleApiScopes.map(::Scope)

@Singleton
class GoogleAuthRepository @Inject constructor(
    private val authClient: AuthorizationClient,
    private val credentialManager: CredentialManager,
    private val credential: GoogleAccountCredential,
    private val accountDataStore: AccountDataStore
) : AuthRepository {

    override val signedInEmail: Flow<String?> = accountDataStore.emailFlow()

    init {
        // Keep the Sheets client pointed at the stored account, including after a restart.
        signedInEmail
            .onEach { email -> credential.selectedAccount = email?.let(::googleAccount) }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    override suspend fun getSignedInEmail(): String? = signedInEmail.first()

    override suspend fun signIn(activityContext: Context, serverClientId: String): SignInResult =
        try {
            val result = getCredential(activityContext, serverClientId)
            if (result is CustomCredential &&
                result.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                bindAccount(result.data)?.let(SignInResult::SignedIn)
                    ?: SignInResult.Failed(IllegalStateException("The Google account has no email address"))
            } else {
                SignInResult.Failed(IllegalStateException("Unexpected credential type: ${result.type}"))
            }
        } catch (_: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (_: NoCredentialException) {
            SignInResult.NoAccount
        } catch (e: GetCredentialException) {
            SignInResult.Failed(e)
        } catch (e: GoogleIdTokenParsingException) {
            SignInResult.Failed(e)
        }

    override suspend fun authorize(): AuthorizationResult =
        try {
            val email = getSignedInEmail()
            val authorizationRequest = AuthorizationRequest.Builder()
                .setRequestedScopes(SheetsAccessScopes)
                .apply { email?.let { setAccount(googleAccount(it)) } }
                .build()
            authClient.authorize(authorizationRequest).await().toDomain()
        } catch (e: ApiException) {
            AuthorizationResult.Failed(e)
        }

    override fun completeAuthorization(data: Intent?): AuthorizationResult {
        if (data == null) return AuthorizationResult.Denied
        return try {
            authClient.getAuthorizationResultFromIntent(data).toDomain()
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) {
                AuthorizationResult.Denied
            } else {
                AuthorizationResult.Failed(e)
            }
        }
    }

    override suspend fun signOut(revokeAccess: Boolean) {
        val email = getSignedInEmail()
        if (revokeAccess && email != null) {
            val request = RevokeAccessRequest.builder()
                .setAccount(googleAccount(email))
                .setScopes(SheetsAccessScopes)
                .build()
            runCatching { authClient.revokeAccess(request).await() }
        }
        // Forget the account locally even if the provider call fails.
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        credential.selectedAccount = null
        accountDataStore.setEmail(null)
    }

    private suspend fun getCredential(context: Context, serverClientId: String): Credential {
        val googleIdOption = GetSignInWithGoogleOption
            .Builder(serverClientId)
            .setNonce(generateSecureRandomNonce())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val response = credentialManager.getCredential(context, request)
        return response.credential
    }

    /** Stores the picked account and returns its email; null when the account has none. */
    private suspend fun bindAccount(data: Bundle): String? {
        val googleIdToken = GoogleIdTokenCredential.createFrom(data)
        val email = googleIdToken.email ?: return null
        credential.selectedAccount = googleAccount(email)
        accountDataStore.setEmail(email)
        return email
    }

    private fun GmsAuthorizationResult.toDomain(): AuthorizationResult {
        val consent = pendingIntent
        return if (hasResolution() && consent != null) {
            AuthorizationResult.ConsentRequired(consent)
        } else {
            AuthorizationResult.Granted
        }
    }

    private fun googleAccount(email: String) = Account(email, GOOGLE_ACCOUNT_TYPE)

    private fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

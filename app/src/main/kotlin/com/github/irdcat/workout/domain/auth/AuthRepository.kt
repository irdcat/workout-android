package com.github.irdcat.workout.domain.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.Flow

/**
 * Google sign-in and Sheets authorization.
 *
 * The flow is two steps:
 * 1. [signIn] picks a Google account (Credential Manager bottom sheet).
 * 2. [authorize] asks for Sheets access for that account. The first time,
 *    this returns [AuthorizationResult.ConsentRequired]; launch its
 *    [AuthorizationResult.ConsentRequired.pendingIntent] and hand the
 *    result to [completeAuthorization].
 */
interface AuthRepository {

    /** Email of the signed-in account, or null when signed out. Survives app restarts. */
    val signedInEmail: Flow<String?>

    /** Current value of [signedInEmail]. */
    suspend fun getSignedInEmail(): String?

    /**
     * Shows the account picker. [activityContext] must be an Activity context,
     * since the picker is UI. [serverClientId] is the OAuth web client ID.
     */
    suspend fun signIn(activityContext: Context, serverClientId: String): SignInResult

    /** Requests Sheets access for the signed-in account. */
    suspend fun authorize(): AuthorizationResult

    /**
     * Finishes the consent screen started from [AuthorizationResult.ConsentRequired].
     * Pass the returned data, or null when the user backed out.
     */
    fun completeAuthorization(data: Intent?): AuthorizationResult

    /**
     * Forgets the signed-in account. With [revokeAccess] the Sheets grant is
     * also revoked, so the next sign-in shows the consent screen again.
     */
    suspend fun signOut(revokeAccess: Boolean = false)
}

sealed interface SignInResult {
    data class SignedIn(val email: String) : SignInResult

    /** The user closed the account picker. */
    data object Cancelled : SignInResult

    /** No Google account on the device. */
    data object NoAccount : SignInResult

    data class Failed(val cause: Throwable) : SignInResult
}

sealed interface AuthorizationResult {
    data object Granted : AuthorizationResult

    /** The user has to approve access; launch [pendingIntent] for a result. */
    data class ConsentRequired(val pendingIntent: PendingIntent) : AuthorizationResult

    /** The user declined or closed the consent screen. */
    data object Denied : AuthorizationResult

    data class Failed(val cause: Throwable) : AuthorizationResult
}

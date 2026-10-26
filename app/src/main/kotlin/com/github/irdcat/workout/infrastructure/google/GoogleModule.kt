package com.github.irdcat.workout.infrastructure.google

import android.content.Context
import androidx.credentials.CredentialManager
import com.github.irdcat.workout.R
import com.github.irdcat.workout.domain.auth.AuthRepository
import com.github.irdcat.workout.domain.results.ResultRepository
import com.github.irdcat.workout.domain.workout.WorkoutPlanRepository
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.Identity
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/** Qualifier for the OAuth web client ID passed to Sign in with Google. */
const val GOOGLE_SERVER_CLIENT_ID = "googleServerClientId"

/** OAuth scopes the app needs; used for both authorization and the Sheets client. */
internal val GoogleApiScopes = listOf(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE)

/** Everything Google: sign-in, authorization and the Sheets-backed plan. */
@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleModule {

    @Binds
    abstract fun bindAuthRepository(impl: GoogleAuthRepository): AuthRepository

    @Binds
    abstract fun bindWorkoutPlanRepository(impl: SheetsWorkoutPlanRepository): WorkoutPlanRepository

    @Binds
    abstract fun bindResultRepository(impl: SheetsResultRepository): ResultRepository

    companion object {

        @Provides
        @Singleton
        fun provideAuthorizationClient(
            @ApplicationContext context: Context,
        ): AuthorizationClient = Identity.getAuthorizationClient(context)

        @Provides
        @Singleton
        fun provideCredentialManager(
            @ApplicationContext context: Context,
        ): CredentialManager = CredentialManager.create(context)

        @Provides
        @Named(GOOGLE_SERVER_CLIENT_ID)
        fun provideServerClientId(
            @ApplicationContext context: Context,
        ): String = context.getString(R.string.google_server_client_id)

        @Provides
        @Singleton
        fun provideGoogleAccountCredential(
            @ApplicationContext context: Context,
        ): GoogleAccountCredential = GoogleAccountCredential.usingOAuth2(context, GoogleApiScopes)

        @Provides
        @Singleton
        fun provideSheetsService(
            credential: GoogleAccountCredential,
        ): Sheets = Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Workout")
            .build()
    }
}

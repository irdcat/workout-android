package com.github.irdcat.workout.infrastructure.google

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccountDataStoreTest {

    private val store = AccountDataStore(ApplicationProvider.getApplicationContext())

    // The DataStore is a process-wide singleton, so start every test signed out.
    @Before
    fun reset() = runTest { store.setEmail(null) }

    @Test
    fun signedOutAtFirst() = runTest {
        assertNull(store.emailFlow().first())
    }

    @Test
    fun remembersTheAccountUntilSignedOut() = runTest {
        store.setEmail("athlete@example.com")
        assertEquals("athlete@example.com", AccountDataStore(ApplicationProvider.getApplicationContext()).emailFlow().first())

        store.setEmail(null)
        assertNull(store.emailFlow().first())
    }
}

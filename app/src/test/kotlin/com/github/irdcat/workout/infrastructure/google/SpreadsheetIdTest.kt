package com.github.irdcat.workout.infrastructure.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpreadsheetIdTest {

    private val id = "1AbCdEfGhIjKlMnOpQrStUvWxYz_0123456789-AbCdE"

    @Test
    fun readsTheIdFromLinks() {
        assertEquals(id, spreadsheetIdOf("https://docs.google.com/spreadsheets/d/$id/edit#gid=0"))
        assertEquals(id, spreadsheetIdOf("docs.google.com/spreadsheets/d/$id/edit"))
        assertEquals(id, spreadsheetIdOf("  https://docs.google.com/spreadsheets/d/$id  "))
        assertEquals(id, spreadsheetIdOf(id))
    }

    @Test
    fun rejectsTextWithoutAnId() {
        assertNull(spreadsheetIdOf(""))
        assertNull(spreadsheetIdOf("docs.google.com/spreadsheets"))
        assertNull(spreadsheetIdOf("not a link"))
    }
}

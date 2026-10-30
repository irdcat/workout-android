package com.github.irdcat.workout.application.ui.screens

import com.github.irdcat.workout.domain.strength.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoresViewModelTest {

    private val viewModel = ScoresViewModel()
    private val state get() = viewModel.state.value

    @Test
    fun scoresShowOnceBothNumbersAreEntered() {
        viewModel.onEvent(ScoresEvent.BodyweightChanged("90"))
        assertNull(state.dots)

        viewModel.onEvent(ScoresEvent.TotalChanged("700"))
        assertEquals("452.62", state.dots)
        assertEquals("446.88", state.wilks)
        assertEquals("536.90", state.wilks2020)

        viewModel.onEvent(ScoresEvent.TotalChanged(""))
        assertNull(state.dots)
        assertNull(state.wilks)
        assertNull(state.wilks2020)
    }

    @Test
    fun theSexChangesTheFormula() {
        viewModel.onEvent(ScoresEvent.BodyweightChanged("60"))
        viewModel.onEvent(ScoresEvent.TotalChanged("400"))
        viewModel.onEvent(ScoresEvent.SexSelected(Sex.Female))

        assertEquals("443.42", state.dots)
        assertEquals("445.95", state.wilks)
        assertEquals("527.61", state.wilks2020)
    }

    @Test
    fun steppersAndTyping() {
        viewModel.onEvent(ScoresEvent.BodyweightChanged("82,4kg"))
        assertEquals("82.4", state.bodyweight)
        viewModel.onEvent(ScoresEvent.AdjustBodyweight(+1))
        assertEquals("82.9", state.bodyweight)

        viewModel.onEvent(ScoresEvent.AdjustTotal(+1))
        assertEquals("2.5", state.total)
        viewModel.onEvent(ScoresEvent.AdjustTotal(-1))
        viewModel.onEvent(ScoresEvent.AdjustTotal(-1))
        assertEquals("0", state.total)
    }
}

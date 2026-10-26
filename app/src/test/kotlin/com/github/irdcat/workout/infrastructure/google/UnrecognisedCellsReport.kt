package com.github.irdcat.workout.infrastructure.google

import com.github.irdcat.workout.domain.workout.Load
import com.github.irdcat.workout.domain.workout.Reps
import com.github.irdcat.workout.domain.workout.SetCount
import com.github.irdcat.workout.domain.workout.SheetNotation
import org.junit.Test

/** Prints every cell the notation parser falls back on, to spot formats it misses. */
class UnrecognisedCellsReport {

    @Test
    fun report() {
        val groups = SheetFixtures.parsed().flatMap { (_, content) -> content.setGroups }
        val unknown = buildList {
            groups.forEach { g ->
                if (SheetNotation.sets(g.sets) is SetCount.Unknown) add("sets  '${g.sets}' (${g.id})")
                if (SheetNotation.reps(g.reps) is Reps.Other) add("reps  '${g.reps}' (${g.id})")
                if (SheetNotation.load(g.load) is Load.Other) add("load  '${g.load}' (${g.id})")
                if (g.tempo !in setOf("", "-") && SheetNotation.tempo(g.tempo) == null) add("tempo '${g.tempo}' (${g.id})")
                if (g.rest !in setOf("", "-") && SheetNotation.restSeconds(g.rest) == null) add("rest  '${g.rest}' (${g.id})")
            }
        }
        println("UNRECOGNISED ${unknown.size} of ${groups.size} set groups")
        unknown.forEach { println("  $it") }
    }
}

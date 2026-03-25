package com.github.irdcat.workout.domain.model

import kotlin.streams.toList

data class Tempo(
    val eccentric: Int,
    val stretched: Int,
    val concentric: Int,
    val tense: Int
) {
    companion object {
        fun fromString(input: String): Tempo {
            if (input.isBlank()) {
                return Tempo(0, 0,0, 0)
            }
            if (input.length != 4) {
                return Tempo(0, 0,0,0)
            }
            val values = input.chars().map {
                if (it in 48..57) { it - 48 } else { 0 }
            }.toList()
            return Tempo(
                values[0],
                values[1],
                values[2],
                values[3]
            )
        }
    }

    override fun toString() = "${eccentric}${stretched}${concentric}${tense}"

    fun isNotApplicable(): Boolean {
        return eccentric == 0
                && stretched == 0
                && concentric == 0
                && tense == 0
    }
}
package com.github.irdcat.workout.domain.model

sealed class Reps {

    companion object {
        fun exact(value: Int) = Exact(value)
        fun range(min: Int, max: Int) = Range(min, max)
        fun timed(seconds: Int) = Timed(seconds)
        fun timedRange(min: Int, max: Int) = TimedRange(min, max)
        fun perSide(v: Int) = PerSide(v)

        fun fromString(string: String): Reps {
            return if (string.endsWith("/str")) {
                string.removeSuffix("/str").trim().toInt().let { perSide(it) }
            } else if (string.endsWith("sek")) {
                if(string.contains("-")) {
                    val result = string.removeSuffix("sek").split("-").map { it.trim() }.map { it.toInt() }
                    timedRange(result[0], result[1])
                } else {
                    string.removeSuffix("sek").toInt().let { timed(it) }
                }
            } else if (string.contains("-")) {
                val result = string.split("-").map { it.trim() }.map { it.toInt() }
                range(result[0], result[1])
            } else {
                string.toIntOrNull()
                    ?.let { exact(it) }
                    ?: throw IllegalArgumentException("Unknown reps format: '$string'")
            }
        }
    }

    data class Exact(val value: Int) : Reps() {
        override fun toString() = "$value"
    }

    data class Range(val min: Int, val max: Int): Reps() {
        override fun toString() = "$min-$max"
    }

    data class Timed(val seconds: Int): Reps() {
        override fun toString() = "${seconds}s"
    }

    data class TimedRange(val minSec: Int, val maxSec: Int): Reps() {
        override fun toString() = "$minSec-$maxSec sek"
    }

    data class PerSide(val side: Int): Reps() {
        override fun toString() = "$side/str"
    }
}
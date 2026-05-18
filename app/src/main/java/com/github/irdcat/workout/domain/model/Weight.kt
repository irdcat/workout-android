package com.github.irdcat.workout.domain.model

sealed class Weight {

    fun isNone() = this is None

    companion object {
        fun none() = None
        fun rpe(v: Double) = Rpe(v)
        fun exact(v: Double) = Exact(v)
        fun doubled(v: Double) = Doubled(v)
        fun range(min: Double, max: Double) = Range(min, max)
        fun multiChoice(choices: List<Double>) = MultiChoice(choices)

        fun fromString(string: String): Weight {
            return if (string.isBlank() or (string.trim() == "-")) {
                none()
            } else if (string.startsWith("RPE")) {
                string.removePrefix("RPE").trim().toDouble().let { rpe(it) }
            } else if (string.contains('-') && string.endsWith("kg")) {
                string.split('-')
                    .map { it.removeSuffix("kg") }
                    .map { it.toDouble() }
                    .let { range(it[0], it[1]) }
            } else if (string.endsWith("kg")) {
                val result = string.removeSuffix("kg").trim()
                if (result.contains("x")) {
                    doubled(result.split("x")[1].toDouble())
                } else {
                    exact(result.toDouble())
                }
            } else if (string.toDoubleOrNull() != null) {
                if (string.contains("x")) {
                    doubled(string.split("x")[1].toDouble())
                } else {
                    exact(string.toDouble())
                }
            } else if (string.contains('/')) {
                string.split('/')
                    .map { it.toDouble() }
                    .let { multiChoice(it) }
            } else {
                throw IllegalArgumentException("Unrecognized weight format: '$string'")
            }
        }
    }

    object None : Weight() {
        override fun toString() = "-"
    }

    data class Exact(val kg: Double) : Weight() {
        override fun toString() = "$kg kg"
    }

    data class Rpe(val rpe: Double) : Weight() {
        override fun toString() = "RPE $rpe"
    }

    data class Doubled(val single: Double) : Weight() {
        override fun toString() = "2x${single} kg"
    }

    data class Range(val min: Double, val max: Double): Weight() {
        override fun toString() = "${min}kg - ${max}kg"
    }

    data class MultiChoice(val choices: List<Double>) : Weight() {
        override fun toString() = choices.joinToString("/")
    }
}
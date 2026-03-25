package com.github.irdcat.workout.domain.model

sealed class Weight {

    fun isNone() = this is None

    companion object {
        fun none() = None
        fun rpe(v: Double) = Rpe(v)
        fun exact(v: Double) = Exact(v)
        fun doubled(v: Double) = Doubled(v)

        fun fromString(string: String): Weight {
            return if (string.isBlank() or (string.trim() == "-")) {
                none()
            } else if (string.startsWith("RPE")) {
                string.removePrefix("RPE").trim().toDouble().let { rpe(it) }
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
}
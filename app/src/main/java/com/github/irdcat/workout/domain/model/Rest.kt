package com.github.irdcat.workout.domain.model

sealed class Rest {
    companion object {
        fun none() = None
        fun seconds(v: Int) = Seconds(v)

        fun fromString(string: String): Rest {
            return if (string.endsWith("sek")) {
                string.removeSuffix("sek").trim().toInt().let { seconds(it) }
            } else {
                none()
            }
        }
    }

    fun isNone(): Boolean = this is None

    object None : Rest() {
        override fun toString() = ""
    }

    data class Seconds(val seconds: Int) : Rest() {
        override fun toString() = "$seconds sek"
    }
}

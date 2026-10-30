package com.github.irdcat.workout.domain.strength

/** Which formula's coefficients apply: DOTS and Wilks both have one set per sex. */
enum class Sex { Male, Female }

/**
 * Powerlifting scores that compare totals across bodyweights: the total times
 * 500 (600 for Wilks 2020) / a polynomial of bodyweight. Bodyweights outside each
 * formula's range use its ends.
 */
object StrengthScores {

    private const val SCALE = 500.0
    private const val WILKS_2020_SCALE = 600.0

    private val DotsMale = doubleArrayOf(-307.75076, 24.0900756, -0.1918759221, 0.0007391293, -0.000001093)
    private val DotsFemale = doubleArrayOf(-57.96288, 13.6175032, -0.1126655495, 0.0005158568, -0.0000010706)

    /** The original (1994) Wilks coefficients, not the 2020 revision. */
    private val WilksMale = doubleArrayOf(-216.0475144, 16.2606339, -0.002388645, -0.00113732, 7.01863e-06, -1.291e-08)
    private val WilksFemale = doubleArrayOf(
        594.31747775582, -27.23842536447, 0.82112226871, -0.00930733913, 4.731582e-05, -9.054e-08,
    )

    private val Wilks2020Male = doubleArrayOf(
        47.4617885411949, 8.47206137941125, 0.073694103462609, -0.00139583381094385, 7.07665973070743e-06,
        -1.20804336482315e-08,
    )
    private val Wilks2020Female = doubleArrayOf(
        -125.425539779509, 13.7121941940668, -0.0330725063103405, -0.0010504000506583, 9.38773881462799e-06,
        -2.3334613884954e-08,
    )

    fun dots(sex: Sex, bodyweightKg: Double, totalKg: Double): Double {
        val bodyweight = when (sex) {
            Sex.Male -> bodyweightKg.coerceIn(40.0, 210.0)
            Sex.Female -> bodyweightKg.coerceIn(40.0, 150.0)
        }
        return totalKg * SCALE / polynomial(if (sex == Sex.Male) DotsMale else DotsFemale, bodyweight)
    }

    fun wilks(sex: Sex, bodyweightKg: Double, totalKg: Double): Double {
        val bodyweight = when (sex) {
            Sex.Male -> bodyweightKg.coerceIn(40.0, 201.9)
            Sex.Female -> bodyweightKg.coerceIn(26.51, 154.53)
        }
        return totalKg * SCALE / polynomial(if (sex == Sex.Male) WilksMale else WilksFemale, bodyweight)
    }

    /** The 2020 revision: new coefficients on a larger scale, so its scores run about a fifth higher. */
    fun wilks2020(sex: Sex, bodyweightKg: Double, totalKg: Double): Double {
        val bodyweight = when (sex) {
            Sex.Male -> bodyweightKg.coerceIn(40.0, 200.95)
            Sex.Female -> bodyweightKg.coerceIn(40.0, 150.95)
        }
        return totalKg * WILKS_2020_SCALE / polynomial(if (sex == Sex.Male) Wilks2020Male else Wilks2020Female, bodyweight)
    }

    private fun polynomial(coefficients: DoubleArray, x: Double): Double =
        coefficients.foldRight(0.0) { coefficient, sum -> sum * x + coefficient }
}

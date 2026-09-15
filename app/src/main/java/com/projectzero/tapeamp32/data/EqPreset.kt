package com.projectzero.tapeamp32.data

/**
 * Skema preset Poweramp Equalizer
 */
data class EqBand(
    val type: Int,
    val channels: Int = 0,
    val frequency: Double,
    val q: Double,
    val gain: Double,
    val color: Int = 0
)

data class EqPreset(
    val name: String,
    val preamp: Double = 0.0,
    val parametric: Boolean = false,
    val bands: List<EqBand>
)

val DefaultTenBandFrequencies = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

fun flatTenBandPreset(): EqPreset = EqPreset(
    name = "Flat",
    preamp = 0.0,
    parametric = false,
    bands = DefaultTenBandFrequencies.mapIndexed { index, freq ->
        val type = when (index) {
            0 -> 0
            DefaultTenBandFrequencies.lastIndex -> 1
            else -> 2
        }
        EqBand(type = type, frequency = freq, q = 1.0, gain = 0.0)
    }
)

val BuiltInPresets: List<EqPreset> = listOf(
    flatTenBandPreset(),
    EqPreset("Rock", 0.0, false, createBands(listOf(4.0, 3.0, 2.0, 0.5, -1.0, -1.0, 0.5, 2.0, 3.0, 3.5))),
    EqPreset("Pop", 0.0, false, createBands(listOf(-1.0, 1.5, 3.0, 3.5, 2.0, 0.0, -1.0, -1.5, -1.0, -1.0))),
    EqPreset("Jazz", 0.0, false, createBands(listOf(2.5, 1.5, 0.5, 1.0, -1.0, -1.0, 0.0, 1.0, 2.0, 3.0))),
    EqPreset("Bass Boost", -3.0, false, createBands(listOf(6.0, 5.5, 4.5, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))),
    EqPreset("Vocal", 0.0, false, createBands(listOf(-2.0, -1.5, -1.0, 1.5, 3.5, 3.5, 2.0, 1.0, 0.0, -1.0)))
)

private fun createBands(gains: List<Double>): List<EqBand> {
    return DefaultTenBandFrequencies.mapIndexed { i, f ->
        val type = when (i) {
            0 -> 0
            DefaultTenBandFrequencies.lastIndex -> 1
            else -> 2
        }
        EqBand(type = type, frequency = f, q = 1.0, gain = gains[i])
    }
}

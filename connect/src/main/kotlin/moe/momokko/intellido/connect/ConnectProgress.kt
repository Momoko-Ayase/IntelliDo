package moe.momokko.intellido.connect

/**
 * Connect tool-window contract. Official status and lower-level estimates stay distinct.
 */
sealed class ConnectProgress {
    data class Official(val trustLevel: Int, val summary: String) : ConnectProgress()

    data class Estimate(
        val trustLevel: Int,
        val source: String,
        val missingMetrics: List<String>,
    ) : ConnectProgress()

    data object Unavailable : ConnectProgress()
}

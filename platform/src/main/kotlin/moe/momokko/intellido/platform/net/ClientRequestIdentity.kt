package moe.momokko.intellido.platform.net

/**
 * Identity attached to IntelliDo-controlled LINUX DO API requests.
 * Never includes install IDs, account IDs, or telemetry tokens.
 */
data class ClientRequestIdentity(
    val productName: String,
    val version: String,
    val projectUrl: String,
) {
    fun userAgent(): String = "$productName/$version (+$projectUrl)"
}

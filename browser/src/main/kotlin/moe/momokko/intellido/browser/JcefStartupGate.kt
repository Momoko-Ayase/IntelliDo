package moe.momokko.intellido.browser

fun interface JcefRuntimeProbe {
    fun probe(): JcefProbeResult
}

sealed class JcefProbeResult {
    data object Available : JcefProbeResult()
    data class Unavailable(val diagnostics: JcefDiagnostics) : JcefProbeResult()
}

sealed class JcefIsolationResult {
    data object Isolated : JcefIsolationResult()
    data class Failed(val diagnostics: JcefDiagnostics) : JcefIsolationResult()
}

sealed class JcefStartupDecision {
    data object ContinueWithJcef : JcefStartupDecision()
    data class ShowRecovery(val diagnostics: JcefDiagnostics) : JcefStartupDecision()
}

object JcefRecoveryActions {
    const val RETRY: String = "intellido.jcef.retry"
    const val OPEN_REPAIR_GUIDE: String = "intellido.jcef.openRepairGuide"
    const val COPY_DIAGNOSTICS: String = "intellido.jcef.copyDiagnostics"
    const val EXIT: String = "intellido.exit"

    val all: List<String> = listOf(RETRY, OPEN_REPAIR_GUIDE, COPY_DIAGNOSTICS, EXIT)
}

/**
 * JCEF is mandatory. Failure never selects an HTTP or system-browser transport.
 * Available JCEF still fail-closes if the isolated profile cannot be established.
 */
class JcefStartupGate(
    private val probe: JcefRuntimeProbe,
    private val isolation: () -> JcefIsolationResult,
) {
    constructor(probe: JcefRuntimeProbe) : this(probe, { JcefIsolationResult.Isolated })

    fun decide(): JcefStartupDecision = when (val result = probe.probe()) {
        JcefProbeResult.Available -> when (val isolated = isolation()) {
            JcefIsolationResult.Isolated -> JcefStartupDecision.ContinueWithJcef
            is JcefIsolationResult.Failed -> JcefStartupDecision.ShowRecovery(isolated.diagnostics)
        }
        is JcefProbeResult.Unavailable -> JcefStartupDecision.ShowRecovery(result.diagnostics)
    }
}

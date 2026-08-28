package moe.momokko.intellido.browser

/**
 * Redacted JCEF failure summary. Never includes cookies, tokens, or content bodies.
 */
data class JcefDiagnostics(
    val reason: String,
    val jcefReportedSupported: Boolean,
    val javaVendor: String,
    val javaVersion: String,
    val osName: String,
    val osVersion: String,
) {
    fun copyableText(): String = buildString {
        appendLine("IntelliDo JCEF diagnostics")
        appendLine("reason=$reason")
        appendLine("jcefReportedSupported=$jcefReportedSupported")
        appendLine("javaVendor=$javaVendor")
        appendLine("javaVersion=$javaVersion")
        appendLine("osName=$osName")
        appendLine("osVersion=$osVersion")
    }

    companion object {
        fun capture(reason: String, jcefReportedSupported: Boolean): JcefDiagnostics = JcefDiagnostics(
            reason = redact(reason),
            jcefReportedSupported = jcefReportedSupported,
            javaVendor = System.getProperty("java.vendor", "unknown"),
            javaVersion = System.getProperty("java.version", "unknown"),
            osName = System.getProperty("os.name", "unknown"),
            osVersion = System.getProperty("os.version", "unknown"),
        )

        fun redact(text: String): String {
            var redacted = text
            redacted = COOKIE_PATTERN.replace(redacted, "$1=<redacted>")
            redacted = TOKEN_PATTERN.replace(redacted, "$1=<redacted>")
            return redacted
        }

        private val COOKIE_PATTERN = Regex("(?i)(cookie|set-cookie)=([^;\\s]+)")
        private val TOKEN_PATTERN = Regex("(?i)(token|csrf|authorization)=([^;\\s]+)")
    }
}

package moe.momokko.intellido.platform.instance

sealed class SupportedLaunchTarget {
    data object Focus : SupportedLaunchTarget()
    data object Home : SupportedLaunchTarget()
}

object LaunchTargets {
    fun parse(args: List<String>): List<SupportedLaunchTarget> {
        val parsed = args.mapNotNull { arg ->
            when (arg) {
                "--home" -> SupportedLaunchTarget.Home
                else -> null
            }
        }
        return parsed.ifEmpty { listOf(SupportedLaunchTarget.Focus) }
    }
}

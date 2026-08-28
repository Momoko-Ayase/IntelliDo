package moe.momokko.intellido.platform.instance

/**
 * The first process polls handoff files written by a second launch of the same channel.
 */
class InstanceHandoffWatcher(
    private val coordinator: ApplicationInstanceCoordinator,
    private val onTargets: (List<SupportedLaunchTarget>) -> Unit,
) {
    fun pollOnce() {
        val targets = coordinator.pollHandoff()
        if (targets.isNotEmpty()) {
            onTargets(targets)
        }
    }
}

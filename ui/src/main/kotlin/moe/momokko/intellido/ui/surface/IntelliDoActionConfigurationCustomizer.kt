package moe.momokko.intellido.ui.surface

import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import moe.momokko.intellido.platform.surface.IdeSurfacePolicy

/**
 * Drops programming/trial chrome while the action manager is still initializing,
 * so the first painted frame never contains Project, Run, or Unlock Ultimate.
 *
 * Do not use plugin.xml `<unregister>`: that path instantiates the action stub and
 * RunToolbarWidgetAction's constructor calls ActionManager.getInstance(), which
 * deadlocks ActionManager initialization.
 *
 * Do not unregister NewGroup: WeighingNewActionGroup.getDelegate() requires it.
 */
class IntelliDoActionConfigurationCustomizer : ActionConfigurationCustomizer {
    override fun customize(): ActionConfigurationCustomizer.CustomizeStrategy {
        return object : ActionConfigurationCustomizer.LightCustomizeStrategy {
            override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
                IdeSurfacePolicy.actionsUnregisteredAtStartup().forEach { id ->
                    if (actionRegistrar.getActionOrStub(id) != null) {
                        runCatching { actionRegistrar.unregisterAction(id) }
                    }
                }
            }
        }
    }
}

class IntelliDoMenuSurfaceCustomizer : ActionConfigurationCustomizer {
    override fun customize(): ActionConfigurationCustomizer.CustomizeStrategy {
        return object : ActionConfigurationCustomizer.SyncHeavyCustomizeStrategy {
            override fun customize(actionManager: com.intellij.openapi.actionSystem.ActionManager) {
                IdeSurfaceApplicator.applyApplicationSurface(actionManager, unregisterWidgets = false)
            }
        }
    }
}

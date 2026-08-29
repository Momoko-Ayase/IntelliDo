package moe.momokko.intellido.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.session.MemberSession
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.jcef.GifBytes
import moe.momokko.intellido.ui.session.SignInCoordinator
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon

class AccountToolbarAction : AnAction(), DumbAware {
    @Volatile
    private var avatarIcon: Icon? = null

    @Volatile
    private var avatarUser: String? = null

    override fun actionPerformed(event: AnActionEvent) {
        val runtime = runtimeOrNull() ?: return
        if (runtime.session is MemberSession.Anonymous) {
            SignInCoordinator.requestSignIn(event.project)
            return
        }
        val group = DefaultActionGroup().apply {
            add(OpenOwnProfileAction())
            add(SignOutAction())
        }
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            null,
            group,
            event.dataContext,
            JBPopupFactory.ActionSelectionAid.MNEMONICS,
            true,
        )
        val source = event.inputEvent?.component
        if (source is Component && source.isShowing) {
            popup.showUnderneathOf(source)
        } else {
            popup.showInBestPositionFor(event.dataContext)
        }
    }

    override fun update(event: AnActionEvent) {
        val runtime = runtimeOrNull()
        val locale = runtime?.locale ?: java.util.Locale.getDefault()
        when (val session = runtime?.session ?: MemberSession.Anonymous) {
            MemberSession.Anonymous -> {
                event.presentation.text = IntelliDoStrings.message("account.placeholder", locale)
                event.presentation.description = event.presentation.text
                event.presentation.icon = PLACEHOLDER
                avatarIcon = null
                avatarUser = null
            }
            is MemberSession.SignedIn -> {
                event.presentation.text = session.displayLabel()
                event.presentation.description = session.username
                val icon = avatarIcon.takeIf { avatarUser == session.username } ?: initialsIcon(session.username)
                event.presentation.icon = icon
                if (avatarUser != session.username) {
                    loadAvatar(session)
                }
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun loadAvatar(session: MemberSession.SignedIn) {
        avatarUser = session.username
        val url = session.avatarUrl(48) ?: return
        val loader = runtimeOrNull()?.mediaLoader ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val bytes = runCatching { loader.load(listOf(url), 48) }.getOrDefault(emptyMap()).values.firstOrNull()
                ?: return@executeOnPooledThread
            if (GifBytes.isGif(bytes)) {
                return@executeOnPooledThread
            }
            avatarIcon = CircleIcon(ImageIcon(bytes).image, JBUI.scale(ICON))
            avatarUser = session.username
        }
    }

    private fun runtimeOrNull(): IntelliDoRuntime? =
        runCatching { service<IntelliDoRuntime>() }.getOrNull()

    private fun initialsIcon(username: String): Icon {
        val size = JBUI.scale(ICON)
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = java.awt.Color(0x6B6458)
        g.fill(Ellipse2D.Float(0.5f, 0.5f, size - 1f, size - 1f))
        g.color = java.awt.Color.WHITE
        g.font = g.font.deriveFont(java.awt.Font.BOLD, (size * 0.55f))
        val letter = username.trim().first().uppercaseChar().toString()
        val fm = g.fontMetrics
        g.drawString(letter, (size - fm.stringWidth(letter)) / 2, (size - fm.height) / 2 + fm.ascent)
        g.dispose()
        return ImageIcon(image)
    }

    companion object {
        private const val ICON: Int = 22
        private val PLACEHOLDER: Icon =
            IconLoader.getIcon("/icons/user.svg", AccountToolbarAction::class.java)
    }
}

private class CircleIcon(
    private val source: Image,
    private val size: Int,
) : Icon {
    override fun getIconWidth(): Int = size

    override fun getIconHeight(): Int = size

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.translate(x, y)
        g2.clip(Ellipse2D.Float(0.5f, 0.5f, size - 1f, size - 1f))
        g2.drawImage(source, 0, 0, size, size, null)
        g2.dispose()
    }
}

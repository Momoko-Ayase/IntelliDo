package moe.momokko.intellido.ui.session

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import moe.momokko.intellido.domain.session.MemberSession
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.transport.FakeLinuxDoCommunityClient
import moe.momokko.intellido.ui.jcef.JcefSignInDialog
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import java.util.concurrent.atomic.AtomicBoolean

object SignInCoordinator {
    private val dialogOpen = AtomicBoolean(false)
    private val closed = java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CountDownLatch?>(null)

    fun isDialogOpen(): Boolean = dialogOpen.get()

    fun waitWhileOpen() {
        closed.get()?.await(5, java.util.concurrent.TimeUnit.MINUTES)
    }

    fun requestSignIn(project: Project? = activeProject()) {
        val runtime = service<IntelliDoRuntime>()
        if (runtime.session is MemberSession.SignedIn) {
            return
        }
        if (runtime.preferFakeTransport()) {
            applySignedIn(project, FakeLinuxDoCommunityClient.HELPER_SESSION)
            return
        }
        if (!dialogOpen.compareAndSet(false, true)) {
            return
        }
        val latch = java.util.concurrent.CountDownLatch(1)
        closed.set(latch)
        val open = Runnable {
            try {
                val session = JcefSignInDialog(runtime.locale).awaitSession()
                dialogOpen.set(false)
                latch.countDown()
                closed.compareAndSet(latch, null)
                if (session is MemberSession.SignedIn) {
                    applySignedIn(project, session)
                }
            } finally {
                dialogOpen.set(false)
                latch.countDown()
                closed.compareAndSet(latch, null)
            }
        }
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            open.run()
        } else {
            app.invokeLater(open)
        }
    }

    fun offerExpiredReauth(project: Project? = activeProject()) {
        val runtime = service<IntelliDoRuntime>()
        if (runtime.suppressAutoReauth || runtime.session is MemberSession.SignedIn) {
            return
        }
        val locale = runtime.locale
        val ok = Messages.showYesNoDialog(
            project,
            IntelliDoStrings.message("signIn.expired", locale),
            IntelliDoStrings.message("signIn.title", locale),
            IntelliDoStrings.message("action.signIn", locale),
            IntelliDoStrings.message("signIn.cancel", locale),
            Messages.getWarningIcon(),
        ) == Messages.YES
        if (!ok) {
            runtime.suppressAutoReauth = true
            return
        }
        requestSignIn(project)
    }

    fun confirmThenSignIn(project: Project? = activeProject()): Boolean {
        val runtime = service<IntelliDoRuntime>()
        if (runtime.session is MemberSession.SignedIn) {
            return true
        }
        val locale = runtime.locale
        val ok = Messages.showYesNoDialog(
            project,
            IntelliDoStrings.message("signIn.needed", locale),
            IntelliDoStrings.message("signIn.needed.title", locale),
            IntelliDoStrings.message("action.signIn", locale),
            IntelliDoStrings.message("signIn.cancel", locale),
            Messages.getQuestionIcon(),
        ) == Messages.YES
        if (!ok) {
            return false
        }
        requestSignIn(project)
        return false
    }

    fun signOut(project: Project? = activeProject()) {
        val runtime = service<IntelliDoRuntime>()
        if (runtime.session !is MemberSession.SignedIn) {
            return
        }
        val locale = runtime.locale
        val ok = Messages.showYesNoDialog(
            project,
            IntelliDoStrings.message("signOut.confirm", locale),
            IntelliDoStrings.message("signOut.title", locale),
            Messages.getQuestionIcon(),
        ) == Messages.YES
        if (!ok) {
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching { runtime.communityClient.signOutRemote() }
            runtime.wipeBrowserProfile()
            ApplicationManager.getApplication().invokeLater {
                runtime.applySession(MemberSession.Anonymous, persist = true)
                val target = project ?: activeProject()
                if (target != null) {
                    IntelliDoWorkspace.applyAnonymousHome(target)
                }
            }
        }
    }

    private fun applySignedIn(project: Project?, session: MemberSession.SignedIn) {
        val runtime = service<IntelliDoRuntime>()
        val previous = runtime.session
        if (previous is MemberSession.SignedIn && previous.username != session.username) {
            val target = project ?: activeProject()
            if (target != null) {
                IntelliDoWorkspace.closeNonHomeTabs(target)
            }
        }
        runtime.applySession(session, persist = true)
        runtime.invalidateCommunityOrigin()
        val target = project ?: activeProject()
        if (target != null) {
            IntelliDoWorkspace.onSessionChanged(target)
        }
    }

    private fun activeProject(): Project? =
        ProjectManager.getInstance().openProjects.firstOrNull()
}

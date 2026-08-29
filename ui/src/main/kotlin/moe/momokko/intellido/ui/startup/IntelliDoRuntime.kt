package moe.momokko.intellido.ui.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import moe.momokko.intellido.browser.BrowserPersistence
import moe.momokko.intellido.browser.IsolatedBrowserProfile
import moe.momokko.intellido.browser.IsolatedBrowserProfiles
import moe.momokko.intellido.domain.session.MemberSession
import moe.momokko.intellido.platform.home.HomeTopicsController
import moe.momokko.intellido.platform.i18n.InMemoryLocalPreferenceStore
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.i18n.LocalPreferenceStore
import com.intellij.openapi.ui.Messages
import moe.momokko.intellido.platform.identity.InMemoryRememberedSessionStore
import moe.momokko.intellido.platform.identity.RememberedSessionStore
import moe.momokko.intellido.platform.reading.ReadingAppearance
import moe.momokko.intellido.platform.reading.ReadingPreferences
import moe.momokko.intellido.platform.identity.ProductIdentity
import moe.momokko.intellido.ui.session.MemberSessionListener
import moe.momokko.intellido.platform.instance.ApplicationInstanceCoordinator
import moe.momokko.intellido.platform.instance.HeldInstanceLock
import moe.momokko.intellido.platform.instance.InstanceHandoffWatcher
import moe.momokko.intellido.platform.instance.SupportedLaunchTarget
import moe.momokko.intellido.platform.live.GuestLiveSession
import moe.momokko.intellido.platform.topic.TopicPreviewSession
import moe.momokko.intellido.platform.welcome.WelcomeVisibility
import moe.momokko.intellido.transport.BridgedLinuxDoCommunityClient
import moe.momokko.intellido.transport.FakeLinuxDoCommunityClient
import moe.momokko.intellido.transport.JsonFetcherMessageBusPoller
import moe.momokko.intellido.transport.LinuxDoCommunityClient
import moe.momokko.intellido.transport.LinuxDoJsonFetcher
import moe.momokko.intellido.transport.LinuxDoMediaLoader
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Service(Service.Level.APP)
class IntelliDoRuntime {
    var identity: ProductIdentity = ProductIdentity.fromSystem()
        private set
    var locale: Locale = Locale.SIMPLIFIED_CHINESE
        private set
    var instanceLock: HeldInstanceLock? = null
        private set
    var coordinator: ApplicationInstanceCoordinator? = null
        private set
    var browserProfile: IsolatedBrowserProfile? = null
        private set
    var communityClient: LinuxDoCommunityClient = FakeLinuxDoCommunityClient()
        private set
    var homeController: HomeTopicsController = HomeTopicsController(communityClient)
        private set
    var preferences: LocalPreferenceStore = InMemoryLocalPreferenceStore()
        private set
    var welcomeVisibility: WelcomeVisibility = WelcomeVisibility(preferences)
        private set
    var topicPreview: TopicPreviewSession = TopicPreviewSession()
        private set
    var session: MemberSession = MemberSession.Anonymous
        private set
    var remembered: RememberedSessionStore = InMemoryRememberedSessionStore()
        private set
    var suppressAutoReauth: Boolean = false
    var persistWarning: Boolean = false
        private set
    var usesLiveCommunity: Boolean = false
        private set
    @Volatile
    private var memberSessionProbed: Boolean = false
    private val memberSessionLock = Any()
    var mediaLoader: LinuxDoMediaLoader? = null
        private set
    var liveSession: GuestLiveSession? = null
        private set
    /** Last captured JCEF failure summary, so the copy-diagnostics action has something real to copy. */
    @Volatile
    var lastJcefDiagnostics: moe.momokko.intellido.browser.JcefDiagnostics? = null
    private var jsonFetcher: AutoCloseable? = null
    private var handoffExecutor: ScheduledExecutorService? = null

    fun initialize(
        identity: ProductIdentity,
        locale: Locale,
        lock: HeldInstanceLock?,
        preferences: LocalPreferenceStore,
        coordinator: ApplicationInstanceCoordinator? = null,
        remembered: RememberedSessionStore = InMemoryRememberedSessionStore(),
    ) {
        this.identity = identity
        this.locale = locale
        this.instanceLock = lock
        this.coordinator = coordinator
        this.preferences = preferences
        this.remembered = remembered
        this.welcomeVisibility = WelcomeVisibility(preferences)
        ReadingAppearance.replace(ReadingPreferences.load(preferences))
        this.communityClient = FakeLinuxDoCommunityClient()
        this.homeController = HomeTopicsController(communityClient)
        this.topicPreview = TopicPreviewSession()
        this.session = MemberSession.Anonymous
        this.suppressAutoReauth = false
        this.persistWarning = false
        this.usesLiveCommunity = false
        this.memberSessionProbed = false
        this.mediaLoader = null
        stopLiveSession()
    }

    fun applySession(next: MemberSession, persist: Boolean = true) {
        val previous = session
        if (previous == next) {
            return
        }
        session = next
        when (next) {
            is MemberSession.SignedIn -> {
                (communityClient as? FakeLinuxDoCommunityClient)?.adoptSession(next)
                if (persist) {
                    persistUsername(next.username)
                }
            }
            MemberSession.Anonymous -> {
                (communityClient as? FakeLinuxDoCommunityClient)?.adoptSession(next)
                if (persist) {
                    persistWarning = false
                    runOffEdt { remembered.clear() }
                }
            }
        }
        val app = ApplicationManager.getApplication() ?: return
        if (app.isDisposed) {
            return
        }
        app.messageBus.syncPublisher(MemberSessionListener.TOPIC).sessionChanged(previous, next)
    }

    private fun applySessionBlocking(next: MemberSession) {
        val app = ApplicationManager.getApplication()
        if (app == null || app.isDisposed || app.isDispatchThread) {
            applySession(next, persist = true)
            return
        }
        val done = java.util.concurrent.CountDownLatch(1)
        app.invokeLater {
            try {
                applySession(next, persist = true)
            } finally {
                done.countDown()
            }
        }
        done.await(5, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun persistUsername(username: String) {
        runOffEdt {
            val ok = remembered.remember(username)
            persistWarning = !ok
            if (!ok) {
                val app = ApplicationManager.getApplication() ?: return@runOffEdt
                app.invokeLater {
                    if (app.isDisposed) {
                        return@invokeLater
                    }
                    Messages.showWarningDialog(
                        IntelliDoStrings.message("signIn.persistUnavailable", locale),
                        IntelliDoStrings.message("signIn.title", locale),
                    )
                }
            }
        }
    }

    private fun runOffEdt(work: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app == null || app.isDisposed) {
            work()
            return
        }
        if (app.isDispatchThread) {
            app.executeOnPooledThread(work)
        } else {
            work()
        }
    }

    fun communityFetcher(): LinuxDoJsonFetcher? = jsonFetcher as? LinuxDoJsonFetcher

    fun jcefClient(): com.intellij.ui.jcef.JBCefClient? =
        (jsonFetcher as? moe.momokko.intellido.ui.jcef.JcefLinuxDoJsonFetcher)?.cefClient

    fun invalidateCommunityOrigin() {
        (jsonFetcher as? moe.momokko.intellido.ui.jcef.JcefLinuxDoJsonFetcher)?.invalidateOrigin()
        communityClient.invalidateCatalog()
    }

    /**
     * Probe `/session/current.json` once, before Home's first `/latest.json`.
     * Cookies already decide the member; this only updates native UI so the
     * toolbar is not still anonymous while topics load.
     */
    fun ensureMemberSession() {
        if (memberSessionProbed) {
            return
        }
        synchronized(memberSessionLock) {
            if (memberSessionProbed) {
                return
            }
            val loaded = runCatching { communityClient.loadCurrentSession() }
                .getOrDefault(MemberSession.Anonymous)
            memberSessionProbed = true
            applySessionBlocking(loaded)
        }
    }

    fun wipeBrowserProfile() {
        runCatching { communityFetcher()?.clearCookies() }
        val profile = browserProfile ?: return
        browserProfile = runCatching { IsolatedBrowserProfiles.signOut(profile) }.getOrElse {
            profile.copy(persistence = BrowserPersistence.ProcessOnly)
        }
    }

    fun attachBrowserProfile(profile: IsolatedBrowserProfile) {
        browserProfile = profile
    }

    fun attachLiveCommunity(fetcher: LinuxDoJsonFetcher) {
        stopLiveSession()
        runCatching { jsonFetcher?.close() }
        jsonFetcher = fetcher as? AutoCloseable
        mediaLoader = fetcher as? LinuxDoMediaLoader
        communityClient = BridgedLinuxDoCommunityClient(fetcher)
        homeController = HomeTopicsController(communityClient)
        usesLiveCommunity = true
    }

    /**
     * MessageBus and site settings wait until Home has painted once so the first
     * `/latest.json` is not stuck behind a 40s long-poll or a competing GET.
     */
    fun ensureLiveSession() {
        if (liveSession != null || !usesLiveCommunity) {
            return
        }
        val fetcher = jsonFetcher as? LinuxDoJsonFetcher ?: return
        startLiveSession(fetcher)
    }

    fun preferFakeTransport(): Boolean =
        System.getProperty("intellido.transport", "jcef").equals("fake", ignoreCase = true)

    fun awaitCommunity(timeoutMs: Long = 20_000) {
        if (preferFakeTransport() || usesLiveCommunity) {
            return
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!usesLiveCommunity && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
    }

    fun startHandoffWatcher() {
        val coordinator = coordinator ?: return
        if (handoffExecutor != null) {
            return
        }
        val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "IntelliDo-instance-handoff").apply { isDaemon = true }
        }
        handoffExecutor = executor
        val watcher = InstanceHandoffWatcher(coordinator, ::dispatchHandoff)
        executor.scheduleAtFixedRate(
            {
                runCatching { watcher.pollOnce() }
            },
            500,
            500,
            TimeUnit.MILLISECONDS,
        )
    }

    fun shutdown() {
        stopLiveSession()
        handoffExecutor?.shutdownNow()
        handoffExecutor = null
        runCatching { jsonFetcher?.close() }
        jsonFetcher = null
        mediaLoader = null
        instanceLock?.close()
        instanceLock = null
    }

    @Volatile
    private var messageBusOrigin: String = moe.momokko.intellido.transport.LinuxDoUrls.MESSAGE_BUS_ORIGIN

    private fun startLiveSession(fetcher: LinuxDoJsonFetcher) {
        stopLiveSession()
        val poller = JsonFetcherMessageBusPoller(fetcher) { messageBusOrigin }
        val session = GuestLiveSession(poller)
        session.watchLatest()
        session.start()
        liveSession = session
        ApplicationManager.getApplication().executeOnPooledThread {
            val origin = runCatching { communityClient.loadSiteSettings().messageBusOrigin }.getOrNull()
            if (!origin.isNullOrBlank()) {
                messageBusOrigin = origin
            }
        }
    }

    private fun stopLiveSession() {
        liveSession?.stop()
        liveSession = null
    }

    private fun dispatchHandoff(targets: List<SupportedLaunchTarget>) {
        ApplicationManager.getApplication().invokeLater {
            val project = IntelliDoWorkspace.openOrFocus() ?: return@invokeLater
            if (targets.contains(SupportedLaunchTarget.Home) || targets.contains(SupportedLaunchTarget.Focus)) {
                IntelliDoWorkspace.focusHome(project)
            }
        }
    }
}

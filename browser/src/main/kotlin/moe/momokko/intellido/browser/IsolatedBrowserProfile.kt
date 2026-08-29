package moe.momokko.intellido.browser

import moe.momokko.intellido.platform.identity.ReleaseChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

enum class BrowserPersistence {
    ProcessOnly,
    OsProtected,
}

/**
 * Dedicated JCEF profile for the single LINUX DO account.
 *
 * Cloudflare `cf_clearance` is kept on disk across anonymous launches (Fluxdo
 * does the same). Discourse `_t` / `_forum_session` are stripped after CEF
 * starts when the member is not remembered. Sign-out still wipes the directory.
 */
data class IsolatedBrowserProfile(
    val channel: ReleaseChannel,
    val persistence: BrowserPersistence,
    val userDataDirectory: Path,
) {
    val persistCookiesToDisk: Boolean
        get() = true
}

data class IsolatedJcefSettings(
    val cachePath: Path,
    val persistSessionCookies: Boolean,
    val locale: String = LOCALE,
    val acceptLanguage: String = ACCEPT_LANGUAGE,
) {
    companion object {
        const val LOCALE: String = "zh-CN"
        const val ACCEPT_LANGUAGE: String = "zh-CN,zh"
    }
}

object IsolatedBrowserProfiles {
    const val CACHE_PATH_PROPERTY: String = "ide.browser.jcef.cache.path"
    const val DEFAULT_CACHE_DIR: String = "jcef_cache"

    val CEF_SWITCHES: List<String> = listOf(
        "--disable-extensions",
        "--disable-component-extensions-with-background-pages",
        "--disable-default-apps",
    )

    fun prepareAnonymous(root: Path, channel: ReleaseChannel): IsolatedBrowserProfile =
        prepare(root, channel, remembered = false)

    /**
     * Reuse the dedicated profile without wiping leftover cookies. Callers must
     * only do this when an OS-protected store remembered a trusted session.
     */
    fun prepareRemembered(root: Path, channel: ReleaseChannel): IsolatedBrowserProfile =
        prepare(root, channel, remembered = true)

    fun prepare(root: Path, channel: ReleaseChannel, remembered: Boolean): IsolatedBrowserProfile {
        val directory = root
            .resolve("jcef")
            .resolve(channel.name.lowercase())
            .resolve("profile")
        require(!isForbiddenSystemBrowserPath(root) && !isForbiddenSystemBrowserPath(directory)) {
            "IntelliDo must not use a system browser profile path: $directory"
        }
        Files.createDirectories(directory)
        return IsolatedBrowserProfile(
            channel = channel,
            persistence = if (remembered) BrowserPersistence.OsProtected else BrowserPersistence.ProcessOnly,
            userDataDirectory = directory,
        )
    }

    fun settingsFor(profile: IsolatedBrowserProfile): IsolatedJcefSettings =
        IsolatedJcefSettings(
            cachePath = profile.userDataDirectory,
            persistSessionCookies = profile.persistCookiesToDisk,
        )

    fun jvmOverrides(settings: IsolatedJcefSettings): Map<String, String> =
        mapOf(CACHE_PATH_PROPERTY to settings.cachePath.toAbsolutePath().toString())

    fun signOut(profile: IsolatedBrowserProfile): IsolatedBrowserProfile {
        wipe(profile.userDataDirectory)
        Files.createDirectories(profile.userDataDirectory)
        return IsolatedBrowserProfile(
            channel = profile.channel,
            persistence = BrowserPersistence.ProcessOnly,
            userDataDirectory = profile.userDataDirectory,
        )
    }

    fun isForbiddenSystemBrowserPath(path: Path): Boolean {
        val normalized = path.toAbsolutePath().normalize().toString().lowercase().replace('\\', '/')
        return FORBIDDEN_PATH_MARKERS.any { marker -> normalized.contains(marker) }
    }

    fun wipe(directory: Path) {
        if (!directory.exists()) {
            return
        }
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private val FORBIDDEN_PATH_MARKERS: List<String> = listOf(
        "/google/chrome",
        "/microsoft/edge",
        "/mozilla/firefox",
        "/chromium/",
        "/brave/",
        "/opera/",
        "/vivaldi/",
    )
}

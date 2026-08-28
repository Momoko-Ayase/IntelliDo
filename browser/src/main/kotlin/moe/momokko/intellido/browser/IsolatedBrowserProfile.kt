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
 * Anonymous sessions stay process-only: leftover cookies are wiped on prepare
 * and CEF must not persist session cookies to disk.
 */
data class IsolatedBrowserProfile(
    val channel: ReleaseChannel,
    val persistence: BrowserPersistence,
    val userDataDirectory: Path,
) {
    val persistCookiesToDisk: Boolean
        get() = persistence == BrowserPersistence.OsProtected
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

    fun prepareAnonymous(root: Path, channel: ReleaseChannel): IsolatedBrowserProfile {
        val directory = root
            .resolve("jcef")
            .resolve(channel.name.lowercase())
            .resolve("anonymous")
        require(!isForbiddenSystemBrowserPath(root) && !isForbiddenSystemBrowserPath(directory)) {
            "IntelliDo must not use a system browser profile path: $directory"
        }
        wipe(directory)
        wipe(root.resolve(DEFAULT_CACHE_DIR))
        Files.createDirectories(directory)
        return IsolatedBrowserProfile(
            channel = channel,
            persistence = BrowserPersistence.ProcessOnly,
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

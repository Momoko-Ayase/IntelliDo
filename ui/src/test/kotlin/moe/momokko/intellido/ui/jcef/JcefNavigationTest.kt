package moe.momokko.intellido.ui.jcef

import moe.momokko.intellido.domain.topic.DiscourseLink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JcefNavigationTest {
    @Test
    fun `http and mailto require confirmation`() {
        assertTrue(JcefNavigation.decide("http://example.com", mainFrame = true) is JcefNav.Confirm)
        assertTrue(JcefNavigation.decide("mailto:ops@linux.do", mainFrame = true) is JcefNav.Confirm)
    }

    @Test
    fun `visible tabs still allow cloudflare as in-app`() {
        assertEquals(
            JcefNav.Allow,
            JcefNavigation.decide(
                "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b/turnstile",
                mainFrame = true,
            ),
        )
    }

    @Test
    fun `hidden fetcher pins the main frame to linux do`() {
        assertEquals(
            JcefNav.Block,
            JcefNavigation.decide(
                "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b/turnstile",
                mainFrame = true,
                pinLinuxDo = true,
            ),
        )
        assertEquals(
            JcefNav.Allow,
            JcefNavigation.decide("https://linux.do/", mainFrame = true, pinLinuxDo = true, nativeStaysInCef = true),
        )
        assertEquals(
            JcefNav.Allow,
            JcefNavigation.decide(
                "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b/turnstile",
                mainFrame = false,
                pinLinuxDo = true,
            ),
        )
    }

    @Test
    fun `attachment urls become download actions`() {
        val action = JcefNavigation.decide("https://linux.do/uploads/default/original/1X/a.bin", mainFrame = true)
        assertTrue(action is JcefNav.Download)
        assertEquals(
            JcefNav.Block,
            JcefNavigation.download("https://linux.do/u/system/summary.json", "summary.json"),
        )
    }

    @Test
    fun `native topic links can stay in cef for the challenge dialog`() {
        val native = JcefNavigation.decide("https://linux.do/t/welcome/101", mainFrame = true)
        assertTrue(native is JcefNav.Native)
        assertEquals((native as JcefNav.Native).link, DiscourseLink.Topic(101, slug = "welcome"))
        assertEquals(
            JcefNav.Allow,
            JcefNavigation.decide("https://linux.do/t/welcome/101", mainFrame = true, nativeStaysInCef = true),
        )
    }

    @Test
    fun `auth flow keeps https oauth origins in the modal`() {
        assertEquals(
            JcefNav.Allow,
            JcefNavigation.decide("https://linux.do/login", mainFrame = true, authFlow = true),
        )
        assertEquals(
            JcefNav.Allow,
            JcefNavigation.decide("https://github.com/login/oauth/authorize", mainFrame = true, authFlow = true),
        )
        assertTrue(
            JcefNavigation.decide("http://example.com", mainFrame = true, authFlow = true) is JcefNav.Confirm,
        )
    }
}

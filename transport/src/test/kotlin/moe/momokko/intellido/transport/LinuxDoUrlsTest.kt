package moe.momokko.intellido.transport

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinuxDoUrlsTest {
    @Test
    fun `login and oauth html routes are auth locations`() {
        assertTrue(LinuxDoUrls.isAuthLocation("https://linux.do/login"))
        assertTrue(LinuxDoUrls.isAuthLocation("https://linux.do/signup"))
        assertTrue(LinuxDoUrls.isAuthLocation("https://linux.do/auth/github"))
        assertTrue(LinuxDoUrls.isAuthLocation("/session/email-login/abc"))
        assertFalse(LinuxDoUrls.isAuthLocation("https://linux.do/"))
        assertFalse(LinuxDoUrls.isAuthLocation("https://linux.do/session/current.json"))
        assertFalse(LinuxDoUrls.isAuthLocation("https://github.com/login/oauth/authorize"))
    }
}

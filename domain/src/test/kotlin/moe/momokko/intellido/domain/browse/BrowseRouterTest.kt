package moe.momokko.intellido.domain.browse

import moe.momokko.intellido.domain.topic.DiscourseLink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrowseRouterTest {
    @Test
    fun `known linux do routes stay native`() {
        val topic = BrowseRouter.decide("https://linux.do/t/welcome/101/2")
        assertTrue(topic is BrowseDecision.Native)
        assertEquals(DiscourseLink.Topic(101, 2, "welcome"), (topic as BrowseDecision.Native).link)

        val about = BrowseRouter.decide("/about")
        assertEquals(DiscourseLink.Directory(DiscourseLink.DirectoryPage.ABOUT), (about as BrowseDecision.Native).link)
    }

    @Test
    fun `allowlisted auxiliary origins open in the restricted browser`() {
        val connect = BrowseRouter.decide("https://connect.linux.do/progress")
        assertEquals("https://connect.linux.do", (connect as BrowseDecision.InApp).origin)
        assertEquals("https://idcflare.com", (BrowseRouter.decide("https://idcflare.com/") as BrowseDecision.InApp).origin)
        assertEquals("https://go.linux.do", (BrowseRouter.decide("https://go.linux.do/pub/resources") as BrowseDecision.InApp).origin)
        val faq = BrowseRouter.decide("https://linux.do/faq")
        assertEquals("https://linux.do", (faq as BrowseDecision.InApp).origin)
    }

    @Test
    fun `leaving the allowlist uses the system browser or a copy-only block`() {
        assertTrue(BrowseRouter.decide("https://t.me/linux_do_channel") is BrowseDecision.External)
        assertTrue(BrowseRouter.decide("https://example.com/x") is BrowseDecision.External)
        assertTrue(BrowseRouter.decide("http://example.com") is BrowseDecision.Confirm)
        assertTrue(BrowseRouter.decide("mailto:ops@linux.do") is BrowseDecision.Confirm)
        assertTrue(BrowseRouter.decide("javascript:alert(1)") is BrowseDecision.CopyOnly)
        assertTrue(BrowseRouter.decide("file:///tmp/x") is BrowseDecision.CopyOnly)
        assertTrue(BrowseRouter.decide("data:text/html,hi") is BrowseDecision.CopyOnly)
    }

    @Test
    fun `trusted origins are exact https origins shipped with the build`() {
        assertEquals(
            setOf(
                "https://linux.do",
                "https://connect.linux.do",
                "https://idcflare.com",
                "https://go.linux.do",
            ),
            TrustedOrigins.VISIBLE,
        )
        assertTrue(TrustedOrigins.isAllowed("https://linux.do"))
        assertTrue(!TrustedOrigins.isAllowed("https://evil.linux.do"))
        assertTrue(!TrustedOrigins.isAllowed("https://linux.do.evil.com"))
    }

    @Test
    fun `cloudflare challenge widgets stay in cef instead of leaving the allowlist`() {
        val widget = BrowseRouter.decide("https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b/turnstile")
        assertEquals("https://challenges.cloudflare.com", (widget as BrowseDecision.InApp).origin)
        assertTrue(TrustedOrigins.isEmbedded("https://challenges.cloudflare.com"))
        assertTrue(TrustedOrigins.isLoadAllowed("https://challenges.cloudflare.com"))
        assertTrue(!TrustedOrigins.VISIBLE.contains("https://challenges.cloudflare.com"))
        assertTrue(BrowseRouter.shouldLoadInCef("https://challenges.cloudflare.com/cdn-cgi/challenge-platform/", mainFrame = false))
        assertTrue(BrowseRouter.shouldLoadInCef("about:blank", mainFrame = false))
        assertTrue(BrowseRouter.shouldLoadInCef("blob:https://challenges.cloudflare.com/abc", mainFrame = false))
        assertTrue(!BrowseRouter.shouldLoadInCef("https://t.me/linux_do_channel", mainFrame = false))
        assertTrue(!BrowseRouter.shouldLoadInCef("https://t.me/linux_do_channel", mainFrame = true))
    }
}

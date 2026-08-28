package moe.momokko.intellido.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudflareChallengeTest {
    @Test
    fun `community json is not a challenge`() {
        assertFalse(CloudflareChallenge.isChallenge(200, """{"topic_list":{"topics":[]}}"""))
        assertFalse(CloudflareChallenge.isChallenge(403, """{"errors":["forbidden"]}"""))
    }

    @Test
    fun `cloudflare interstitial is a challenge`() {
        assertTrue(
            CloudflareChallenge.isChallenge(
                403,
                """<html><title>Just a moment...</title><div id="challenge-platform"></div></html>""",
            ),
        )
        assertTrue(
            CloudflareChallenge.isChallenge(
                200,
                """<html>Checking your browser before accessing linux.do</html>""",
            ),
        )
        assertFalse(CloudflareChallenge.isChallenge(200, "<html><script src=\"challenge-platform\"></script></html>"))
        assertTrue(CloudflareChallenge.isChallengeUrl("https://linux.do/cdn-cgi/challenge-platform/h/g/turnstile"))
        assertFalse(CloudflareChallenge.isChallengeUrl("https://linux.do/latest.json"))
    }

    @Test
    fun `plain 403 html without cloudflare markers is not a challenge`() {
        assertFalse(CloudflareChallenge.isChallenge(403, "<html><body>denied</body></html>"))
        assertFalse(CloudflareChallenge.isChallenge(200, ""))
        assertFalse(CloudflareChallenge.isChallenge(0, ""))
    }

    @Test
    fun `chinese turnstile copy is a challenge`() {
        assertTrue(CloudflareChallenge.isChallenge(200, "<html>确认您是真人</html>"))
        assertTrue(CloudflareChallenge.isChallenge(403, "请完成安全检查后再访问"))
        assertFalse(CloudflareChallenge.isChallenge(200, "<html>佬友七夕快乐</html>"))
    }

    @Test
    fun `community shell requires linux do chrome not just long text`() {
        val home = "https://linux.do/"
        assertFalse(CloudflareChallenge.isCommunityShell(home, ready = false))
        assertFalse(CloudflareChallenge.isCommunityShell("https://linux.do/cdn-cgi/challenge", ready = true))
        assertTrue(CloudflareChallenge.isCommunityShell(home, ready = true))
        val probe = CloudflareChallenge.parsePageProbe("ready::https://linux.do/::最新")
        assertTrue(probe.ready)
        assertFalse(probe.turnstile)
        assertEquals("https://linux.do/", probe.url)
        assertEquals("最新", probe.text)
        assertFalse(CloudflareChallenge.parsePageProbe("https://linux.do/").ready)
    }

    @Test
    fun `dialog closes when turnstile disappears without waiting for community chrome`() {
        val widget = CloudflareChallenge.parsePageProbe("turnstile::https://linux.do/::确认您是真人")
        assertTrue(widget.turnstile)
        assertFalse(CloudflareChallenge.dialogMayClose(widget, sawTurnstile = false))
        assertFalse(CloudflareChallenge.dialogMayClose(widget, sawTurnstile = true))

        val blank = CloudflareChallenge.parsePageProbe("wait::https://linux.do/::")
        assertFalse(CloudflareChallenge.dialogMayClose(blank, sawTurnstile = false))
        assertTrue(CloudflareChallenge.dialogMayClose(blank, sawTurnstile = true))

        val home = CloudflareChallenge.parsePageProbe("ready::https://linux.do/::最新")
        assertTrue(CloudflareChallenge.dialogMayClose(home, sawTurnstile = false))
        assertFalse(
            CloudflareChallenge.dialogMayClose(
                CloudflareChallenge.parsePageProbe("wait::https://linux.do/cdn-cgi/challenge::"),
                sawTurnstile = true,
            ),
        )
    }

    @Test
    fun `homepage copy closes the dialog even if selectors miss`() {
        val text = "LINUX DO\nLog In\nLatest\n真诚、友善、团结、专业，共建你我引以为荣之社区。《社区准则》\n佬友七夕快乐"
        val probe = CloudflareChallenge.parsePageProbe("wait::https://linux.do/::$text")
        assertFalse(probe.ready)
        assertTrue(CloudflareChallenge.looksLikePassedHome(text))
        assertTrue(CloudflareChallenge.dialogMayClose(probe, sawTurnstile = false))
        assertTrue(CloudflareChallenge.looksLikePassedHome("LINUX DO 登录"))
        assertTrue(
            CloudflareChallenge.dialogMayClose(
                CloudflareChallenge.parsePageProbe("wait::https://linux.do/::LINUX DO 登录"),
                sawTurnstile = false,
            ),
        )
        val leftover = CloudflareChallenge.parsePageProbe("turnstile::https://linux.do/::$text")
        assertTrue(CloudflareChallenge.dialogMayClose(leftover, sawTurnstile = true))
    }

    @Test
    fun `latest json is not accepted as categories payload`() {
        val latest = """{"topic_list":{"topics":[]}}"""
        assertFalse(CloudflareChallenge.isExpectedPayload("https://linux.do/categories.json", latest))
        assertTrue(CloudflareChallenge.isExpectedPayload("https://linux.do/latest.json", latest))
        assertTrue(CloudflareChallenge.isExpectedPayload("https://linux.do/hot.json", latest))
        assertTrue(CloudflareChallenge.isExpectedPayload("https://linux.do/top.json", latest))
        assertTrue(
            CloudflareChallenge.isExpectedPayload(
                "https://linux.do/categories.json",
                """{"category_list":{"categories":[]}}""",
            ),
        )
    }

    @Test
    fun `message bus long poll arrays are expected payloads`() {
        assertTrue(
            CloudflareChallenge.isExpectedPayload(
                "https://linux.do/message-bus/cafef00d/poll",
                "[]",
            ),
        )
        assertTrue(
            CloudflareChallenge.isExpectedPayload(
                "https://linux.do/message-bus/cafef00d/poll",
                """[{"channel":"/latest","data":{}}]""",
            ),
        )
        assertFalse(
            CloudflareChallenge.isExpectedPayload(
                "https://linux.do/message-bus/cafef00d/poll",
                "<html>Just a moment...</html>",
            ),
        )
    }
}

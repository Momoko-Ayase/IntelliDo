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
        assertFalse(CloudflareChallenge.isChallengeUrl("https://linux.do/challenge"))
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
    fun `dialog stays open until csrf or topic list json arrives`() {
        val widget = CloudflareChallenge.parsePageProbe("turnstile::https://linux.do/::确认您是真人")
        assertTrue(widget.turnstile)
        assertFalse(CloudflareChallenge.dialogMayClose(widget))

        val blank = CloudflareChallenge.parsePageProbe("wait::https://linux.do/::")
        assertFalse(CloudflareChallenge.dialogMayClose(blank))

        val logo = CloudflareChallenge.parsePageProbe("ready::https://linux.do/::最新")
        assertTrue(logo.ready)
        assertFalse(CloudflareChallenge.dialogMayClose(logo))
        val passed = CloudflareChallenge.parsePageProbe("passed::https://linux.do/::{\"csrf\":\"token\"}")
        assertTrue(passed.passed)
        assertTrue(CloudflareChallenge.dialogMayClose(passed))
        assertTrue(
            CloudflareChallenge.dialogMayClose(
                CloudflareChallenge.parsePageProbe("passed::https://linux.do/::{\"topic_list\":{}}"),
            ),
        )
        assertFalse(
            CloudflareChallenge.dialogMayClose(
                CloudflareChallenge.parsePageProbe("wait::https://linux.do/cdn-cgi/challenge::"),
            ),
        )
    }

    @Test
    fun `homepage chrome and leftover clearance are not a pass`() {
        val text = "LINUX DO\nLatest\n真诚、友善、团结、专业，共建你我引以为荣之社区。《社区准则》\n佬友七夕快乐"
        val probe = CloudflareChallenge.parsePageProbe("wait::https://linux.do/::$text")
        assertFalse(probe.ready)
        assertTrue(CloudflareChallenge.looksLikePassedHome(text))
        assertFalse(CloudflareChallenge.dialogMayClose(probe))
        assertFalse(CloudflareChallenge.looksLikePassedHome("LINUX DO 登录"))
        assertFalse(
            CloudflareChallenge.dialogMayClose(
                CloudflareChallenge.parsePageProbe("wait::https://linux.do/::LINUX DO 登录"),
            ),
        )
        val leftover = CloudflareChallenge.parsePageProbe("turnstile::https://linux.do/::$text")
        assertFalse(CloudflareChallenge.dialogMayClose(leftover))
        assertFalse(CloudflareChallenge.isCsrfPassPayload("Just a moment... csrf challenge {"))
        assertTrue(CloudflareChallenge.isCsrfPassPayload("""{"csrf":"x"}"""))
        assertTrue(
            CloudflareChallenge.dialogMayClose(
                CloudflareChallenge.parsePageProbe("passed::https://linux.do/::{\"csrf\":\"x\"}"),
            ),
        )
    }

    @Test
    fun `cf_chl_opt keeps the interstitial from counting as the community shell`() {
        assertTrue(CloudflareChallenge.hasActiveChallenge("<script>window.cf_chl_opt={}</script>"))
        assertFalse(
            CloudflareChallenge.isCommunityShell(
                "https://linux.do/",
                ready = true,
                text = "<html id=\"site-logo\">cf_chl_opt</html>",
            ),
        )
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

    @Test
    fun `session current json is an expected payload`() {
        assertTrue(
            CloudflareChallenge.isExpectedPayload(
                "https://linux.do/session/current.json",
                """{"current_user":{"username":"helper"}}""",
            ),
        )
        assertTrue(
            CloudflareChallenge.isExpectedPayload(
                "https://linux.do/session/current.json",
                """{"current_user":null}""",
            ),
        )
        assertTrue(
            CloudflareChallenge.isExpectedPayload(
                "https://linux.do/session/csrf",
                """{"csrf":"token"}""",
            ),
        )
    }
}

package moe.momokko.intellido.domain.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DiscourseLinksTest {
    @Test
    fun `topic urls with slug id and optional floor`() {
        assertEquals(DiscourseLink.Topic(2810651, null, "draw"), DiscourseLinks.parse("/t/draw/2810651"))
        assertEquals(DiscourseLink.Topic(2810651, 20, "draw"), DiscourseLinks.parse("https://linux.do/t/draw/2810651/20"))
        assertEquals(DiscourseLink.Topic(101, 2, null), DiscourseLinks.parse("/t/101/2"))
        assertEquals(DiscourseLink.Topic(101, null, null), DiscourseLinks.parse("https://www.linux.do/t/101"))
    }

    @Test
    fun `tag category and user urls`() {
        assertEquals(DiscourseLink.Tag("抽奖"), DiscourseLinks.parse("/tag/抽奖"))
        assertEquals(DiscourseLink.Tag("抽奖"), DiscourseLinks.parse("https://linux.do/tag/%E6%8A%BD%E5%A5%96"))
        assertEquals(DiscourseLink.Category(4), DiscourseLinks.parse("/c/develop/4"))
        assertEquals(DiscourseLink.Category(94), DiscourseLinks.parse("/c/resource/cloud-asset/94"))
        assertEquals(DiscourseLink.User("helper"), DiscourseLinks.parse("/u/helper"))
        assertEquals(DiscourseLink.User("helper"), DiscourseLinks.parse("https://linux.do/u/helper/summary"))
    }

    @Test
    fun `foreign hosts are ignored`() {
        assertNull(DiscourseLinks.parse("https://example.com/t/101"))
        assertNull(DiscourseLinks.parse("https://example.com/about"))
    }

    @Test
    fun `canonical topic urls stay on linux do`() {
        assertEquals("https://linux.do/t/welcome-to-intellido/101", DiscourseLinks.canonical(101, "welcome-to-intellido"))
        assertEquals("https://linux.do/t/welcome-to-intellido/101/3", DiscourseLinks.canonical(101, "welcome-to-intellido", 3))
        assertEquals("https://linux.do/t/101", DiscourseLinks.canonical(101))
        assertEquals("https://linux.do/t/101/2", DiscourseLinks.canonical(101, postNumber = 2))
    }

    @Test
    fun `directory group and community pages parse as native links`() {
        assertEquals(DiscourseLink.Directory(DiscourseLink.DirectoryPage.ABOUT), DiscourseLinks.parse("https://linux.do/about"))
        assertEquals(DiscourseLink.Directory(DiscourseLink.DirectoryPage.CATEGORIES), DiscourseLinks.parse("/categories"))
        assertEquals(DiscourseLink.Directory(DiscourseLink.DirectoryPage.TAGS), DiscourseLinks.parse("/tags"))
        assertEquals(DiscourseLink.Directory(DiscourseLink.DirectoryPage.GROUPS), DiscourseLinks.parse("/groups"))
        assertEquals(DiscourseLink.Directory(DiscourseLink.DirectoryPage.BADGES), DiscourseLinks.parse("/badges"))
        assertEquals(DiscourseLink.Directory(DiscourseLink.DirectoryPage.MEMBERS), DiscourseLinks.parse("/u"))
        assertEquals(DiscourseLink.Group("admins"), DiscourseLinks.parse("/g/admins"))
        assertEquals(DiscourseLink.Page("/faq"), DiscourseLinks.parse("https://linux.do/faq"))
        assertEquals(DiscourseLink.Page("/guidelines"), DiscourseLinks.parse("/guidelines"))
        assertEquals(DiscourseLink.Page("/tos"), DiscourseLinks.parse("/tos"))
        assertEquals(DiscourseLink.Page("/privacy"), DiscourseLinks.parse("/privacy"))
    }
}

package moe.momokko.intellido.platform.home

import moe.momokko.intellido.transport.FakeLinuxDoCommunityClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeTopicsControllerTest {
    @Test
    fun `home controller surfaces fake LINUX DO topics for the native list`() {
        val controller = HomeTopicsController(FakeLinuxDoCommunityClient())
        val topics = controller.load()

        assertEquals(listOf("欢迎使用 IntelliDo", "如何阅读话题", "非官方客户端说明"), controller.titles())
        assertEquals(101L, topics.first().id)
        assertEquals("公告", topics.first().categoryName)
        assertEquals(true, topics.first().pinned)
        assertEquals(29_700, topics.first().views)
        assertEquals(listOf("system", "helper"), topics.first().posters.map { it.username })
    }

    @Test
    fun `home controller category filter stays on the fake corpus`() {
        val controller = HomeTopicsController(FakeLinuxDoCommunityClient())
        assertEquals(listOf(101L, 103L), controller.loadCategory(1).map { it.id })
    }

    @Test
    fun `home controller public search stays on the fake corpus`() {
        val controller = HomeTopicsController(FakeLinuxDoCommunityClient())
        assertEquals(listOf("欢迎使用 IntelliDo"), controller.search("欢迎").map { it.title })
        assertEquals(3, controller.search("").size)
    }

    @Test
    fun `hot and top lists stay on the fake corpus`() {
        val controller = HomeTopicsController(FakeLinuxDoCommunityClient())
        assertEquals(listOf(102L, 101L, 106L), controller.loadHot().map { it.id })
        assertEquals(listOf(101L, 103L, 102L), controller.loadTop().map { it.id })
    }

    @Test
    fun `home controller tag filter stays on the fake corpus`() {
        val controller = HomeTopicsController(FakeLinuxDoCommunityClient())
        assertEquals(listOf(102L), controller.loadTag("faq").map { it.id })
    }

    @Test
    fun `home controller appends the next latest page`() {
        val controller = HomeTopicsController(FakeLinuxDoCommunityClient())
        assertEquals(3, controller.load().size)
        assertEquals(listOf(104L, 105L, 106L), controller.loadMore().map { it.id })
        assertEquals(6, controller.snapshot().size)
        assertEquals(emptyList<Long>(), controller.loadMore().map { it.id })
        assertEquals(false, controller.hasMore())
    }

    @Test
    fun `latest bumps accumulate until the list is replaced`() {
        val controller = HomeTopicsController(FakeLinuxDoCommunityClient())
        controller.load()
        controller.noteIncoming(202)
        controller.noteIncoming(202)
        controller.noteIncoming(303)
        assertEquals(2, controller.incomingCount())
        controller.load()
        assertEquals(0, controller.incomingCount())
    }
}

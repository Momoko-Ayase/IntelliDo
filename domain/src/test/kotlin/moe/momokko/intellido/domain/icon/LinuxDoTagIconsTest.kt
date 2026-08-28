package moe.momokko.intellido.domain.icon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LinuxDoTagIconsTest {
    @Test
    fun `known LINUX DO tags map to Font Awesome names`() {
        assertEquals("bullhorn", LinuxDoTagIcons.icon("公告"))
        assertEquals("brain", LinuxDoTagIcons.icon("人工智能"))
        assertEquals("faucet", LinuxDoTagIcons.icon("纯水"))
        assertEquals("tree", LinuxDoTagIcons.icon("树洞"))
        assertEquals("triangle-exclamation", LinuxDoTagIcons.icon("nsfw"))
        assertEquals("file-code", LinuxDoTagIcons.icon("软件开发"))
        assertEquals("lightbulb", LinuxDoTagIcons.icon("原创"))
        assertEquals("shuffle", LinuxDoTagIcons.icon("抽奖"))
        assertEquals("F7941D", LinuxDoTagIcons.color("抽奖"))
        assertEquals("coins", LinuxDoTagIcons.icon("高级推广"))
        assertEquals("F5BF03", LinuxDoTagIcons.color("高级推广"))
        assertNull(LinuxDoTagIcons.icon("chatgpt"))
    }
}

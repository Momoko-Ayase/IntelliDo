package moe.momokko.intellido.domain.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TopicFindTest {
    @Test
    fun `find matches plain text across loaded posts without keeping the query`() {
        val hits = TopicFind.search(
            listOf(
                post(1001, 1, "欢迎使用 IntelliDo。"),
                post(1002, 2, "单击话题会在预览标签页中打开。"),
                post(1003, 3, "第三篇，滚动后才会加载。"),
            ),
            "intellido",
        )
        assertEquals(listOf(1), hits.map { it.postNumber })
        assertTrue(hits.single().snippet.contains("IntelliDo"))
        assertEquals(1001L, hits.single().postId)
    }

    @Test
    fun `blank queries match nothing`() {
        assertTrue(TopicFind.search(listOf(post(1, 1, "IntelliDo")), "  ").isEmpty())
        assertTrue(TopicFind.search(listOf(post(1, 1, "IntelliDo")), "").isEmpty())
    }

    private fun post(id: Long, number: Int, text: String): TopicPost =
        TopicPost(
            id = id,
            postNumber = number,
            username = "system",
            cookedHtml = "<p>$text</p>",
            plainText = text,
            createdAt = Instant.EPOCH,
        )
}

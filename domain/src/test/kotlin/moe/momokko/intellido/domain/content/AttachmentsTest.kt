package moe.momokko.intellido.domain.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttachmentsTest {
    @Test
    fun `cooked attachment anchors become saveable files`() {
        val html = """
            <p>见附件 <a class="attachment" href="/uploads/default/original/1X/abc.pdf">notes.pdf</a>
            和 <a href="https://linux.do/t/101">话题</a>。</p>
        """.trimIndent()
        val found = Attachments.fromCooked(html)
        assertEquals(listOf("notes.pdf"), found.map { it.filename })
        assertEquals("https://linux.do/uploads/default/original/1X/abc.pdf", found.single().url)
        assertTrue(Attachments.isAttachmentUrl(found.single().url))
        assertFalse(Attachments.isAttachmentUrl("https://linux.do/t/101"))
    }

    @Test
    fun `suggested names never execute a path`() {
        assertEquals("notes.pdf", Attachments.suggestedName("https://linux.do/uploads/default/original/1X/abc.pdf", "notes.pdf"))
        assertEquals("abc.bin", Attachments.suggestedName("https://linux.do/uploads/default/original/1X/abc.bin"))
        assertEquals("download", Attachments.suggestedName("https://linux.do/uploads/short-url/xyz"))
    }
}

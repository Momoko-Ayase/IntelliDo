package moe.momokko.intellido.transport

import moe.momokko.intellido.domain.content.CookedBlock
import moe.momokko.intellido.domain.content.CookedDocument
import moe.momokko.intellido.domain.content.CookedHtmlParser
import moe.momokko.intellido.domain.content.CookedSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscourseFormatPreviewTest {
    private val parser = CookedHtmlParser()

    @Test
    fun `format preview is discoverable in the local home corpus`() {
        val client = FakeLinuxDoCommunityClient()
        val secondPage = client.loadHomeTopics(1)

        assertEquals(DiscourseFormatPreview.TOPIC_ID, secondPage.last().id)
        assertTrue(secondPage.last().title.contains("Markdown"))
        val thread = client.loadTopic(DiscourseFormatPreview.TOPIC_ID)
        assertEquals(listOf(1, 2, 3, 4), thread.posts.map { it.postNumber })
        assertTrue(thread.posts.all { it.cookedHtml.isNotBlank() })
    }

    @Test
    fun `markdown and bbcode previews retain authoring source and cooked result`() {
        val thread = FakeLinuxDoCommunityClient().loadTopic(DiscourseFormatPreview.TOPIC_ID)
        val markdown = parser.parse(thread.posts[0].cookedHtml)
        val bbCode = parser.parse(thread.posts[1].cookedHtml)

        assertTrue(markdown.blocks.filterIsInstance<CookedBlock.CodeBlock>().any { "**粗体**" in it.code })
        assertTrue(bbCode.blocks.filterIsInstance<CookedBlock.CodeBlock>().any { "[b]粗体[/b]" in it.code })
        assertContainsBlocks<CookedBlock.Heading>(markdown)
        assertContainsBlocks<CookedBlock.Quote>(markdown)
        assertContainsBlocks<CookedBlock.ListBlock>(markdown)
        assertContainsBlocks<CookedBlock.Table>(markdown)
        assertContainsBlocks<CookedBlock.ThematicBreak>(markdown)
        assertContainsBlocks<CookedBlock.Details>(bbCode)
        assertContainsBlocks<CookedBlock.Spoiler>(bbCode)
        assertContainsBlocks<CookedBlock.Quote>(bbCode)
        assertTrue(allSpans(bbCode).any { it is CookedSpan.Colored })
        assertTrue(allSpans(bbCode).any { it is CookedSpan.Sized })
        assertTrue(allSpans(bbCode).any { it is CookedSpan.Spoiler })
    }

    @Test
    fun `discourse extensions and media previews cover every native block family`() {
        val thread = FakeLinuxDoCommunityClient().loadTopic(DiscourseFormatPreview.TOPIC_ID)
        val extensions = parser.parse(thread.posts[2].cookedHtml)
        val media = parser.parse(thread.posts[3].cookedHtml)

        assertContainsBlocks<CookedBlock.Callout>(extensions)
        assertContainsBlocks<CookedBlock.Details>(extensions)
        assertContainsBlocks<CookedBlock.Onebox>(extensions)
        assertContainsBlocks<CookedBlock.Poll>(extensions)
        assertContainsBlocks<CookedBlock.Math>(extensions)
        assertContainsBlocks<CookedBlock.Policy>(extensions)
        assertContainsBlocks<CookedBlock.ChatTranscript>(extensions)
        assertContainsBlocks<CookedBlock.DefinitionList>(extensions)
        assertContainsBlocks<CookedBlock.Footnotes>(extensions)
        val extensionSpans = allSpans(extensions)
        assertTrue(extensionSpans.any { it is CookedSpan.Link && it.kind == CookedSpan.Link.Kind.Mention })
        assertTrue(extensionSpans.any { it is CookedSpan.Link && it.kind == CookedSpan.Link.Kind.Hashtag })
        assertTrue(extensionSpans.any { it is CookedSpan.Link && it.kind == CookedSpan.Link.Kind.Attachment })
        assertTrue(extensionSpans.any { it is CookedSpan.Emoji })
        assertTrue(extensionSpans.any { it is CookedSpan.Image })
        assertTrue(extensionSpans.any { it is CookedSpan.LocalDate })
        assertTrue(extensionSpans.any { it is CookedSpan.FootnoteRef })

        val everySpan = thread.posts.map { parser.parse(it.cookedHtml) }.flatMap(::allSpans)
        assertEveryInlineFamilyIsPreviewed(everySpan)

        assertContainsBlocks<CookedBlock.Image>(media)
        assertContainsBlocks<CookedBlock.ImageGrid>(media)
        assertContainsBlocks<CookedBlock.Svg>(media)
        val mediaKinds = allBlocks(media).filterIsInstance<CookedBlock.Media>().map { it.kind }.toSet()
        assertTrue(mediaKinds.containsAll(CookedBlock.Media.Kind.entries), mediaKinds.toString())

        (listOf(extensions, media) + thread.posts.take(2).map { parser.parse(it.cookedHtml) }).forEach { document ->
            assertTrue(document.blocks.isNotEmpty())
            assertFalse(allBlocks(document).any { it is CookedBlock.Unknown }, document.plainText)
        }
    }

    private inline fun <reified T : CookedBlock> assertContainsBlocks(document: CookedDocument) {
        assertTrue(allBlocks(document).any { it is T }, "missing ${T::class.simpleName}: ${document.plainText}")
    }

    private fun allBlocks(document: CookedDocument): Sequence<CookedBlock> =
        document.blocks.asSequence().flatMap(::walkBlock)

    private fun walkBlock(block: CookedBlock): Sequence<CookedBlock> = sequence {
        yield(block)
        val children = when (block) {
            is CookedBlock.Quote -> block.children
            is CookedBlock.Details -> block.children
            is CookedBlock.Spoiler -> block.children
            is CookedBlock.Callout -> block.children
            is CookedBlock.Policy -> block.children
            is CookedBlock.ChatTranscript -> block.children
            is CookedBlock.Unknown -> block.children
            is CookedBlock.ListBlock -> block.items.flatten()
            is CookedBlock.DefinitionList -> block.items.flatMap { it.definitions.flatten() }
            else -> emptyList()
        }
        children.forEach { yieldAll(walkBlock(it)) }
    }

    private fun allSpans(document: CookedDocument): List<CookedSpan> = buildList {
        fun addSpans(spans: List<CookedSpan>) {
            spans.forEach { span ->
                add(span)
                when (span) {
                    is CookedSpan.Strong -> addSpans(span.children)
                    is CookedSpan.Emphasis -> addSpans(span.children)
                    is CookedSpan.Strike -> addSpans(span.children)
                    is CookedSpan.Underline -> addSpans(span.children)
                    is CookedSpan.Highlight -> addSpans(span.children)
                    is CookedSpan.Subscript -> addSpans(span.children)
                    is CookedSpan.Superscript -> addSpans(span.children)
                    is CookedSpan.Colored -> addSpans(span.children)
                    is CookedSpan.Sized -> addSpans(span.children)
                    is CookedSpan.Link -> addSpans(span.children)
                    is CookedSpan.Spoiler -> addSpans(span.children)
                    is CookedSpan.FootnoteRef -> addSpans(span.content)
                    else -> Unit
                }
            }
        }
        allBlocks(document).forEach { block ->
            when (block) {
                is CookedBlock.Paragraph -> addSpans(block.spans)
                is CookedBlock.Heading -> addSpans(block.spans)
                is CookedBlock.Table -> block.rows.flatMap { it.cells }.forEach { addSpans(it.spans) }
                is CookedBlock.Footnotes -> block.entries.forEach { addSpans(it.spans) }
                is CookedBlock.DefinitionList -> block.items.forEach { addSpans(it.term) }
                else -> Unit
            }
        }
    }

    private fun assertEveryInlineFamilyIsPreviewed(spans: List<CookedSpan>) {
        val checks: List<Pair<String, (CookedSpan) -> Boolean>> = listOf(
            "Text" to { it is CookedSpan.Text },
            "Break" to { it is CookedSpan.Break },
            "Strong" to { it is CookedSpan.Strong },
            "Emphasis" to { it is CookedSpan.Emphasis },
            "Strike" to { it is CookedSpan.Strike },
            "Underline" to { it is CookedSpan.Underline },
            "Highlight" to { it is CookedSpan.Highlight },
            "Subscript" to { it is CookedSpan.Subscript },
            "Superscript" to { it is CookedSpan.Superscript },
            "Colored" to { it is CookedSpan.Colored },
            "Sized" to { it is CookedSpan.Sized },
            "Code" to { it is CookedSpan.Code },
            "Kbd" to { it is CookedSpan.Kbd },
            "Link" to { it is CookedSpan.Link },
            "Image" to { it is CookedSpan.Image },
            "Emoji" to { it is CookedSpan.Emoji },
            "Spoiler" to { it is CookedSpan.Spoiler },
            "FootnoteRef" to { it is CookedSpan.FootnoteRef },
            "LocalDate" to { it is CookedSpan.LocalDate },
            "Math" to { it is CookedSpan.Math },
            "ClickCount" to { it is CookedSpan.ClickCount },
        )
        checks.forEach { (name, predicate) ->
            assertTrue(spans.any(predicate), "local preview is missing inline family $name")
        }
        CookedSpan.Link.Kind.entries.forEach { kind ->
            assertTrue(
                spans.any { it is CookedSpan.Link && it.kind == kind },
                "local preview is missing link kind $kind",
            )
        }
    }
}

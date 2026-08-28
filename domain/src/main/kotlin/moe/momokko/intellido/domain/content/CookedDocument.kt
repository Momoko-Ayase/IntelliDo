package moe.momokko.intellido.domain.content

data class CookedDocument(
    val blocks: List<CookedBlock>,
) {
    val plainText: String
        get() = blocks.joinToString("\n") { it.plainText() }.trim()
}

sealed class CookedBlock {
    data class Paragraph(
        val spans: List<CookedSpan>,
        val alignment: CookedAlignment = CookedAlignment.Start,
    ) : CookedBlock()
    data class Heading(
        val level: Int,
        val spans: List<CookedSpan>,
        val alignment: CookedAlignment = CookedAlignment.Start,
        val anchor: String? = null,
    ) : CookedBlock()
    data class Quote(
        val children: List<CookedBlock>,
        val attribution: String? = null,
        val attributionHref: String? = null,
    ) : CookedBlock()
    data class ListBlock(
        val ordered: Boolean,
        val items: List<List<CookedBlock>>,
        val start: Int = 1,
    ) : CookedBlock()
    data class CodeBlock(val language: String?, val code: String) : CookedBlock()
    data class Image(
        val src: String,
        val alt: String,
        val trusted: Boolean,
        val originalSrc: String = src,
        val width: Int? = null,
        val height: Int? = null,
        val caption: String? = null,
    ) : CookedBlock() {
        val original: String get() = originalSrc.ifBlank { MediaUrls.original(src) }
    }
    data class ImageGrid(
        val images: List<Image>,
        val columns: Int = 2,
        val carousel: Boolean = false,
    ) : CookedBlock()
    data class Table(val rows: List<TableRow>) : CookedBlock()
    data class Onebox(
        val url: String,
        val title: String,
        val description: String,
        val site: String,
        val thumbnail: String = "",
        val kind: String = "generic",
    ) : CookedBlock()
    data class Details(
        val summary: String,
        val children: List<CookedBlock>,
        val initiallyOpen: Boolean = false,
    ) : CookedBlock()
    data class Spoiler(val children: List<CookedBlock>) : CookedBlock()
    data class Poll(
        val name: String,
        val options: List<String>,
        val title: String? = null,
        val status: String? = null,
        val multiple: Boolean = false,
    ) : CookedBlock()
    data class Callout(
        val kind: Kind,
        val title: String,
        val children: List<CookedBlock>,
        val collapsible: Boolean = false,
        val initiallyOpen: Boolean = true,
    ) : CookedBlock() {
        enum class Kind { Note, Tip, Important, Warning, Danger, Info, Success, Question, Quote, Unknown }
    }
    data class Media(
        val kind: Kind,
        val src: String,
        val title: String = "",
        val poster: String = "",
        val originalSrc: String = src,
        val mime: String? = null,
        val trusted: Boolean = false,
        val width: Int? = null,
        val height: Int? = null,
    ) : CookedBlock() {
        enum class Kind { Audio, Voice, Video, Embed }
    }
    data class Math(val latex: String) : CookedBlock()
    data class Footnotes(val entries: List<FootnoteEntry>) : CookedBlock()
    data class Policy(
        val children: List<CookedBlock>,
        val version: String? = null,
        val groups: String? = null,
    ) : CookedBlock()
    data class ChatTranscript(
        val username: String,
        val children: List<CookedBlock>,
        val channel: String? = null,
        val dateTime: String? = null,
        val chained: Boolean = false,
    ) : CookedBlock()
    data class DefinitionList(val items: List<DefinitionItem>) : CookedBlock()
    data class Svg(
        val source: String,
        val width: Int? = null,
        val height: Int? = null,
        val title: String = "SVG",
    ) : CookedBlock()
    data object ThematicBreak : CookedBlock()
    data class Unknown(
        val tag: String,
        val children: List<CookedBlock> = emptyList(),
    ) : CookedBlock()
}

enum class CookedAlignment { Start, Center, End }

data class FootnoteEntry(
    val id: String,
    val number: String,
    val spans: List<CookedSpan>,
)

data class DefinitionItem(
    val term: List<CookedSpan>,
    val definitions: List<List<CookedBlock>>,
)

data class TableRow(
    val header: Boolean,
    val cells: List<TableCell>,
)

data class TableCell(
    val spans: List<CookedSpan>,
    val header: Boolean = false,
)

sealed class CookedSpan {
    data class Text(val text: String) : CookedSpan()
    data object Break : CookedSpan()
    data class Strong(val children: List<CookedSpan>) : CookedSpan()
    data class Emphasis(val children: List<CookedSpan>) : CookedSpan()
    data class Strike(val children: List<CookedSpan>) : CookedSpan()
    data class Underline(val children: List<CookedSpan>) : CookedSpan()
    data class Highlight(val children: List<CookedSpan>) : CookedSpan()
    data class Subscript(val children: List<CookedSpan>) : CookedSpan()
    data class Superscript(val children: List<CookedSpan>) : CookedSpan()
    data class Colored(
        val color: String = "",
        val children: List<CookedSpan>,
        val background: String = "",
    ) : CookedSpan()
    data class Sized(val scale: Float, val children: List<CookedSpan>) : CookedSpan()
    data class Code(val text: String) : CookedSpan()
    data class Kbd(val text: String) : CookedSpan()
    data class Link(
        val href: String,
        val children: List<CookedSpan>,
        val kind: Kind = Kind.Regular,
    ) : CookedSpan() {
        enum class Kind { Regular, Mention, Hashtag, Attachment }
    }
    data class Image(
        val src: String,
        val alt: String,
        val trusted: Boolean,
        val originalSrc: String = src,
        val width: Int? = null,
        val height: Int? = null,
    ) : CookedSpan() {
        val original: String get() = originalSrc.ifBlank { MediaUrls.original(src) }
    }
    data class Emoji(val shortcode: String, val glyph: String, val src: String = "") : CookedSpan() {
        val needsImage: Boolean get() = src.isNotBlank()
    }
    data class Spoiler(val children: List<CookedSpan>) : CookedSpan()
    data class FootnoteRef(
        val number: String,
        val id: String,
        val content: List<CookedSpan> = emptyList(),
    ) : CookedSpan()
    data class LocalDate(
        val text: String,
        val date: String,
        val time: String? = null,
        val timezone: String? = null,
        val countdown: Boolean = false,
    ) : CookedSpan()
    data class Math(val latex: String) : CookedSpan()
    data class ClickCount(val text: String) : CookedSpan()
}

internal fun CookedBlock.plainText(): String = when (this) {
    is CookedBlock.Paragraph -> spans.plainText()
    is CookedBlock.Heading -> spans.plainText()
    is CookedBlock.Quote -> {
        val body = children.joinToString("\n") { it.plainText() }
        if (attribution.isNullOrBlank()) body else "$attribution\n$body"
    }
    is CookedBlock.ListBlock -> items.joinToString("\n") { item -> item.joinToString(" ") { it.plainText() } }
    is CookedBlock.CodeBlock -> code
    is CookedBlock.Image -> alt
    is CookedBlock.ImageGrid -> images.joinToString("\n") { it.alt }
    is CookedBlock.Table -> rows.joinToString("\n") { row ->
        row.cells.joinToString("\t") { it.spans.plainText() }
    }
    is CookedBlock.Onebox -> listOf(title, description).filter { it.isNotBlank() }.joinToString("\n")
    is CookedBlock.Details -> summary
    is CookedBlock.Spoiler -> ""
    is CookedBlock.Poll -> options.joinToString("\n")
    is CookedBlock.Callout -> listOf(title, children.joinToString("\n") { it.plainText() })
        .filter { it.isNotBlank() }.joinToString("\n")
    is CookedBlock.Media -> title.ifBlank { src }
    is CookedBlock.Math -> latex
    is CookedBlock.Footnotes -> entries.joinToString("\n") { "${it.number}. ${it.spans.plainText()}" }
    is CookedBlock.Policy -> children.joinToString("\n") { it.plainText() }
    is CookedBlock.ChatTranscript -> children.joinToString("\n") { it.plainText() }
    is CookedBlock.DefinitionList -> items.joinToString("\n") { item ->
        listOf(
            item.term.plainText(),
            item.definitions.flatten().joinToString(" ") { it.plainText() },
        ).filter { it.isNotBlank() }.joinToString(": ")
    }
    is CookedBlock.Svg -> title
    is CookedBlock.ThematicBreak -> ""
    is CookedBlock.Unknown -> children.joinToString("\n") { it.plainText() }
}

internal fun List<CookedSpan>.plainText(): String = joinToString("") { it.plainText() }

internal fun CookedSpan.plainText(): String = when (this) {
    is CookedSpan.Text -> text
    is CookedSpan.Break -> "\n"
    is CookedSpan.Strong -> children.plainText()
    is CookedSpan.Emphasis -> children.plainText()
    is CookedSpan.Strike -> children.plainText()
    is CookedSpan.Underline -> children.plainText()
    is CookedSpan.Highlight -> children.plainText()
    is CookedSpan.Subscript -> children.plainText()
    is CookedSpan.Superscript -> children.plainText()
    is CookedSpan.Colored -> children.plainText()
    is CookedSpan.Sized -> children.plainText()
    is CookedSpan.Code -> text
    is CookedSpan.Kbd -> text
    is CookedSpan.Link -> children.plainText()
    is CookedSpan.Image -> alt
    is CookedSpan.Emoji -> glyph.ifBlank { ":$shortcode:" }
    is CookedSpan.Spoiler -> ""
    is CookedSpan.FootnoteRef -> number
    is CookedSpan.LocalDate -> text
    is CookedSpan.Math -> latex
    is CookedSpan.ClickCount -> text
}

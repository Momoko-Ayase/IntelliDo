package moe.momokko.intellido.domain.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI

/** Parses Discourse cooked HTML into the native, inert document model. */
class CookedHtmlParser(
    private val trustedMediaHosts: Set<String> = DEFAULT_TRUSTED_MEDIA_HOSTS,
) {
    fun parse(html: String): CookedDocument = NativeCookedParser(trustedMediaHosts).parse(html)

    companion object {
        val DEFAULT_TRUSTED_MEDIA_HOSTS: Set<String> = setOf(
            "linux.do",
            "cdn.linux.do",
            "uploads.linux.do",
            "emoji.discourse-cdn.com",
            "ldstatic.com",
            "cdn.ldstatic.com",
            "cdn3.ldstatic.com",
            "intellido.test",
        )
    }
}

private class NativeCookedParser(
    private val trustedMediaHosts: Set<String>,
) {
    private var footnotes: Map<String, List<CookedSpan>> = emptyMap()

    fun parse(html: String): CookedDocument {
        if (html.isBlank()) return CookedDocument(emptyList())
        val body = Jsoup.parseBodyFragment(html, BASE_URL).body()
        footnotes = collectFootnotes(body)
        return CookedDocument(parseBlocks(body.childNodes(), 0))
    }

    private fun parseBlocks(nodes: List<Node>, depth: Int): List<CookedBlock> {
        if (depth > MAX_DEPTH) {
            val text = nodes.joinToString(" ") { it.toString() }.replace(TAGS, " ").collapseWhitespace().trim()
            return if (text.isBlank()) emptyList() else listOf(CookedBlock.Paragraph(listOf(CookedSpan.Text(text))))
        }
        val blocks = mutableListOf<CookedBlock>()
        val pending = mutableListOf<CookedSpan>()
        var inlineBridge = false
        fun flush() {
            val spans = normalizeSpans(pending, trimEdges = true)
            if (spans.isNotEmpty()) {
                val previous = blocks.lastOrNull() as? CookedBlock.Paragraph
                if (previous != null && isInlineBridge(spans)) {
                    blocks[blocks.lastIndex] = previous.copy(spans = joinSpans(previous.spans, spans))
                    inlineBridge = true
                } else {
                    blocks += splitImages(spans)
                    inlineBridge = false
                }
            }
            pending.clear()
        }

        nodes.forEach { node ->
            when (node) {
                is TextNode -> {
                    val text = node.wholeText.collapseWhitespace()
                    if (text.isNotBlank()) pending += CookedSpan.Text(text)
                }
                is Element -> {
                    val tag = node.normalName()
                    if (shouldSkip(node)) return@forEach
                    if (tag in INLINE_TAGS || isInlineSemantic(node)) {
                        pending += parseInline(node, depth + 1)
                    } else {
                        flush()
                        val parsed = parseBlock(node, depth + 1)
                        val previous = blocks.lastOrNull() as? CookedBlock.Paragraph
                        val first = parsed.firstOrNull() as? CookedBlock.Paragraph
                        if (inlineBridge && previous != null && first != null) {
                            blocks[blocks.lastIndex] = previous.copy(spans = joinSpans(previous.spans, first.spans))
                            blocks += parsed.drop(1)
                        } else {
                            blocks += parsed
                        }
                        inlineBridge = false
                    }
                }
            }
        }
        flush()
        return blocks.filterNot { it is CookedBlock.Paragraph && it.spans.isEmpty() }
    }

    private fun parseBlock(element: Element, depth: Int): List<CookedBlock> {
        val tag = element.normalName()
        val classes = element.classNames()

        if (tag == "div" && element.hasClass("lazy-video-container")) return listOf(parseLazyVideo(element))
        if (tag == "div" && element.hasClass("policy")) return listOf(parsePolicy(element, depth))
        if (tag == "div" && element.hasClass("math")) return mathBlock(element)
        if (tag == "div" && element.hasClass("poll")) return listOf(parsePoll(element))
        if (tag == "div" && element.hasClass("chat-transcript")) return listOf(parseChat(element, depth))
        if (tag == "div" && element.hasClass("d-image-grid")) return listOf(parseImageGrid(element))
        if (tag == "div" && element.hasClass("d-wrap") && element.attr("data-wrap") == "voice") {
            return listOf(parseAudio(element, voice = true))
        }
        if (tag == "div" && classes.any { it in VIDEO_CONTAINER_CLASSES }) return listOf(parseVideo(element))
        if (tag == "div" && (element.hasClass("spoiler") || element.hasClass("spoiled"))) {
            return listOf(CookedBlock.Spoiler(parseBlocks(element.childNodes(), depth)))
        }
        if ((tag == "div" || tag == "aside") && classes.any { it.startsWith("callout") }) {
            parseClassCallout(element, depth)?.let { return listOf(it) }
        }

        return when (tag) {
            "p" -> parseParagraph(element, depth)
            in HEADING_TAGS -> listOf(
                CookedBlock.Heading(
                    level = tag.drop(1).toInt(),
                    spans = parseInlineNodes(element.childNodes(), depth),
                    alignment = alignment(element),
                    anchor = element.selectFirst("a.anchor")?.attr("name")?.ifBlank { null }
                        ?: element.selectFirst("a.anchor")?.attr("id")?.ifBlank { null },
                ),
            )
            "blockquote" -> listOf(parseCallout(element, depth) ?: CookedBlock.Quote(parseBlocks(element.childNodes(), depth)))
            "ul", "ol" -> {
                if (element.hasClass("footnotes-list")) listOf(parseFootnotes(element))
                else listOf(parseList(element, tag == "ol", depth))
            }
            "dl" -> listOfNotNull(parseDefinitionList(element, depth))
            "pre" -> listOf(parseCode(element))
            "hr" -> if (element.hasClass("footnotes-sep")) emptyList() else listOf(CookedBlock.ThematicBreak)
            "aside" -> when {
                element.hasClass("quote") -> listOf(parseQuoteCard(element, depth))
                element.hasClass("onebox") || classes.any { it.endsWith("-onebox") } -> listOf(parseOnebox(element))
                else -> transparentOrUnknown(element, depth)
            }
            "details" -> listOf(parseDetails(element, depth))
            "section" -> if (element.hasClass("footnotes")) listOf(parseFootnotes(element)) else transparentOrUnknown(element, depth)
            "table" -> listOfNotNull(parseTable(element, depth))
            "figure" -> parseFigure(element)
            "picture" -> parsePicture(element)
            "img" -> listOf(imageBlock(element))
            "iframe" -> listOf(parseIframe(element))
            "video" -> listOf(parseVideo(element))
            "audio" -> listOf(parseAudio(element))
            "svg" -> parseSvg(element)
            "div", "article", "main", "header", "footer", "nav", "center", "thead", "tbody", "tfoot" ->
                parseBlocks(element.childNodes(), depth).map { inheritAlignment(it, alignment(element)) }
            "script", "style", "template", "noscript" -> emptyList()
            else -> {
                val children = parseBlocks(element.childNodes(), depth)
                val fallback = if (children.isEmpty()) {
                    val spans = parseInlineNodes(element.childNodes(), depth)
                    if (spans.isEmpty()) emptyList() else listOf(CookedBlock.Paragraph(spans))
                } else {
                    children
                }
                if (fallback.isEmpty()) emptyList() else listOf(CookedBlock.Unknown(tag, fallback))
            }
        }
    }

    private fun parseParagraph(element: Element, depth: Int): List<CookedBlock> {
        val result = mutableListOf<CookedBlock>()
        val pending = mutableListOf<CookedSpan>()
        fun flush() {
            val spans = normalizeSpans(pending, trimEdges = true)
            if (spans.isNotEmpty()) {
                splitImages(spans, alignment(element)).forEach { result += it }
            }
            pending.clear()
        }
        element.childNodes().forEach { child ->
            val media = (child as? Element)?.let { mediaBlock(it) }
            if (media != null) {
                flush()
                result += media
            } else {
                pending += parseInlineNode(child, depth)
            }
        }
        flush()
        return result
    }

    private fun splitImages(
        spans: List<CookedSpan>,
        alignment: CookedAlignment = CookedAlignment.Start,
    ): List<CookedBlock> {
        val result = mutableListOf<CookedBlock>()
        val pending = mutableListOf<CookedSpan>()
        fun flush() {
            val normalized = normalizeSpans(pending, trimEdges = true)
            if (normalized.isNotEmpty()) result += CookedBlock.Paragraph(normalized, alignment)
            pending.clear()
        }
        spans.forEach { span ->
            if (span is CookedSpan.Image && !isInlineGraphic(span)) {
                flush()
                result += CookedBlock.Image(
                    span.src,
                    span.alt,
                    span.trusted,
                    span.original,
                    span.width,
                    span.height,
                )
            } else {
                pending += span
            }
        }
        flush()
        return result
    }

    private fun parseInlineNodes(nodes: List<Node>, depth: Int): List<CookedSpan> =
        normalizeSpans(nodes.flatMap { parseInlineNode(it, depth) }, trimEdges = true)

    private fun parseInlineNode(node: Node, depth: Int): List<CookedSpan> = when (node) {
        is TextNode -> node.wholeText.collapseWhitespace().takeIf { it.isNotEmpty() }
            ?.let { listOf(CookedSpan.Text(it)) }.orEmpty()
        is Element -> parseInline(node, depth + 1)
        else -> emptyList()
    }

    private fun parseInline(element: Element, depth: Int): List<CookedSpan> {
        if (depth > MAX_DEPTH || shouldSkip(element)) return emptyList()
        val tag = element.normalName()
        if (tag == "img") return listOf(parseImage(element))
        if (tag == "br") return listOf(CookedSpan.Break)
        if (tag == "input" && element.attr("type").equals("checkbox", true)) {
            return listOf(CookedSpan.Text(if (element.hasAttr("checked")) "☑" else "☐"))
        }
        if (tag == "code") return listOf(CookedSpan.Code(element.wholeText()))
        if (tag == "div" && element.hasClass("lightbox-wrapper")) {
            return element.selectFirst("a.lightbox, img")?.let { parseInline(it, depth) }.orEmpty()
        }

        val children = normalizeSpans(element.childNodes().flatMap { parseInlineNode(it, depth) }, trimEdges = false)
        return when (tag) {
            "strong", "b" -> listOf(CookedSpan.Strong(children))
            "em", "i", "cite", "dfn", "var" -> listOf(CookedSpan.Emphasis(children))
            "s", "del", "strike" -> listOf(CookedSpan.Strike(children))
            "u", "ins" -> listOf(CookedSpan.Underline(children))
            "mark" -> listOf(CookedSpan.Highlight(children))
            "sub" -> listOf(CookedSpan.Subscript(children))
            "sup" -> parseSuperscript(element, children)
            "kbd", "samp", "tt" -> listOf(CookedSpan.Kbd(children.plainText()))
            "small" -> listOf(CookedSpan.Sized(0.85f, children))
            "big" -> listOf(CookedSpan.Sized(1.2f, children))
            "a" -> parseAnchor(element, children)
            "span" -> parseSpan(element, children)
            "font" -> parseFont(element, children)
            "q" -> listOf(CookedSpan.Text("“")) + children + CookedSpan.Text("”")
            "ruby" -> children
            "rt" -> listOf(CookedSpan.Text("（")) + children + CookedSpan.Text("）")
            "svg", "source", "button", "form", "script", "style" -> emptyList()
            else -> children
        }
    }

    private fun parseAnchor(element: Element, children: List<CookedSpan>): List<CookedSpan> {
        if (element.hasClass("anchor") && children.isEmpty()) return emptyList()
        val rawHref = element.attr("href")
        val href = allowedHref(rawHref).ifBlank { allowedHref(element.attr("data-orig-href")) }
        if (element.hasClass("lightbox")) {
            val image = children.filterIsInstance<CookedSpan.Image>().firstOrNull()
            if (image != null) {
                val original = absolutizeMedia(href).takeIf { it.isNotBlank() } ?: image.original
                return listOf(image.copy(originalSrc = original))
            }
        }
        if (href.isBlank()) return children
        if (element.hasClass("mention") || element.hasClass("mention-group")) {
            val visible = children.plainText().trim()
            val label = if (visible.startsWith("@")) visible else "@$visible"
            return listOf(CookedSpan.Link(href, listOf(CookedSpan.Text(label)), CookedSpan.Link.Kind.Mention))
        }
        val kind = when {
            element.hasClass("hashtag-cooked") || element.hasClass("hashtag") -> CookedSpan.Link.Kind.Hashtag
            element.hasClass("attachment") -> CookedSpan.Link.Kind.Attachment
            else -> CookedSpan.Link.Kind.Regular
        }
        val link = CookedSpan.Link(href, children.ifEmpty { listOf(CookedSpan.Text(href)) }, kind)
        val clicks = element.attr("data-clicks").trim()
            .takeIf { it.length <= 16 && it.matches(CLICK_COUNT_VALUE) }
            ?.let { CookedSpan.ClickCount(it) }
        return if (clicks == null) listOf(link) else listOf(link, clicks)
    }

    private fun parseSpan(element: Element, children: List<CookedSpan>): List<CookedSpan> {
        val classes = element.classNames()
        if ("button-wrapper" in classes || "d-icon" in classes) return emptyList()
        if ("chcklst-box" in classes) {
            val mark = if ("checked" in classes) "☑" else "☐"
            return listOf(CookedSpan.Text(mark))
        }
        if ("spoiler" in classes || "spoiled" in classes) return listOf(CookedSpan.Spoiler(children))
        if ("bbcode-u" in classes) return listOf(CookedSpan.Underline(children))
        if ("bbcode-s" in classes) return listOf(CookedSpan.Strike(children))
        if ("bbcode-b" in classes) return listOf(CookedSpan.Strong(children))
        if ("bbcode-i" in classes) return listOf(CookedSpan.Emphasis(children))
        if ("discourse-local-date" in classes) {
            val date = element.attr("data-date")
            if (date.isNotBlank()) {
                return listOf(
                    CookedSpan.LocalDate(
                        text = element.text().ifBlank { date },
                        date = date,
                        time = element.attr("data-time").ifBlank { null },
                        timezone = element.attr("data-timezone").ifBlank { null },
                        countdown = element.hasAttr("data-countdown"),
                    ),
                )
            }
        }
        if ("click-count" in classes) return listOf(CookedSpan.ClickCount(element.text().trim()))
        if ("math" in classes) return listOf(CookedSpan.Math(element.text().trim()))
        if ("mark" in classes) return listOf(CookedSpan.Highlight(children))

        val style = safeStyle(element.attr("style"))
        val color = cssProperty(style, "color")?.takeIf(::allowedCssColor).orEmpty()
        val background = cssProperty(style, "background-color")?.takeIf(::allowedCssColor).orEmpty()
        val scale = cssProperty(style, "font-size")
            ?.trim()?.takeIf { it.endsWith('%') }?.dropLast(1)?.toFloatOrNull()?.div(100f)
            ?.coerceIn(0.5f, 3f)
        var result: List<CookedSpan> = if (scale != null) listOf(CookedSpan.Sized(scale, children)) else children
        if (color.isNotBlank() || background.isNotBlank()) {
            result = listOf(CookedSpan.Colored(color, result, background))
        }
        return result
    }

    private fun parseFont(element: Element, children: List<CookedSpan>): List<CookedSpan> {
        val color = element.attr("color").takeIf(::allowedCssColor).orEmpty()
        if (color.isNotBlank()) return listOf(CookedSpan.Colored(color, children))
        return if (element.attr("face").contains("mono", true)) {
            listOf(CookedSpan.Code(children.plainText()))
        } else {
            children
        }
    }

    private fun parseSuperscript(element: Element, children: List<CookedSpan>): List<CookedSpan> {
        if (element.hasClass("footnote-ref")) {
            val anchor = element.selectFirst("a")
            val number = anchor?.text()?.trim()?.trim('[', ']').orEmpty()
            val id = anchor?.attr("href")?.removePrefix("#").orEmpty()
            if (number.isNotBlank() && id.isNotBlank()) {
                return listOf(CookedSpan.FootnoteRef(number, id, footnotes[id].orEmpty()))
            }
        }
        return listOf(CookedSpan.Superscript(children))
    }

    private fun parseImage(element: Element): CookedSpan {
        val src = absolutizeMedia(imageSource(element))
        if (element.hasClass("emoji") || isEmojiAlt(element.attr("alt"))) {
            val raw = element.attr("title").ifBlank { element.attr("alt") }
            val code = EmojiShortcodes.shortcode(raw, src) ?: raw.trim(':').ifBlank { "emoji" }
            val glyph = EmojiShortcodes.glyph(raw, src).orEmpty()
            return CookedSpan.Emoji(code, glyph, src)
        }
        val original = element.attr("data-orig-src").takeIf { it.isNotBlank() }?.let(::absolutizeMedia)
            ?: MediaUrls.original(src)
        return CookedSpan.Image(
            src = src,
            alt = element.attr("alt"),
            trusted = isTrustedMedia(src),
            originalSrc = original,
            width = dimension(element, "width"),
            height = dimension(element, "height"),
        )
    }

    private fun imageBlock(element: Element, caption: String? = null): CookedBlock.Image {
        val image = parseImage(element)
        if (image is CookedSpan.Image) {
            return CookedBlock.Image(
                image.src,
                image.alt,
                image.trusted,
                image.original,
                image.width,
                image.height,
                caption,
            )
        }
        return CookedBlock.Image("", image.plainText(), false, caption = caption)
    }

    private fun parseList(element: Element, ordered: Boolean, depth: Int): CookedBlock.ListBlock {
        val items = element.children().filter { it.normalName() == "li" }.map { li ->
            parseBlocks(li.childNodes(), depth)
        }
        return CookedBlock.ListBlock(
            ordered = ordered,
            items = items,
            start = element.attr("start").toIntOrNull()?.takeIf { it > 0 } ?: 1,
        )
    }

    private fun parseCode(element: Element): CookedBlock.CodeBlock {
        val code = element.selectFirst("code") ?: element
        val language = code.classNames().firstOrNull { it.startsWith("lang-") || it.startsWith("language-") }
            ?.substringAfter('-')?.lowercase()
        return CookedBlock.CodeBlock(language, code.wholeText().trimEnd('\n', '\r'))
    }

    private fun parseQuoteCard(element: Element, depth: Int): CookedBlock.Quote {
        val quote = element.children().firstOrNull { it.normalName() == "blockquote" }
        val title = element.selectFirst(".quote-title__text-content, .title")
        val username = element.attr("data-username").ifBlank {
            title?.text()?.trim()?.trimEnd(':').orEmpty()
        }
        val href = title?.selectFirst("a")?.attr("href")?.let(::allowedHref)?.ifBlank { null }
        return CookedBlock.Quote(
            children = quote?.let { parseBlocks(it.childNodes(), depth) }.orEmpty(),
            attribution = username.ifBlank { null },
            attributionHref = href,
        )
    }

    private fun parseOnebox(element: Element): CookedBlock.Onebox {
        val declared = allowedHref(element.attr("data-onebox-src"))
        val titleNode = element.selectFirst("h1 a, h2 a, h3 a, h4 a, article a")
        val sourceNode = element.selectFirst("header.source a, .source a")
        val url = declared.ifBlank { allowedHref(titleNode?.attr("href").orEmpty()) }
            .ifBlank { allowedHref(sourceNode?.attr("href").orEmpty()) }
        val title = titleNode?.text()?.trim().orEmpty().ifBlank { url }
        val description = element.selectFirst("article p, .onebox-body p")?.text()?.trim().orEmpty()
        val site = sourceNode?.text()?.trim().orEmpty().ifBlank { hostOf(url) }
        val thumbnail = element.selectFirst("img.thumbnail, .aspect-image img")?.let(::imageSource)?.let(::absolutizeMedia).orEmpty()
        val kind = element.classNames().firstOrNull { it.endsWith("-onebox") }?.removeSuffix("-onebox") ?: "generic"
        return CookedBlock.Onebox(url, title, description, site, thumbnail, kind)
    }

    private fun parseDetails(element: Element, depth: Int): CookedBlock.Details {
        val summaryNode = element.children().firstOrNull { it.normalName() == "summary" }
        val bodyNodes = element.childNodes().filterNot { it === summaryNode }
        return CookedBlock.Details(
            summary = summaryNode?.text()?.trim().orEmpty().ifBlank { "详细内容" },
            children = parseBlocks(bodyNodes, depth),
            initiallyOpen = element.hasAttr("open"),
        )
    }

    private fun parseTable(element: Element, depth: Int): CookedBlock.Table? {
        val rows = element.select("tr").mapNotNull { row ->
            val cells = row.children().filter { it.normalName() == "th" || it.normalName() == "td" }.map { cell ->
                TableCell(
                    spans = parseInlineNodes(cell.childNodes(), depth),
                    header = cell.normalName() == "th",
                )
            }
            cells.takeIf { it.isNotEmpty() }?.let { TableRow(it.any(TableCell::header), it) }
        }
        return rows.takeIf { it.isNotEmpty() }?.let(CookedBlock::Table)
    }

    private fun parseDefinitionList(element: Element, depth: Int): CookedBlock.DefinitionList? {
        val items = mutableListOf<DefinitionItem>()
        var term: List<CookedSpan>? = null
        var definitions = mutableListOf<List<CookedBlock>>()
        fun flush() {
            val current = term ?: return
            items += DefinitionItem(current, definitions.toList())
            term = null
            definitions = mutableListOf()
        }
        element.children().forEach { child ->
            when (child.normalName()) {
                "dt" -> {
                    flush()
                    term = parseInlineNodes(child.childNodes(), depth)
                }
                "dd" -> definitions += parseBlocks(child.childNodes(), depth)
            }
        }
        flush()
        return items.takeIf { it.isNotEmpty() }?.let(CookedBlock::DefinitionList)
    }

    private fun parseFigure(element: Element): List<CookedBlock> {
        val image = element.selectFirst("img") ?: return transparentOrUnknown(element, 1)
        val caption = element.selectFirst("figcaption")?.text()?.trim()?.ifBlank { null }
        return listOf(imageBlock(image, caption))
    }

    private fun parsePicture(element: Element): List<CookedBlock> {
        element.selectFirst("img")?.let { return listOf(imageBlock(it)) }
        val source = element.selectFirst("source[srcset]")?.attr("srcset")?.substringBefore(' ')?.substringBefore(',').orEmpty()
        return if (source.isBlank()) emptyList() else listOf(
            CookedBlock.Image(absolutizeMedia(source), "", isTrustedMedia(source)),
        )
    }

    private fun parseImageGrid(element: Element): CookedBlock.ImageGrid {
        val images = element.select("img").mapNotNull { img ->
            val span = parseImage(img) as? CookedSpan.Image ?: return@mapNotNull null
            val lightbox = generateSequence(img.parent()) { it.parent() }
                .firstOrNull { it.normalName() == "a" && it.hasClass("lightbox") }
                ?.attr("href")?.let(::absolutizeMedia)
            CookedBlock.Image(
                span.src,
                span.alt,
                span.trusted,
                lightbox?.takeIf { it.isNotBlank() } ?: span.original,
                span.width,
                span.height,
            )
        }
        val columns = element.attr("data-columns").toIntOrNull()?.coerceIn(1, 6) ?: 2
        val carousel = element.hasClass("d-image-grid--carousel") || element.attr("data-mode") == "carousel"
        return CookedBlock.ImageGrid(images, columns, carousel)
    }

    private fun parseFootnotes(element: Element): CookedBlock.Footnotes {
        val entries = element.select("li").mapIndexedNotNull { index, li ->
            val id = li.id().ifBlank { "fn-${index + 1}" }
            val copy = li.clone()
            copy.select("a.footnote-backref").remove()
            val spans = parseInlineNodes(copy.childNodes(), 1)
            spans.takeIf { it.isNotEmpty() }?.let { FootnoteEntry(id, (index + 1).toString(), it) }
        }
        return CookedBlock.Footnotes(entries)
    }

    private fun collectFootnotes(root: Element): Map<String, List<CookedSpan>> = buildMap {
        root.select("section.footnotes li, ol.footnotes-list li").forEach { li ->
            if (li.id().isBlank() || containsKey(li.id())) return@forEach
            val copy = li.clone()
            copy.select("a.footnote-backref").remove()
            put(li.id(), parseInlineNodes(copy.childNodes(), 1))
        }
    }

    private fun parsePoll(element: Element): CookedBlock.Poll {
        val options = element.select("li[data-poll-option-id]").map { option ->
            option.selectFirst(".option-text, .poll-option-label")?.text()
                ?: option.text()
        }.map { it.collapseWhitespace().trim() }.filter { it.isNotBlank() }
        val type = element.attr("data-poll-type")
        return CookedBlock.Poll(
            name = element.attr("data-poll-name").ifBlank { "poll" },
            options = options,
            title = element.attr("data-poll-question").ifBlank {
                element.selectFirst(".poll-title")?.text()?.trim().orEmpty()
            }.ifBlank { null },
            status = element.attr("data-poll-status").ifBlank { null },
            multiple = type == "multiple",
        )
    }

    private fun parsePolicy(element: Element, depth: Int): CookedBlock.Policy {
        val body = element.selectFirst(".policy-body") ?: element
        return CookedBlock.Policy(
            children = parseBlocks(body.childNodes(), depth),
            version = element.attr("data-version").ifBlank { null },
            groups = element.attr("data-groups").ifBlank { null },
        )
    }

    private fun parseChat(element: Element, depth: Int): CookedBlock.ChatTranscript {
        val messages = element.selectFirst(".chat-transcript-messages") ?: element
        return CookedBlock.ChatTranscript(
            username = element.attr("data-username").ifBlank { "LINUX DO Chat" },
            children = parseBlocks(messages.childNodes(), depth),
            channel = element.attr("data-channel-name").ifBlank { null },
            dateTime = element.attr("data-datetime").ifBlank { null },
            chained = element.hasClass("chat-transcript-chained"),
        )
    }

    private fun parseCallout(element: Element, depth: Int): CookedBlock.Callout? {
        val children = parseBlocks(element.childNodes(), depth).toMutableList()
        val first = children.firstOrNull() as? CookedBlock.Paragraph ?: return null
        val text = first.spans.plainText().trimStart()
        val match = CALLOUT_MARKER.find(text) ?: return null
        val kind = calloutKind(match.groupValues[1])
        val title = match.groupValues[3].trim().ifBlank { calloutTitle(kind) }
        val firstBreak = first.spans.indexOfFirst { it is CookedSpan.Break }
        if (firstBreak >= 0 && firstBreak + 1 < first.spans.size) {
            children[0] = first.copy(spans = normalizeSpans(first.spans.drop(firstBreak + 1), trimEdges = true))
        } else {
            children.removeAt(0)
        }
        val fold = match.groupValues[2]
        return CookedBlock.Callout(
            kind = kind,
            title = title,
            children = children,
            collapsible = fold.isNotBlank(),
            initiallyOpen = fold != "-",
        )
    }

    private fun parseClassCallout(element: Element, depth: Int): CookedBlock.Callout? {
        val raw = element.attr("data-callout").ifBlank {
            element.classNames().firstOrNull { it.startsWith("callout-") }?.removePrefix("callout-").orEmpty()
        }
        if (raw.isBlank() && !element.hasClass("callout")) return null
        val kind = calloutKind(raw.ifBlank { "note" })
        val titleNode = element.selectFirst(".callout-title")
        val body = element.selectFirst(".callout-content, .callout-body") ?: element
        return CookedBlock.Callout(
            kind,
            titleNode?.text()?.trim().orEmpty().ifBlank { calloutTitle(kind) },
            parseBlocks(body.childNodes(), depth),
            collapsible = element.hasAttr("data-fold"),
            initiallyOpen = element.attr("data-fold") != "-",
        )
    }

    private fun parseLazyVideo(element: Element): CookedBlock.Media {
        val link = element.selectFirst("a.title-link, a[href]")
        val src = allowedHref(link?.attr("href").orEmpty())
        val provider = element.attr("data-provider-name").ifBlank { "视频" }
        return CookedBlock.Media(
            kind = CookedBlock.Media.Kind.Embed,
            src = src,
            title = element.attr("data-video-title").ifBlank { "$provider 视频" },
            poster = element.selectFirst("img")?.let(::imageSource)?.let(::absolutizeMedia).orEmpty(),
            trusted = isTrustedMedia(src),
        )
    }

    private fun parseIframe(element: Element): CookedBlock.Media {
        val src = allowedMediaUrl(element.attr("src").ifBlank { element.attr("data-src") })
        return CookedBlock.Media(
            kind = CookedBlock.Media.Kind.Embed,
            src = src,
            title = element.attr("title").ifBlank { hostOf(src).ifBlank { "嵌入内容" } },
            trusted = isTrustedMedia(src),
            width = dimension(element, "width"),
            height = dimension(element, "height"),
        )
    }

    private fun parseVideo(element: Element): CookedBlock.Media {
        val video = if (element.normalName() == "video") element else element.selectFirst("video")
        val raw = element.attr("data-video-src").ifBlank {
            video?.attr("src").orEmpty().ifBlank { video?.selectFirst("source[src]")?.attr("src").orEmpty() }
        }
        val src = allowedMediaUrl(raw)
        val poster = element.attr("data-thumbnail-src").ifBlank { video?.attr("poster").orEmpty() }.let(::absolutizeMedia)
        return CookedBlock.Media(
            kind = CookedBlock.Media.Kind.Video,
            src = src,
            title = video?.attr("title").orEmpty().ifBlank { "视频" },
            poster = poster,
            originalSrc = element.attr("data-orig-src").ifBlank { src },
            mime = video?.selectFirst("source")?.attr("type")?.ifBlank { null },
            trusted = isTrustedMedia(src),
            width = video?.let { dimension(it, "width") },
            height = video?.let { dimension(it, "height") },
        )
    }

    private fun parseAudio(element: Element, voice: Boolean = false): CookedBlock.Media {
        val audio = if (element.normalName() == "audio") element else element.selectFirst("audio") ?: element
        val source = audio.selectFirst("source[src]")
        val src = allowedMediaUrl(audio.attr("src").ifBlank { source?.attr("src").orEmpty() })
        return CookedBlock.Media(
            kind = if (voice) CookedBlock.Media.Kind.Voice else CookedBlock.Media.Kind.Audio,
            src = src,
            title = audio.attr("title").ifBlank { if (voice) "语音消息" else "音频" },
            originalSrc = source?.attr("data-orig-src").orEmpty().ifBlank { src },
            mime = source?.attr("type")?.ifBlank { null },
            trusted = isTrustedMedia(src),
        )
    }

    private fun mediaBlock(element: Element): CookedBlock? {
        val tag = element.normalName()
        if (tag == "audio") return parseAudio(element)
        if (tag == "video") return parseVideo(element)
        if (tag == "iframe") return parseIframe(element)
        if (tag == "div" && element.hasClass("d-wrap") && element.attr("data-wrap") == "voice") {
            return parseAudio(element, voice = true)
        }
        if (tag == "div" && element.classNames().any { it in VIDEO_CONTAINER_CLASSES }) return parseVideo(element)
        return null
    }

    private fun mathBlock(element: Element): List<CookedBlock> =
        element.text().trim().takeIf { it.isNotBlank() }?.let { listOf(CookedBlock.Math(it)) }.orEmpty()

    private fun parseSvg(element: Element): List<CookedBlock> {
        if (element.hasClass("d-icon") || (!element.hasAttr("viewBox") && !element.hasAttr("width") && !element.hasAttr("height"))) {
            return emptyList()
        }
        val safe = element.clone()
        safe.select("script, style, foreignObject, iframe, object, embed, audio, video").remove()
        safe.getAllElements().forEach { node ->
            node.attributes().asList().map { it.key }.forEach { name ->
                if (name.startsWith("on", true) || name == "style") node.removeAttr(name)
            }
            listOf("href", "xlink:href", "src").forEach { name ->
                if (node.hasAttr(name) && !node.attr(name).startsWith("#")) node.removeAttr(name)
            }
        }
        return listOf(
            CookedBlock.Svg(
                source = safe.outerHtml(),
                width = dimension(safe, "width"),
                height = dimension(safe, "height"),
                title = safe.selectFirst("title")?.text()?.trim().orEmpty().ifBlank { "SVG 图像" },
            ),
        )
    }

    private fun transparentOrUnknown(element: Element, depth: Int): List<CookedBlock> {
        val children = parseBlocks(element.childNodes(), depth)
        return if (children.isEmpty()) emptyList() else listOf(CookedBlock.Unknown(element.normalName(), children))
    }

    private fun inheritAlignment(block: CookedBlock, alignment: CookedAlignment): CookedBlock = when (block) {
        is CookedBlock.Paragraph -> if (block.alignment == CookedAlignment.Start) block.copy(alignment = alignment) else block
        is CookedBlock.Heading -> if (block.alignment == CookedAlignment.Start) block.copy(alignment = alignment) else block
        else -> block
    }

    private fun normalizeSpans(spans: List<CookedSpan>, trimEdges: Boolean): List<CookedSpan> {
        val normalized = spans.mapNotNull { span ->
            when (span) {
                is CookedSpan.Strong -> span.copy(children = normalizeSpans(span.children, false)).takeIf { it.children.isNotEmpty() }
                is CookedSpan.Emphasis -> span.copy(children = normalizeSpans(span.children, false)).takeIf { it.children.isNotEmpty() }
                is CookedSpan.Strike -> span.copy(children = normalizeSpans(span.children, false)).takeIf { it.children.isNotEmpty() }
                is CookedSpan.Underline -> span.copy(children = normalizeSpans(span.children, false)).takeIf { it.children.isNotEmpty() }
                is CookedSpan.Highlight -> span.copy(children = normalizeSpans(span.children, false)).takeIf { it.children.isNotEmpty() }
                is CookedSpan.Subscript -> span.copy(children = normalizeSpans(span.children, false)).takeIf { it.children.isNotEmpty() }
                is CookedSpan.Superscript -> span.copy(children = normalizeSpans(span.children, false)).takeIf { it.children.isNotEmpty() }
                is CookedSpan.Colored -> span.copy(children = normalizeSpans(span.children, false)).takeIf { it.children.isNotEmpty() }
                is CookedSpan.Sized -> span.copy(children = normalizeSpans(span.children, false)).takeIf { it.children.isNotEmpty() }
                is CookedSpan.Link -> span.copy(children = normalizeSpans(span.children, false))
                is CookedSpan.Spoiler -> span.copy(children = normalizeSpans(span.children, false)).takeIf { it.children.isNotEmpty() }
                is CookedSpan.Text -> CookedSpan.Text(span.text.collapseWhitespace()).takeIf { it.text.isNotEmpty() }
                else -> span
            }
        }
        val merged = mutableListOf<CookedSpan>()
        normalized.forEach { span ->
            val previous = merged.lastOrNull()
            if (span is CookedSpan.Text && previous is CookedSpan.Text) {
                merged[merged.lastIndex] = CookedSpan.Text(previous.text + span.text)
            } else {
                merged += span
            }
        }
        if (trimEdges && merged.isNotEmpty()) {
            (merged.firstOrNull() as? CookedSpan.Text)?.let { first ->
                merged[0] = CookedSpan.Text(first.text.trimStart())
            }
            (merged.lastOrNull() as? CookedSpan.Text)?.let { last ->
                merged[merged.lastIndex] = CookedSpan.Text(last.text.trimEnd())
            }
        }
        return merged.filterNot { it is CookedSpan.Text && it.text.isEmpty() }
    }

    private fun isInlineBridge(spans: List<CookedSpan>): Boolean = spans.all { span ->
        span is CookedSpan.Code || span is CookedSpan.Kbd || span is CookedSpan.Link ||
            span is CookedSpan.Emoji || span is CookedSpan.Break ||
            (span is CookedSpan.Text && span.text.isBlank())
    }

    private fun joinSpans(left: List<CookedSpan>, right: List<CookedSpan>): List<CookedSpan> {
        if (left.isEmpty()) return right
        if (right.isEmpty()) return left
        val leftText = left.last().plainText()
        val rightText = right.first().plainText()
        val needsSpace = leftText.isNotEmpty() && rightText.isNotEmpty() &&
            !leftText.last().isWhitespace() && !rightText.first().isWhitespace() &&
            rightText.first() !in NO_LEADING_SPACE
        return if (needsSpace) left + CookedSpan.Text(" ") + right else left + right
    }

    private fun alignment(element: Element): CookedAlignment {
        val raw = element.attr("align").ifBlank { cssProperty(safeStyle(element.attr("style")), "text-align").orEmpty() }
        return when (raw.lowercase()) {
            "center" -> CookedAlignment.Center
            "right", "end" -> CookedAlignment.End
            else -> CookedAlignment.Start
        }
    }

    private fun shouldSkip(element: Element): Boolean {
        val tag = element.normalName()
        if (tag in SKIP_TAGS) return true
        val classes = element.classNames()
        return "d-icon" in classes || "meta" in classes || "lb-spacer" in classes ||
            "quote-controls" in classes || "codeblock-buttons" in classes || "footnote-backref" in classes
    }

    private fun isInlineSemantic(element: Element): Boolean =
        element.normalName() == "div" && element.hasClass("lightbox-wrapper")

    private fun isInlineGraphic(image: CookedSpan.Image): Boolean =
        image.src.contains(":chip:") || image.src.contains("hashtag-chip") ||
            ((image.width ?: Int.MAX_VALUE) <= 32 && (image.height ?: Int.MAX_VALUE) <= 32)

    private fun imageSource(element: Element): String {
        val src = element.attr("src")
        if (src.isNotBlank() && !src.startsWith("data:")) return src
        return element.attr("data-src").ifBlank { element.attr("data-orig-src") }.ifBlank {
            element.attr("srcset").substringBefore(',').trim().substringBefore(' ')
        }
    }

    private fun dimension(element: Element, name: String): Int? =
        element.attr(name).trim().removeSuffix("px").toDoubleOrNull()?.toInt()?.takeIf { it > 0 }

    private fun absolutizeMedia(raw: String): String = when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> "$BASE_URL$raw"
        else -> raw
    }

    private fun allowedHref(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        if (value.startsWith("//")) return allowedHref("https:$value")
        if (value.startsWith("/") || value.startsWith("#") || value.startsWith("upload://")) return value
        val uri = runCatching { URI(value) }.getOrNull() ?: return ""
        return value.takeIf { uri.scheme?.lowercase() in ALLOWED_LINK_SCHEMES }.orEmpty()
    }

    private fun allowedMediaUrl(raw: String): String {
        val absolute = absolutizeMedia(raw.trim())
        if (absolute.startsWith("upload://")) return absolute
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return ""
        return absolute.takeIf { uri.scheme?.lowercase() in setOf("https", "http") }.orEmpty()
    }

    private fun isTrustedMedia(raw: String): Boolean {
        val uri = runCatching { URI(absolutizeMedia(raw)) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return host in trustedMediaHosts || LinuxDoMediaHosts.isTrusted(host)
    }

    private fun safeStyle(style: String): String =
        style.takeUnless { it.contains("url(", true) || it.contains("expression", true) || it.contains("javascript", true) }.orEmpty()

    private fun cssProperty(style: String, name: String): String? = style.split(';').mapNotNull { declaration ->
        val pair = declaration.split(':', limit = 2)
        pair.takeIf { it.size == 2 && it[0].trim().equals(name, true) }?.get(1)?.trim()
    }.lastOrNull()

    private fun allowedCssColor(color: String): Boolean {
        val value = color.trim()
        return value.matches(HEX_COLOR) || value.matches(NAMED_COLOR) || value.matches(FUNCTION_COLOR)
    }

    private fun hostOf(url: String): String = runCatching { URI(absolutizeMedia(url)).host }.getOrNull().orEmpty()

    private fun calloutKind(raw: String): CookedBlock.Callout.Kind = when (raw.lowercase()) {
        "note" -> CookedBlock.Callout.Kind.Note
        "tip", "hint" -> CookedBlock.Callout.Kind.Tip
        "important" -> CookedBlock.Callout.Kind.Important
        "warning", "caution" -> CookedBlock.Callout.Kind.Warning
        "danger", "error", "bug", "failure", "fail", "missing" -> CookedBlock.Callout.Kind.Danger
        "info", "todo", "abstract", "summary", "tldr" -> CookedBlock.Callout.Kind.Info
        "success", "check", "done" -> CookedBlock.Callout.Kind.Success
        "question", "help", "faq" -> CookedBlock.Callout.Kind.Question
        "quote", "cite" -> CookedBlock.Callout.Kind.Quote
        else -> CookedBlock.Callout.Kind.Unknown
    }

    private fun calloutTitle(kind: CookedBlock.Callout.Kind): String = when (kind) {
        CookedBlock.Callout.Kind.Note -> "备注"
        CookedBlock.Callout.Kind.Tip -> "提示"
        CookedBlock.Callout.Kind.Important -> "重要"
        CookedBlock.Callout.Kind.Warning -> "警告"
        CookedBlock.Callout.Kind.Danger -> "危险"
        CookedBlock.Callout.Kind.Info -> "信息"
        CookedBlock.Callout.Kind.Success -> "完成"
        CookedBlock.Callout.Kind.Question -> "问题"
        CookedBlock.Callout.Kind.Quote -> "引用"
        CookedBlock.Callout.Kind.Unknown -> "说明"
    }

    private fun isEmojiAlt(alt: String): Boolean = alt.length >= 3 && alt.startsWith(':') && alt.endsWith(':')

    companion object {
        private const val BASE_URL = "https://linux.do"
        private const val MAX_DEPTH = 48
        private val TAGS = Regex("<[^>]+>")
        private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        private val INLINE_TAGS = setOf(
            "a", "abbr", "b", "big", "br", "cite", "code", "del", "dfn", "em", "font", "i", "img",
            "input", "ins", "kbd", "mark", "q", "ruby", "rt", "s", "samp", "small", "span", "strike",
            "strong", "sub", "sup", "tt", "u", "var", "wbr",
        )
        private val SKIP_TAGS = setOf("script", "style", "template", "noscript", "object", "embed", "canvas")
        private val VIDEO_CONTAINER_CLASSES = setOf("video-placeholder-container", "video-container", "video-onebox")
        private val ALLOWED_LINK_SCHEMES = setOf("https", "http", "mailto")
        private val CALLOUT_MARKER = Regex("""^\[!([A-Za-z0-9_-]+)]([+-])?\s*([^\n]*)""")
        private val HEX_COLOR = Regex("^#[0-9a-fA-F]{3,8}$")
        private val NAMED_COLOR = Regex("^[a-zA-Z]{3,24}$")
        private val FUNCTION_COLOR = Regex("^(?:rgb|rgba|hsl|hsla)\\([^)]{1,64}\\)$", RegexOption.IGNORE_CASE)
        private val CLICK_COUNT_VALUE = Regex("^[0-9][0-9.,\\s]*[kKmMbB万亿]?$", RegexOption.IGNORE_CASE)
        private val NO_LEADING_SPACE = setOf('，', '。', '、', '；', '：', '！', '？', ',', '.', '!', '?', ';', ':', ')', ']', '}')
    }
}

private fun String.collapseWhitespace(): String = replace(Regex("[\\t\\n\\r\\f ]+"), " ")

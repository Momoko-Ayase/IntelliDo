package moe.momokko.intellido.domain.content

import moe.momokko.intellido.domain.icon.LinuxDoTagIcons
import org.jsoup.Jsoup

/**
 * Turns the allowlisted cooked model into simple HTML for a wrapping native viewer.
 * Remote `http(s)` image URLs are never inlined; JCEF-fetched data URIs may be.
 * Details and spoilers stay closed in this HTML path so hidden copy is not leaked.
 */
object CookedHtml {
    sealed class Part {
        data class Html(val document: CookedDocument) : Part()
        data class Image(val src: String, val alt: String, val originalSrc: String = src) : Part()
        data class Code(val language: String?, val code: String) : Part()
        data class Details(val summary: String, val inner: List<Part>, val initiallyOpen: Boolean = false) : Part()
        data class Spoiler(val inner: List<Part>) : Part()
        data class Poll(
            val name: String,
            val options: List<String>,
            val title: String? = null,
            val status: String? = null,
            val multiple: Boolean = false,
        ) : Part()
    }

    fun toSafeHtml(document: CookedDocument, media: Map<String, String> = emptyMap()): String =
        document.blocks.map { blockHtml(it, media) }.filter { it.isNotBlank() }.joinToString("")

    fun nativeParts(document: CookedDocument): List<Part> {
        val parts = mutableListOf<Part>()
        val buffer = mutableListOf<CookedBlock>()
        fun flush() {
            if (buffer.isNotEmpty()) {
                parts += Part.Html(CookedDocument(buffer.toList()))
                buffer.clear()
            }
        }
        document.blocks.forEach { block ->
            when (block) {
                is CookedBlock.Image -> {
                    if (block.trusted && block.src.isNotBlank()) {
                        flush()
                        parts += Part.Image(block.src, block.alt, block.original)
                    } else {
                        buffer += block
                    }
                }
                is CookedBlock.CodeBlock -> {
                    flush()
                    parts += Part.Code(block.language, block.code)
                }
                is CookedBlock.Details -> {
                    flush()
                    parts += Part.Details(block.summary, nativeParts(CookedDocument(block.children)), block.initiallyOpen)
                }
                is CookedBlock.Spoiler -> {
                    flush()
                    parts += Part.Spoiler(nativeParts(CookedDocument(block.children)))
                }
                is CookedBlock.Poll -> {
                    flush()
                    parts += Part.Poll(block.name, block.options, block.title, block.status, block.multiple)
                }
                else -> buffer += block
            }
        }
        flush()
        return parts
    }

    fun trustedMediaUrls(document: CookedDocument): List<String> {
        val urls = linkedSetOf<String>()
        collect(document.blocks, urls)
        return urls.toList()
    }

    fun emojiUrls(document: CookedDocument): List<String> {
        val urls = linkedSetOf<String>()
        collectEmoji(document.blocks, urls)
        return urls.toList()
    }

    fun uploadUrls(html: String): List<String> {
        val found = linkedSetOf<String>()
        Jsoup.parseBodyFragment(html, "https://linux.do").select("img").forEach { image ->
            val classes = image.classNames()
            val alt = image.attr("alt").trim()
            if (classes.any { it.equals("emoji", true) } || (alt.length >= 3 && alt.startsWith(':') && alt.endsWith(':'))) {
                return@forEach
            }
            val raw = image.attr("src").takeUnless { it.startsWith("data:", true) }.orEmpty()
                .ifBlank { image.attr("data-src") }
                .ifBlank { image.attr("data-orig-src") }
                .ifBlank { image.attr("srcset").substringBefore(',').trim().substringBefore(' ') }
                .trim()
            if (raw.isEmpty()) return@forEach
            val absolute = when {
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("/") -> "https://linux.do$raw"
                else -> raw
            }
            if (isLinuxDoImage(absolute)) {
                found += absolute
            }
        }
        return found.toList()
    }

    private fun isLinuxDoImage(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("https://")) {
            return false
        }
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        if (!LinuxDoMediaHosts.isTrusted(host)) {
            return false
        }
        if ("/emoji" in lower || "emoji." in lower || "/user_avatar/" in lower || "/letter_avatar" in lower) {
            return false
        }
        return "/uploads/" in lower ||
            "/optimized/" in lower ||
            "/original/" in lower ||
            IMAGE_EXTS.any { ext -> lower.contains(".$ext") }
    }

    private fun collect(blocks: List<CookedBlock>, urls: MutableSet<String>) {
        blocks.forEach { block ->
            when (block) {
                is CookedBlock.Paragraph -> collectSpans(block.spans, urls)
                is CookedBlock.Heading -> collectSpans(block.spans, urls)
                is CookedBlock.Quote -> collect(block.children, urls)
                is CookedBlock.ListBlock -> block.items.forEach { collect(it, urls) }
                is CookedBlock.Image -> if (block.trusted && block.src.isNotBlank()) urls += block.src
                is CookedBlock.ImageGrid -> block.images.forEach { image ->
                    if (image.trusted && image.src.isNotBlank()) urls += image.src
                }
                is CookedBlock.Table -> block.rows.forEach { row ->
                    row.cells.forEach { collectSpans(it.spans, urls) }
                }
                is CookedBlock.Details -> collect(block.children, urls)
                is CookedBlock.Spoiler -> collect(block.children, urls)
                is CookedBlock.Callout -> collect(block.children, urls)
                is CookedBlock.Policy -> collect(block.children, urls)
                is CookedBlock.ChatTranscript -> collect(block.children, urls)
                is CookedBlock.DefinitionList -> block.items.forEach { item ->
                    item.definitions.forEach { collect(it, urls) }
                }
                is CookedBlock.Unknown -> collect(block.children, urls)
                is CookedBlock.Media -> {
                    if (block.trusted && block.src.isNotBlank()) urls += block.src
                    if (block.poster.isNotBlank() && LinuxDoMediaHosts.isTrusted(runCatching { java.net.URI(block.poster).host }.getOrNull())) {
                        urls += block.poster
                    }
                }
                is CookedBlock.Onebox, is CookedBlock.Poll, is CookedBlock.Math,
                is CookedBlock.Footnotes, is CookedBlock.Svg, CookedBlock.ThematicBreak,
                is CookedBlock.CodeBlock -> Unit
            }
        }
    }

    private fun collectSpans(spans: List<CookedSpan>, urls: MutableSet<String>) {
        spans.forEach { span ->
            when (span) {
                is CookedSpan.Image -> if (span.trusted && span.src.isNotBlank()) urls += span.src
                is CookedSpan.Strong -> collectSpans(span.children, urls)
                is CookedSpan.Emphasis -> collectSpans(span.children, urls)
                is CookedSpan.Strike -> collectSpans(span.children, urls)
                is CookedSpan.Underline -> collectSpans(span.children, urls)
                is CookedSpan.Highlight -> collectSpans(span.children, urls)
                is CookedSpan.Subscript -> collectSpans(span.children, urls)
                is CookedSpan.Superscript -> collectSpans(span.children, urls)
                is CookedSpan.Colored -> collectSpans(span.children, urls)
                is CookedSpan.Sized -> collectSpans(span.children, urls)
                is CookedSpan.Link -> collectSpans(span.children, urls)
                is CookedSpan.Spoiler -> collectSpans(span.children, urls)
                is CookedSpan.FootnoteRef -> collectSpans(span.content, urls)
                is CookedSpan.Text, is CookedSpan.Code, is CookedSpan.Kbd, is CookedSpan.Emoji,
                is CookedSpan.LocalDate, is CookedSpan.Math, is CookedSpan.ClickCount, CookedSpan.Break -> Unit
            }
        }
    }

    private fun collectEmoji(blocks: List<CookedBlock>, urls: MutableSet<String>) {
        blocks.forEach { block ->
            when (block) {
                is CookedBlock.Paragraph -> collectEmojiSpans(block.spans, urls)
                is CookedBlock.Heading -> collectEmojiSpans(block.spans, urls)
                is CookedBlock.Quote -> collectEmoji(block.children, urls)
                is CookedBlock.ListBlock -> block.items.forEach { collectEmoji(it, urls) }
                is CookedBlock.Table -> block.rows.forEach { row ->
                    row.cells.forEach { collectEmojiSpans(it.spans, urls) }
                }
                is CookedBlock.Details -> collectEmoji(block.children, urls)
                is CookedBlock.Spoiler -> collectEmoji(block.children, urls)
                is CookedBlock.Callout -> collectEmoji(block.children, urls)
                is CookedBlock.Policy -> collectEmoji(block.children, urls)
                is CookedBlock.ChatTranscript -> collectEmoji(block.children, urls)
                is CookedBlock.DefinitionList -> block.items.forEach { item ->
                    collectEmojiSpans(item.term, urls)
                    item.definitions.forEach { collectEmoji(it, urls) }
                }
                is CookedBlock.Unknown -> collectEmoji(block.children, urls)
                else -> Unit
            }
        }
    }

    private fun collectEmojiSpans(spans: List<CookedSpan>, urls: MutableSet<String>) {
        spans.forEach { span ->
            when (span) {
                is CookedSpan.Emoji -> if (span.needsImage) urls += span.src
                is CookedSpan.Strong -> collectEmojiSpans(span.children, urls)
                is CookedSpan.Emphasis -> collectEmojiSpans(span.children, urls)
                is CookedSpan.Strike -> collectEmojiSpans(span.children, urls)
                is CookedSpan.Underline -> collectEmojiSpans(span.children, urls)
                is CookedSpan.Highlight -> collectEmojiSpans(span.children, urls)
                is CookedSpan.Subscript -> collectEmojiSpans(span.children, urls)
                is CookedSpan.Superscript -> collectEmojiSpans(span.children, urls)
                is CookedSpan.Colored -> collectEmojiSpans(span.children, urls)
                is CookedSpan.Sized -> collectEmojiSpans(span.children, urls)
                is CookedSpan.Link -> collectEmojiSpans(span.children, urls)
                is CookedSpan.Spoiler -> collectEmojiSpans(span.children, urls)
                is CookedSpan.FootnoteRef -> collectEmojiSpans(span.content, urls)
                else -> Unit
            }
        }
    }

    private fun blockHtml(block: CookedBlock, media: Map<String, String>): String = when (block) {
        is CookedBlock.Paragraph -> {
            val inner = spans(block.spans, media)
            if (inner.isBlank()) "" else "<p>$inner</p>"
        }
        is CookedBlock.Heading -> {
            val level = block.level.coerceIn(1, 6)
            "<h$level>${spans(block.spans, media)}</h$level>"
        }
        is CookedBlock.Quote -> {
            val who = block.attribution?.takeIf { it.isNotBlank() }
                ?.let { "<p class=\"quote-title\">${escape(it)}</p>" }
                .orEmpty()
            "<blockquote>$who${block.children.joinToString("") { blockHtml(it, media) }}</blockquote>"
        }
        is CookedBlock.ListBlock -> listHtml(block, media)
        is CookedBlock.CodeBlock -> "<pre><code>${escape(block.code)}</code></pre>"
        is CookedBlock.Image -> {
            val tag = imgTag(block.src, block.alt, block.trusted, media)
            if (tag.startsWith("<img")) "<p>$tag</p>" else captionParagraph(block.alt)
        }
        is CookedBlock.ImageGrid -> block.images.joinToString("") { image -> blockHtml(image, media) }
        is CookedBlock.Table -> tableHtml(block, media)
        is CookedBlock.Onebox -> oneboxHtml(block)
        is CookedBlock.Details -> {
            val label = block.summary.trim().ifBlank { "…" }
            "<p class=\"details\">${escape(label)}</p>"
        }
        is CookedBlock.Spoiler -> "<p class=\"spoiler\">…</p>"
        is CookedBlock.Poll -> {
            val items = block.options.joinToString("") { "<li>${escape(it)}</li>" }
            if (items.isEmpty()) "" else "<ul class=\"poll\">$items</ul>"
        }
        is CookedBlock.Callout -> {
            val body = block.children.joinToString("") { blockHtml(it, media) }
            "<blockquote><p><b>${escape(block.title)}</b></p>$body</blockquote>"
        }
        is CookedBlock.Media -> {
            val title = block.title.ifBlank { block.src }
            if (block.src.isBlank()) "<p>${escape(title)}</p>" else
                "<p><a href=\"${escape(absolutizeHref(block.src))}\">${escape(title)}</a></p>"
        }
        is CookedBlock.Math -> "<pre><code>${escape(block.latex)}</code></pre>"
        is CookedBlock.Footnotes -> {
            val items = block.entries.joinToString("") { entry ->
                "<li>${spans(entry.spans, media)}</li>"
            }
            "<ol>$items</ol>"
        }
        is CookedBlock.Policy -> block.children.joinToString("") { blockHtml(it, media) }
        is CookedBlock.ChatTranscript -> {
            val who = escape(block.username)
            "<blockquote><p><b>$who</b></p>${block.children.joinToString("") { blockHtml(it, media) }}</blockquote>"
        }
        is CookedBlock.DefinitionList -> block.items.joinToString("") { item ->
            "<p><b>${spans(item.term, media)}</b></p>" + item.definitions.flatten().joinToString("") { blockHtml(it, media) }
        }
        is CookedBlock.Svg -> "<p><i>${escape(block.title)}</i></p>"
        CookedBlock.ThematicBreak -> "<hr>"
        is CookedBlock.Unknown -> block.children.joinToString("") { blockHtml(it, media) }
    }

    private fun listHtml(block: CookedBlock.ListBlock, media: Map<String, String>): String =
        block.items.mapIndexed { index, item ->
            val mark = if (block.ordered) "${block.start + index}." else "•"
            "<p>&nbsp;&nbsp;$mark ${flattenListItem(item, media)}</p>"
        }.joinToString("")

    private fun flattenListItem(item: List<CookedBlock>, media: Map<String, String>): String =
        item.joinToString("") { child ->
            when (child) {
                is CookedBlock.Paragraph -> spans(child.spans, media)
                is CookedBlock.ListBlock -> listHtml(child, media)
                is CookedBlock.Unknown -> ""
                else -> blockHtml(child, media)
            }
        }

    private fun tableHtml(block: CookedBlock.Table, media: Map<String, String>): String {
        if (block.rows.isEmpty()) {
            return ""
        }
        val body = block.rows.joinToString("") { row ->
            val cells = row.cells.joinToString("") { cell ->
                val tag = if (cell.header || row.header) "th" else "td"
                "<$tag>${spans(cell.spans, media)}</$tag>"
            }
            "<tr>$cells</tr>"
        }
        return "<table>$body</table>"
    }

    private fun oneboxHtml(block: CookedBlock.Onebox): String {
        val title = block.title.trim().ifBlank { block.url }
        val titleHtml = if (block.url.isNotBlank()) {
            "<p class=\"onebox-title\"><a href=\"${escape(absolutizeHref(block.url))}\">${escape(title)}</a></p>"
        } else {
            if (title.isBlank()) "" else "<p class=\"onebox-title\">${escape(title)}</p>"
        }
        val desc = block.description.trim().takeIf { it.isNotEmpty() }
            ?.let { "<p class=\"onebox-desc\">${escape(it)}</p>" }
            .orEmpty()
        val site = block.site.trim().takeIf { it.isNotEmpty() }
            ?.let { "<p class=\"onebox-site\">${escape(it)}</p>" }
            .orEmpty()
        val inner = titleHtml + desc + site
        return if (inner.isBlank()) "" else "<div class=\"onebox\">$inner</div>"
    }

    private fun spans(spans: List<CookedSpan>, media: Map<String, String>): String =
        spans.mapIndexed { index, span ->
            var html = spanHtml(span, media)
            val prev = spans.getOrNull(index - 1)
            val next = spans.getOrNull(index + 1)
            if (span is CookedSpan.Link && next is CookedSpan.Text && next.text.startsWith(" ")) {
                html = html.removeSuffix("</a>") + "&nbsp;</a>"
            }
            if (span is CookedSpan.Text) {
                if (prev is CookedSpan.Code || prev is CookedSpan.Kbd || prev is CookedSpan.Link) {
                    if (html.startsWith(" ")) {
                        html = html.removePrefix(" ")
                    }
                }
                if (next is CookedSpan.Code || next is CookedSpan.Kbd) {
                    if (html.endsWith(" ")) {
                        html = html.removeSuffix(" ")
                    }
                }
                if (next is CookedSpan.Link && html.endsWith(" ")) {
                    html = html.removeSuffix(" ") + "&nbsp;"
                }
            }
            html
        }.joinToString("")

    private fun spanHtml(span: CookedSpan, media: Map<String, String>): String = when (span) {
        is CookedSpan.Text -> escape(span.text)
        is CookedSpan.Break -> "<br>"
        is CookedSpan.Strong -> "<b>${spans(span.children, media)}</b>"
        is CookedSpan.Emphasis -> "<i>${spans(span.children, media)}</i>"
        is CookedSpan.Strike -> "<s>${spans(span.children, media)}</s>"
        is CookedSpan.Underline -> "<u>${spans(span.children, media)}</u>"
        is CookedSpan.Highlight -> "<span class=\"mark\">${spans(span.children, media)}</span>"
        is CookedSpan.Subscript -> "<sub>${spans(span.children, media)}</sub>"
        is CookedSpan.Superscript -> "<sup>${spans(span.children, media)}</sup>"
        is CookedSpan.Colored ->
            "<span style=\"${if (span.color.isNotBlank()) "color:${span.color};" else ""}${if (span.background.isNotBlank()) "background-color:${span.background};" else ""}\">${spans(span.children, media)}</span>"
        is CookedSpan.Sized -> "<span>${spans(span.children, media)}</span>"
        is CookedSpan.Code -> """<font face="Monospaced">&nbsp;${escape(span.text)}&nbsp;</font>"""
        is CookedSpan.Kbd -> """<font face="Monospaced">&nbsp;${escape(span.text)}&nbsp;</font>"""
        is CookedSpan.Link -> {
            val inner = spans(span.children, media)
            val href = escape(absolutizeHref(span.href))
            if (span.kind == CookedSpan.Link.Kind.Hashtag) {
                hashtagHtml(href, inner, span)
            } else {
                "<a href=\"$href\">$inner</a>"
            }
        }
        is CookedSpan.Image -> imgTag(span.src, span.alt, span.trusted, media).ifBlank { captionText(span.alt) }
        is CookedSpan.Emoji -> emojiTag(span, media)
        is CookedSpan.Spoiler -> "<span class=\"spoiler\">…</span>"
        is CookedSpan.FootnoteRef -> "<sup>${escape(span.number)}</sup>"
        is CookedSpan.LocalDate -> escape(span.text)
        is CookedSpan.Math -> "<font face=\"Monospaced\">${escape(span.latex)}</font>"
        is CookedSpan.ClickCount -> "<small>${escape(span.text)}</small>"
    }

    private fun hashtagHtml(href: String, inner: String, span: CookedSpan.Link): String {
        val name = hashtagName(span)
        val style = LinuxDoTagIcons.style(name)
        if (style == null) {
            return """<a href="$href">$inner</a>"""
        }
        return """<a href="$href"><img class="hashtag-chip" src="intellido-media:chip:${escape(name)}" alt="${escape(name)}"></a>"""
    }

    private fun hashtagName(span: CookedSpan.Link): String {
        val text = span.children.plainText().trim().trimStart('#')
        if (text.isNotEmpty()) {
            return text
        }
        val path = span.href.substringAfter("/tag/").substringBefore('?').substringBefore('#')
        val slug = path.substringBefore('/')
        return runCatching { java.net.URLDecoder.decode(slug, Charsets.UTF_8) }.getOrDefault(slug)
    }

    private fun emojiTag(span: CookedSpan.Emoji, media: Map<String, String>): String {
        val data = mediaData(span.src, media)
        return if (span.needsImage && !data.isNullOrBlank() && isPaintedSrc(data)) {
            "<img class=\"emoji\" src=\"${escape(data)}\" alt=\":${escape(span.shortcode)}:\" width=\"20\" height=\"20\">"
        } else {
            escape(span.glyph.ifBlank { ":${span.shortcode}:" })
        }
    }

    private fun imgTag(src: String, alt: String, trusted: Boolean, media: Map<String, String>): String {
        val data = mediaData(src, media)
        return if (trusted && !data.isNullOrBlank() && isPaintedSrc(data)) {
            "<img src=\"${escape(data)}\" alt=\"${escape(alt)}\">"
        } else {
            ""
        }
    }

    private fun isPaintedSrc(data: String): Boolean {
        val value = data.trim()
        return value.startsWith("data:image") ||
            value.startsWith("file:") ||
            value.startsWith("intellido-media:")
    }

    private fun mediaData(src: String, media: Map<String, String>): String? {
        if (src.isBlank() || media.isEmpty()) {
            return null
        }
        media[src]?.let { return it }
        val key = MediaUrls.key(src)
        return media.entries.firstOrNull { MediaUrls.key(it.key) == key }?.value
    }

    private fun captionParagraph(alt: String): String {
        val caption = captionText(alt)
        return if (caption.isEmpty()) "" else "<p><i>$caption</i></p>"
    }

    private fun captionText(alt: String): String {
        val trimmed = alt.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        if (trimmed.startsWith(":") && trimmed.endsWith(":") && trimmed.length >= 3) {
            return ""
        }
        val lower = trimmed.lowercase()
        if (lower in GENERIC_IMAGE_ALTS) {
            return ""
        }
        val ext = lower.substringAfterLast('.', missingDelimiterValue = "")
        if (ext in IMAGE_EXTS) {
            return ""
        }
        return escape(trimmed)
    }

    private fun absolutizeHref(href: String): String =
        if (href.startsWith("/")) "https://linux.do$href" else href

    private fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private val GENERIC_IMAGE_ALTS = setOf("image", "img", "photo", "picture", "图片", "圖像")
    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp")
}

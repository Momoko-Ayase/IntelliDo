package moe.momokko.intellido.domain.content

object MediaUrls {
    fun key(url: String): String = url.substringBefore('?')

    /**
     * Discourse serves cooked `<img>` from `/optimized/` with a `_N_WxH` suffix.
     * Lightbox / editor open should use `/original/` without that suffix.
     */
    fun original(url: String): String {
        val path = key(url).replace("/optimized/", "/original/")
        return SIZE_SUFFIX.replace(path, "")
    }

    private val SIZE_SUFFIX = Regex("""_\d+_\d+x\d+(?=\.[A-Za-z0-9]+$)""")
}

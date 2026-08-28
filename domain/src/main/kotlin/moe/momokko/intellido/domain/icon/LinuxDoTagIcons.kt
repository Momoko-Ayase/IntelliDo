package moe.momokko.intellido.domain.icon

/**
 * LINUX DO tag-icons theme: icon name plus the configured color.
 * Tags.json does not carry this; only these named tags show a glyph on the website.
 */
object LinuxDoTagIcons {
    data class Style(val icon: String, val color: String)

    fun style(name: String): Style? {
        val key = name.trim()
        if (key.isEmpty()) {
            return null
        }
        return STYLES[key] ?: STYLES[key.lowercase()]
    }

    fun icon(name: String): String? = style(name)?.icon

    fun color(name: String): String? = style(name)?.color

    fun styles(): Collection<Style> = STYLES.values.distinct()

    fun all(): Map<String, Style> = STYLES

    fun glyphNames(): Set<String> = STYLES.values.map { it.icon }.toSet()

    private val STYLES: Map<String, Style> = parse(
        "公告,bullhorn,#00AEFF|" +
            "精华神帖,thumbs-up,#00AEFF|" +
            "快问快答,circle-question,#669D34|" +
            "nsfw,triangle-exclamation,#F7941D|" +
            "NSFW,triangle-exclamation,#F7941D|" +
            "文档,book,#75B6D7|" +
            "人工智能,brain,#BD93F9|" +
            "软件开发,file-code,#669D34|" +
            "硬件开发,file-code,#669D34|" +
            "抽奖,shuffle,#F7941D|" +
            "纯水,faucet,#F7941D|" +
            "树洞,tree,#669D34|" +
            "开源推广,receipt,#669D34|" +
            "推广,receipt,#669D34|" +
            "高级推广,coins,#F5BF03|" +
            "公益推广,receipt,#669D34|" +
            "原创,lightbulb,#00AEFF|" +
            "集中帖,people-group,#00AEFF|" +
            "碎碎碎念,droplet,#00AEFF|" +
            "病友,user-injured,#F7941D|" +
            "游戏,gamepad,#669D34|" +
            "职场,briefcase,#669D34|" +
            "拼车,car,#669D34|" +
            "网络安全,user-secret,#FF1111|" +
            "金融经济,hand-holding-dollar,#669D34|" +
            "赏金任务,comment-dollar,#669D34|" +
            "音乐,music,#669D34|" +
            "影视,video,#669D34|" +
            "旅行,route,#669D34|" +
            "美食,pepper-hot,#669D34|" +
            "二次元,venus,#669D34|" +
            "动漫,face-smile,#669D34|" +
            "配置优化,terminal,#669D34|" +
            "软件测试,bug,#669D34|" +
            "软件调试,spider,#669D34|" +
            "vps,server,#669D34|" +
            "硬件测试,bug,#669D34|" +
            "硬件调试,spider,#669D34|" +
            "摄影,camera,#669D34|" +
            "嵌入式,microchip,#669D34|" +
            "健身,heart-pulse,#669D34|" +
            "算法,calculator,#669D34|" +
            "aff,arrow-pointer,#F7941D|" +
            "订阅节点,network-wired,#669D34|" +
            "数据库,database,#669D34|" +
            "计算机网络,ethernet,#669D34|" +
            "求资源,hands-praying,#669D34|" +
            "禁水,droplet-slash,#FF5555|" +
            "危险,radiation,#FF1111|" +
            "封禁,user-slash,#FF4444|" +
            "livestream,headset,#00AEFF|" +
            "转载,share,#669D34|" +
            "优质博文,blog,#00AEFF|" +
            "作品集,palette,#00AEFF",
    )

    private fun parse(raw: String): Map<String, Style> {
        val out = linkedMapOf<String, Style>()
        raw.split('|').forEach { item ->
            val parts = item.split(',')
            if (parts.size < 3) {
                return@forEach
            }
            val name = parts[0].trim()
            val icon = parts[1].trim()
            val color = parts[2].trim().removePrefix("#").uppercase()
            if (name.isNotEmpty() && icon.isNotEmpty() && color.length == 6) {
                out[name] = Style(icon, color)
            }
        }
        return out
    }
}

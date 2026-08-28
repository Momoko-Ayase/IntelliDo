package moe.momokko.intellido.domain.icon

import moe.momokko.intellido.domain.catalog.CommunityCategory

/**
 * Guest sidebar categories from LINUX DO `default_navigation_menu_categories`.
 * The list is the current site navigation; live catalogs do not replace it.
 */
object LinuxDoSidebarCategories {
    val GUEST: List<CommunityCategory> = listOf(
        category(4, "开发调优", "develop", "00AEFF", "code"),
        category(98, "国产替代", "domestic", "E91E63", "seedling"),
        category(14, "资源荟萃", "resource", "26A69A", "square-share-nodes"),
        category(10, "文档共建", "wiki", "78909C", "book"),
        category(27, "非我莫属", "job", "1E88E5", "briefcase"),
        category(45, "读书成诗", "reading", "FDD835", "book-open-reader"),
        category(34, "前沿快讯", "news", "7E57C2", "newspaper"),
        category(92, "网络记忆", "feeds", "FB8C00", "rss"),
        category(32, "福利羊毛", "welfare", "F4511E", "fire"),
        category(42, "搞七捻三", "gossip", "43A047", "droplet"),
        category(110, "虫洞广场", "square", "EC407A", "location-dot"),
        category(11, "运营反馈", "feedback", "90A4AE", "comments"),
    )

    /**
     * Keep the seed names and order. Copy live ids / colours / icons from the
     * same category, identified by name then slug. Matching by id alone would
     * paint another category's icon onto this row and open that category's
     * topics when the seed ids are stale.
     */
    fun overlay(live: List<CommunityCategory>): List<CommunityCategory> {
        val visible = live.filterNot { it.readRestricted }
        val byName = visible.associateBy { it.name }
        val bySlug = visible.associateBy { it.slug.lowercase() }
        return GUEST.map { seed ->
            val hit = byName[seed.name] ?: bySlug[seed.slug.lowercase()] ?: return@map seed
            seed.copy(
                id = hit.id,
                slug = hit.slug.ifBlank { seed.slug },
                description = hit.description ?: seed.description,
                topicCount = hit.topicCount,
                color = hit.color?.takeIf { it.isNotBlank() } ?: seed.color,
                icon = hit.icon?.takeIf { it.isNotBlank() } ?: seed.icon,
            )
        }
    }

    private fun category(
        id: Long,
        name: String,
        slug: String,
        color: String,
        icon: String,
    ): CommunityCategory = CommunityCategory(
        id = id,
        name = name,
        slug = slug,
        description = null,
        topicCount = 0,
        readRestricted = false,
        color = color,
        icon = icon,
    )
}

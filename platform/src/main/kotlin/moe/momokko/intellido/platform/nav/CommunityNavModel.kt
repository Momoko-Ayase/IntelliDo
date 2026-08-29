package moe.momokko.intellido.platform.nav

import moe.momokko.intellido.domain.catalog.CommunityCategory
import moe.momokko.intellido.domain.catalog.CommunityTag
import moe.momokko.intellido.domain.icon.LinuxDoSidebarCategories
import moe.momokko.intellido.domain.icon.LinuxDoSidebarTags

enum class CommunityNavAction {
    TOPICS,
    MY_POSTS,
    MY_MESSAGES,
    ALL_CATEGORIES,
    ALL_TAGS,
    TAGS,
    GROUPS,
    BADGES,
    MEMBERS,
    ABOUT,
}

sealed class CommunityNavEntry {
    data class Action(
        val action: CommunityNavAction,
        val titleKey: String,
        val needsSignIn: Boolean = false,
        val icon: String? = null,
    ) : CommunityNavEntry()

    data class Header(val titleKey: String) : CommunityNavEntry()

    data class Category(val category: CommunityCategory) : CommunityNavEntry()

    data class Tag(val tag: CommunityTag) : CommunityNavEntry()

    data class Link(
        val titleKey: String,
        val url: String,
        val icon: String? = null,
    ) : CommunityNavEntry()

    data class Group(
        val titleKey: String,
        val icon: String? = null,
        val children: List<CommunityNavEntry>,
        val expandedByDefault: Boolean = true,
    ) : CommunityNavEntry()
}

object CommunityNavModel {
    const val TOOL_WINDOW_ID: String = "LINUX DO"

    val RESOURCE_LINKS: List<CommunityNavEntry.Link> = listOf(
        CommunityNavEntry.Link("nav.resource.connect", "https://connect.linux.do", "link"),
        CommunityNavEntry.Link("nav.resource.channel", "https://t.me/linux_do_channel", "paper-plane"),
        CommunityNavEntry.Link("nav.resource.idcflare", "https://idcflare.com", "server"),
        CommunityNavEntry.Link("nav.resource.more", "https://go.linux.do/pub/resources", "infinity"),
    )

    fun guest(
        categories: List<CommunityCategory>,
        tags: List<CommunityTag> = emptyList(),
    ): List<CommunityNavEntry> = entries(signedIn = false, categories, tags)

    fun signedIn(
        categories: List<CommunityCategory>,
        tags: List<CommunityTag> = emptyList(),
    ): List<CommunityNavEntry> = entries(signedIn = true, categories, tags)

    fun forSession(
        signedIn: Boolean,
        categories: List<CommunityCategory>,
        tags: List<CommunityTag> = emptyList(),
    ): List<CommunityNavEntry> = entries(signedIn, categories, tags)

    private fun entries(
        signedIn: Boolean,
        categories: List<CommunityCategory>,
        tags: List<CommunityTag>,
    ): List<CommunityNavEntry> = buildList {
        add(CommunityNavEntry.Action(CommunityNavAction.TOPICS, "nav.topics", icon = "layer-group"))
        if (signedIn) {
            add(CommunityNavEntry.Action(CommunityNavAction.MY_POSTS, "nav.myPosts", icon = "user"))
        }
        add(CommunityNavEntry.Link("nav.recent", "https://linux.do/upcoming-events", "calendar"))
        add(
            CommunityNavEntry.Group(
                titleKey = "nav.section.more",
                icon = "ellipsis-vertical",
                expandedByDefault = false,
                children = listOf(
                    CommunityNavEntry.Action(CommunityNavAction.ABOUT, "directory.about", icon = "circle-info"),
                    CommunityNavEntry.Action(CommunityNavAction.GROUPS, "directory.groups", icon = "users"),
                    CommunityNavEntry.Action(CommunityNavAction.BADGES, "directory.badges", icon = "certificate"),
                    CommunityNavEntry.Link("nav.leaderboard", "https://linux.do/leaderboard", "chart-simple"),
                    CommunityNavEntry.Link("nav.filter", "https://linux.do/filter", "filter"),
                ),
            ),
        )
        add(CommunityNavEntry.Header("nav.section.resources"))
        addAll(RESOURCE_LINKS)
        add(CommunityNavEntry.Header("nav.section.categories"))
        LinuxDoSidebarCategories.overlay(categories).forEach { add(CommunityNavEntry.Category(it)) }
        add(
            CommunityNavEntry.Action(
                CommunityNavAction.ALL_CATEGORIES,
                "nav.allCategories",
                icon = "list",
            ),
        )
        add(CommunityNavEntry.Header("nav.section.tags"))
        sidebarTags(tags).forEach { add(CommunityNavEntry.Tag(it)) }
        add(CommunityNavEntry.Action(CommunityNavAction.ALL_TAGS, "nav.allTags", icon = "list"))
    }

    private fun sidebarTags(tags: List<CommunityTag>): List<CommunityTag> {
        val byName = tags.associateBy { it.name }
        return LinuxDoSidebarTags.GUEST.map { name ->
            byName[name] ?: CommunityTag(name, 0)
        }
    }
}

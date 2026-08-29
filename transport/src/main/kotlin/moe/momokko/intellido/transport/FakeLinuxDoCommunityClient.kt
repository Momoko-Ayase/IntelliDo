package moe.momokko.intellido.transport

import moe.momokko.intellido.domain.catalog.CommunityAbout
import moe.momokko.intellido.domain.catalog.CommunityBadge
import moe.momokko.intellido.domain.catalog.CommunityCategory
import moe.momokko.intellido.domain.catalog.CommunityGroup
import moe.momokko.intellido.domain.catalog.CommunityTag
import moe.momokko.intellido.domain.catalog.ProfileBadge
import moe.momokko.intellido.domain.catalog.ProfileCategoryStat
import moe.momokko.intellido.domain.catalog.ProfileField
import moe.momokko.intellido.domain.catalog.ProfileLink
import moe.momokko.intellido.domain.catalog.ProfilePeer
import moe.momokko.intellido.domain.catalog.ProfileTopicItem
import moe.momokko.intellido.domain.catalog.PublicMember
import moe.momokko.intellido.domain.catalog.PublicProfile
import moe.momokko.intellido.domain.catalog.PublicProfileSummary
import moe.momokko.intellido.domain.search.SearchHit
import moe.momokko.intellido.domain.session.MemberSession
import moe.momokko.intellido.domain.site.SiteSettings
import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.domain.topic.TopicFind
import moe.momokko.intellido.domain.topic.PostBoost
import moe.momokko.intellido.domain.topic.PostReaction
import moe.momokko.intellido.domain.topic.ReplyTo
import moe.momokko.intellido.domain.topic.TopicPost
import moe.momokko.intellido.domain.topic.TopicPoster
import moe.momokko.intellido.domain.topic.TopicThread
import java.time.Instant

/**
 * Completely local LINUX DO stand-in for tests and anonymous native reading.
 */
class FakeLinuxDoCommunityClient(
    private val topics: List<HomeTopic> = defaultTopics,
) : LinuxDoCommunityClient {
    @Volatile
    private var session: MemberSession = MemberSession.Anonymous

    fun adoptSession(next: MemberSession) {
        session = next
    }
    override fun loadHomeTopics(page: Int): List<HomeTopic> = pageOf(topics + moreTopics, page)

    override fun loadHotTopics(page: Int): List<HomeTopic> =
        pageOf((topics + moreTopics).sortedByDescending { it.replyCount }, page)

    override fun loadTopTopics(page: Int): List<HomeTopic> =
        pageOf((topics + moreTopics).sortedByDescending { it.views }, page)

    override fun loadTopic(id: Long): TopicThread {
        val topic = (topics + moreTopics).firstOrNull { it.id == id }
            ?: error("unknown fake topic $id")
        val posts = defaultPosts[id] ?: listOf(
            TopicPost(
                id = id * 10,
                postNumber = 1,
                username = topic.authorUsername,
                cookedHtml = "<p>${topic.title}</p>",
                plainText = topic.title,
                createdAt = topic.lastPostedAt,
            ),
        )
        val stream = extraPosts[id]?.let { extra -> posts.map { it.id } + extra.map { it.id } }
            ?: posts.map { it.id }
        return TopicThread(topic, posts, stream)
    }

    override fun loadNextPosts(thread: TopicThread, limit: Int): TopicThread {
        val extra = extraPosts[thread.topic.id].orEmpty()
        if (extra.isEmpty()) {
            return thread
        }
        val have = thread.posts.map { it.id }.toSet()
        val added = extra.filter { it.id !in have }.take(limit.coerceAtLeast(1))
        if (added.isEmpty()) {
            return thread
        }
        val byId = (thread.posts + added).associateBy { it.id }
        val ordered = thread.streamIds.mapNotNull { byId[it] }.ifEmpty { thread.posts + added }
        return thread.copy(posts = ordered)
    }

    override fun loadPostsAround(thread: TopicThread, streamIndex: Int, limit: Int): TopicThread {
        val extra = extraPosts[thread.topic.id].orEmpty()
        val all = (thread.posts + extra).associateBy { it.id }
        if (thread.streamIds.isEmpty()) {
            return thread
        }
        val center = streamIndex.coerceIn(0, thread.streamIds.lastIndex)
        val start = (center - limit.coerceAtLeast(1) / 2).coerceAtLeast(0)
        val end = (start + limit.coerceAtLeast(1)).coerceAtMost(thread.streamIds.size)
        val have = thread.posts.map { it.id }.toSet()
        val added = thread.streamIds.subList(start, end).mapNotNull { all[it] }.filter { it.id !in have }
        if (added.isEmpty()) {
            return thread
        }
        val byId = (thread.posts + added).associateBy { it.id }
        val ordered = thread.streamIds.mapNotNull { byId[it] }.ifEmpty { thread.posts + added }
        return thread.copy(posts = ordered)
    }

    override fun loadRemainingPosts(thread: TopicThread): TopicThread =
        loadNextPosts(thread, Int.MAX_VALUE)

    override fun loadCategories(): List<CommunityCategory> = defaultCategories

    override fun loadCategoryTopics(categoryId: Long, page: Int): List<HomeTopic> {
        val category = defaultCategories.firstOrNull { it.id == categoryId } ?: return emptyList()
        return pageOf(topics.filter { it.categoryName == category.name }, page)
    }

    override fun loadTags(): List<CommunityTag> = defaultTags

    override fun loadTagTopics(tag: String, page: Int): List<HomeTopic> =
        pageOf(
            topics.filter { topic ->
                defaultTopicTags[topic.id].orEmpty().contains(tag)
            },
            page,
        )

    private fun pageOf(all: List<HomeTopic>, page: Int): List<HomeTopic> {
        val start = page.coerceAtLeast(0) * PAGE_SIZE
        if (start >= all.size) {
            return emptyList()
        }
        return all.subList(start, (start + PAGE_SIZE).coerceAtMost(all.size))
    }

    override fun loadGroups(): List<CommunityGroup> = defaultGroups.filter { it.publicVisible }

    override fun loadBadges(): List<CommunityBadge> = defaultBadges

    override fun loadMembers(): List<PublicMember> = defaultMembers

    override fun loadAbout(): CommunityAbout = defaultAbout

    override fun searchPublic(query: String): List<HomeTopic> {
        val needle = query.trim()
        if (needle.isEmpty()) {
            return emptyList()
        }
        return topics.filter { topic ->
            topic.title.contains(needle, ignoreCase = true) ||
                topic.slug.contains(needle, ignoreCase = true) ||
                (topic.categoryName?.contains(needle, ignoreCase = true) == true)
        }
    }

    override fun searchHits(query: String): List<SearchHit> {
        val needle = query.trim()
        if (needle.isEmpty()) {
            return emptyList()
        }
        val topicHits = searchPublic(needle).map { topic ->
            SearchHit(topic.title, topic.categoryName.orEmpty(), topic.id, slug = topic.slug)
        }
        val postHits = (defaultPosts.values.flatten() + extraPosts.values.flatten()).mapNotNull { post ->
            if (!post.plainText.contains(needle, ignoreCase = true)) {
                return@mapNotNull null
            }
            val topic = (topics + moreTopics).firstOrNull { candidate ->
                defaultPosts[candidate.id].orEmpty().any { it.id == post.id } ||
                    extraPosts[candidate.id].orEmpty().any { it.id == post.id }
            } ?: return@mapNotNull null
            SearchHit(topic.title, post.plainText, topic.id, post.postNumber, post.username, topic.slug)
        }
        return topicHits + postHits
    }

    override fun searchTopic(topicId: Long, query: String): List<SearchHit> {
        val posts = defaultPosts[topicId].orEmpty() + extraPosts[topicId].orEmpty()
        val topic = (topics + moreTopics).firstOrNull { it.id == topicId }
        return TopicFind.search(posts, query).map { hit ->
            SearchHit(topic?.title ?: "话题", hit.snippet, topicId, hit.postNumber, slug = topic?.slug)
        }
    }

    override fun loadSiteSettings(): SiteSettings = SiteSettings()

    override fun loadPublicProfile(username: String): PublicProfile {
        val key = username.trim()
        return defaultProfiles[key] ?: defaultProfiles[key.lowercase()]
            ?: error("unknown fake member $username")
    }

    override fun loadCurrentSession(): MemberSession = session

    override fun loadCreatedTopics(username: String, page: Int): List<HomeTopic> {
        val name = username.trim()
        if (name.isEmpty()) {
            return emptyList()
        }
        val authored = (topics + moreTopics).filter { topic ->
            topic.authorUsername.equals(name, ignoreCase = true)
        }
        return pageOf(authored, page)
    }

    override fun signOutRemote() {
        session = MemberSession.Anonymous
    }

    override fun loadPostReplies(postId: Long): List<TopicPost> {
        if (postId != 1001L) {
            return emptyList()
        }
        return listOfNotNull(
            defaultPosts[101]?.getOrNull(1),
            extraPosts[101]?.firstOrNull(),
        )
    }

    override fun loadTopicPosts(topicId: Long, postIds: List<Long>, title: String): List<TopicPost> {
        val want = postIds.toSet()
        val first = defaultPosts[topicId].orEmpty()
        val extra = extraPosts[topicId].orEmpty()
        return (first + extra).filter { it.id in want }
    }

    companion object {
        const val LOCAL_ORIGIN: String = "intellido.test"
        const val PAGE_SIZE: Int = 3

        val HELPER_SESSION: MemberSession.SignedIn = MemberSession.SignedIn(
            username = "helper",
            trustLevel = 2,
            id = 2,
            name = "助手",
            avatarTemplate = "/user_avatar/linux.do/helper/{size}/2.png",
        )

        val moreTopics: List<HomeTopic> = listOf(
            HomeTopic(
                id = 104,
                title = "第二页的话题",
                slug = "second-page-topic",
                postsCount = 1,
                replyCount = 0,
                categoryName = "使用指南",
                authorUsername = "helper",
                lastPostedAt = Instant.parse("2026-08-22T04:00:00Z"),
                categoryIcon = "book",
                tags = listOf("faq"),
                views = 12,
                posters = listOf(TopicPoster("helper")),
            ),
            HomeTopic(
                id = 105,
                title = "滚动加载更多",
                slug = "scroll-for-more",
                postsCount = 1,
                replyCount = 0,
                categoryName = "公告",
                authorUsername = "system",
                lastPostedAt = Instant.parse("2026-08-22T05:00:00Z"),
                categoryColor = "F6C344",
                categoryIcon = "comments",
                tags = listOf("intellido"),
                views = 8,
                posters = listOf(TopicPoster("system")),
            ),
            DiscourseFormatPreview.topic,
        )

        val defaultCategories: List<CommunityCategory> = listOf(
            CommunityCategory(1, "公告", "announcements", "站点公告", 2, readRestricted = false, color = "F6C344", icon = "comments"),
            CommunityCategory(2, "使用指南", "guides", "公开的使用说明", 1, readRestricted = false, icon = "book"),
        )

        val defaultTags: List<CommunityTag> = listOf(
            CommunityTag("intellido", 2),
            CommunityTag("faq", 1),
            CommunityTag("人工智能", 8),
            CommunityTag("公告", 3),
            CommunityTag("原创", 1),
            CommunityTag("快问快答", 4),
            CommunityTag("抽奖", 2),
            CommunityTag("精华神帖", 1),
            CommunityTag("集中帖", 1),
        )

        val defaultTopicTags: Map<Long, List<String>> = mapOf(
            101L to listOf("intellido"),
            102L to listOf("faq"),
            103L to listOf("intellido"),
        )

        val defaultGroups: List<CommunityGroup> = listOf(
            CommunityGroup(1, "everyone", "所有人", 3, publicVisible = true, bioHtml = "<p>公开群组</p>"),
            CommunityGroup(2, "staff", "工作人员", 1, publicVisible = false),
        )

        val defaultBadges: List<CommunityBadge> = listOf(
            CommunityBadge(1, "首次发帖", "发布第一篇帖子", icon = "pencil", grantCount = 12, badgeType = "铜"),
            CommunityBadge(2, "善解人意", "收到多个赞", grantCount = 4, badgeType = "银"),
        )

        val defaultMembers: List<PublicMember> = listOf(
            PublicMember(1, "system", "系统", 3, avatarTemplate = "/user_avatar/linux.do/system/{size}/1.png", title = "系统管理员", likesReceived = 80),
            PublicMember(2, "helper", "助手", 2, likesReceived = 4),
        )

        val defaultProfiles: Map<String, PublicProfile> = mapOf(
            "system" to PublicProfile(
                id = 1,
                username = "system",
                displayName = "系统",
                title = "系统管理员",
                bioHtml = "<p>IntelliDo 本地假用户。</p>",
                trustLevel = 3,
                avatarTemplate = "/user_avatar/linux.do/system/{size}/1_2.png",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                badgeCount = 2,
            ),
            "helper" to PublicProfile(
                id = 2,
                username = "helper",
                displayName = "助手",
                title = "使用向导",
                bioHtml = "<p>IntelliDo 本地假用户，负责公开指南。</p>",
                trustLevel = 2,
                avatarTemplate = "/user_avatar/linux.do/helper/{size}/2.png",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                badgeCount = 2,
                location = "测试城",
                website = "https://example.test",
                lastPostedAt = Instant.parse("2026-08-22T02:00:00Z"),
                lastSeenAt = Instant.parse("2026-08-22T03:00:00Z"),
                profileViews = 48,
                publicFields = listOf(ProfileField("所属机构", "IntelliDo 测试")),
                followerCount = 3,
                gamificationScore = 120,
                statusEmoji = "slight_smile",
                statusDescription = "在读指南",
                featuredBadges = listOf(ProfileBadge(1, "首次发帖", "发布第一篇帖子", "pencil")),
                summary = PublicProfileSummary(
                    daysVisited = 9,
                    timeReadSeconds = 3600,
                    recentTimeReadSeconds = 600,
                    topicsEntered = 12,
                    postsRead = 40,
                    likesGiven = 6,
                    likesReceived = 4,
                    topicCount = 1,
                    postCount = 3,
                    solvedCount = 1,
                    replies = listOf(
                        ProfileTopicItem(
                            102,
                            "如何阅读话题",
                            8,
                            Instant.parse("2026-08-22T02:00:00Z"),
                            2,
                            "how-to-read-topics",
                        ),
                    ),
                    topics = listOf(
                        ProfileTopicItem(
                            101,
                            "欢迎使用 IntelliDo",
                            120,
                            Instant.parse("2026-08-22T01:00:00Z"),
                            slug = "welcome-to-intellido",
                        ),
                    ),
                    links = listOf(ProfileLink("https://example.test/docs", "IntelliDo 说明", 3, 101)),
                    topCategories = listOf(
                        ProfileCategoryStat(1, "公告", "F6C344", 2, 4, "announcements", "comments"),
                    ),
                    badges = listOf(ProfileBadge(1, "首次发帖", "发布第一篇帖子", "pencil")),
                    mostLikedBy = listOf(ProfilePeer(1, "system", "系统", count = 4)),
                    mostLiked = listOf(ProfilePeer(1, "system", "系统", count = 2)),
                    mostRepliedTo = listOf(ProfilePeer(1, "system", "系统", count = 1)),
                ),
            ),
        )

        val defaultAbout: CommunityAbout = CommunityAbout(
            title = "LINUX DO",
            description = "这是本地 Fake 社区说明，供 IntelliDo 匿名只读浏览。",
            staffUsernames = listOf("system"),
            topicCount = 10,
            postCount = 20,
            userCount = 5,
            likeCount = 30,
            admins = listOf(PublicMember(1, "system", "系统", 4, title = "系统管理员")),
            moderators = listOf(PublicMember(2, "helper", "助手", 3)),
            faqUrl = "https://linux.do/faq",
            guidelinesUrl = "https://linux.do/guidelines",
            tosUrl = "https://linux.do/tos",
            privacyUrl = "https://linux.do/privacy",
        )

        val defaultTopics: List<HomeTopic> = listOf(
            HomeTopic(
                id = 101,
                title = "欢迎使用 IntelliDo",
                slug = "welcome-to-intellido",
                postsCount = 4,
                replyCount = 3,
                categoryName = "公告",
                authorUsername = "system",
                lastPostedAt = Instant.parse("2026-08-22T01:20:00Z"),
                categoryColor = "F6C344",
                categoryIcon = "comments",
                tags = listOf("intellido", "公告"),
                views = 29_700,
                pinned = true,
                posters = listOf(
                    TopicPoster("system", "/user_avatar/linux.do/system/{size}/1_2.png"),
                    TopicPoster("helper", "/user_avatar/linux.do/helper/{size}/2.png"),
                ),
                likeCount = 120,
                participantCount = 8,
                linkCount = 1,
                wordCount = 240,
            ),
            HomeTopic(
                id = 102,
                title = "如何阅读话题",
                slug = "how-to-read-topics",
                postsCount = 8,
                replyCount = 7,
                categoryName = "使用指南",
                authorUsername = "helper",
                lastPostedAt = Instant.parse("2026-08-22T02:00:00Z"),
                categoryIcon = "book",
                tags = listOf("faq"),
                views = 113,
                posters = listOf(TopicPoster("helper")),
            ),
            HomeTopic(
                id = 103,
                title = "非官方客户端说明",
                slug = "unofficial-client-notice",
                postsCount = 2,
                replyCount = 1,
                categoryName = "公告",
                authorUsername = "system",
                lastPostedAt = Instant.parse("2026-08-22T03:00:00Z"),
                categoryColor = "F6C344",
                categoryIcon = "comments",
                tags = listOf("intellido"),
                views = 6_000,
                posters = listOf(TopicPoster("system")),
            ),
        )

        val defaultPosts: Map<Long, List<TopicPost>> = mapOf(
            101L to listOf(
                TopicPost(
                    id = 1001,
                    postNumber = 1,
                    username = "system",
                    displayName = "系统",
                    cookedHtml = """
                        <p>欢迎使用 <strong>IntelliDo</strong>。<img src="https://linux.do/images/emoji/twitter/slight_smile.png?v=12" class="emoji" alt=":slight_smile:"></p>
                        <aside class="quote" data-username="helper"><blockquote><p>Home 是永久标签页。</p></blockquote></aside>
                        <p>分类和标签会出现在标题下。</p>
                    """.trimIndent(),
                    plainText = "欢迎使用 IntelliDo。Home 是永久标签页。分类和标签会出现在标题下。",
                    createdAt = Instant.parse("2026-08-22T01:00:00Z"),
                    userTitle = "系统管理员",
                    likeCount = 42,
                    replyCount = 3,
                    staff = true,
                    version = 2,
                    flairName = "admins",
                    flairUrl = "shield-halved",
                    reactions = listOf(
                        PostReaction("heart", 36),
                        PostReaction("+1", 6),
                    ),
                    reactionUsersCount = 42,
                    boosts = listOf(
                        PostBoost(11, "<p>前排</p>", "helper", "助手", "/user_avatar/linux.do/helper/{size}/2.png"),
                        PostBoost(12, "<p>前排合影</p>", "reader", avatarTemplate = "/user_avatar/linux.do/reader/{size}/3.png"),
                        PostBoost(13, "<p>前排合影</p>", "guest", avatarTemplate = "/user_avatar/linux.do/guest/{size}/4.png"),
                    ),
                ),
                TopicPost(
                    id = 1002,
                    postNumber = 2,
                    username = "helper",
                    displayName = "助手",
                    cookedHtml = "<p>单击话题会在预览标签页中打开。</p><ul><li>分类</li><li>标签</li></ul>",
                    plainText = "Home 是永久标签页。单击话题会在预览标签页中打开。",
                    createdAt = Instant.parse("2026-08-22T01:10:00Z"),
                    likeCount = 4,
                    replyTo = ReplyTo(1, "system", "/user_avatar/linux.do/system/{size}/1_2.png"),
                ),
            ),
            102L to listOf(
                TopicPost(
                    id = 2001,
                    postNumber = 1,
                    username = "helper",
                    cookedHtml = """
                        <p>话题以连续帖子列表阅读，不使用网页分页。</p>
                        <p>有人在亮眼的数据面前逐渐疯狂，而我们应该在数据面前冷静。我们需要思考的是，如何让每一次的数据不成为顶峰，而只作为新的起点。未来，才是我们需要聚焦关注的。</p>
                        <p>这篇帖子，正是我对社区下一个阶段的思考，它将包含并揭示我的思考过程。我之所以将思考过程铺开给大家，是希望大家能理解并检阅我的思考路径。佬友的能力是无限的，如果能指出错误和不足之处，那是我最希望看到的事。</p>
                        <p>让我们把目光移到社区之外的互联网，我认为可以总结为“四化”：功利化、情绪化、同质化、娱乐化。如果你长期在互联网浏览，相信你能对我提出的这“四化”感同身受。</p>
                    """.trimIndent(),
                    plainText = "话题以连续帖子列表阅读，不使用网页分页。",
                    createdAt = Instant.parse("2026-08-22T02:00:00Z"),
                ),
            ),
            103L to listOf(
                TopicPost(
                    id = 3001,
                    postNumber = 1,
                    username = "system",
                    cookedHtml = "<p>IntelliDo 是<strong>非官方 LINUX DO 客户端</strong>。</p>",
                    plainText = "IntelliDo 是非官方 LINUX DO 客户端。",
                    createdAt = Instant.parse("2026-08-22T03:00:00Z"),
                ),
            ),
            DiscourseFormatPreview.TOPIC_ID to DiscourseFormatPreview.posts,
        )

        val extraPosts: Map<Long, List<TopicPost>> = mapOf(
            101L to listOf(
                TopicPost(
                    id = 1003,
                    postNumber = 3,
                    username = "reader",
                    displayName = "读者",
                    cookedHtml = "<p>第三篇，滚动后才会加载。</p>",
                    plainText = "第三篇，滚动后才会加载。",
                    createdAt = Instant.parse("2026-08-22T01:20:00Z"),
                    likeCount = 1,
                    replyTo = ReplyTo(2, "helper"),
                ),
                TopicPost(
                    id = 1004,
                    postNumber = 4,
                    username = "system",
                    displayName = "系统",
                    cookedHtml = "",
                    plainText = "pinned_globally",
                    createdAt = Instant.parse("2026-08-22T01:30:00Z"),
                    staff = true,
                    postType = 3,
                    actionCode = "pinned_globally",
                ),
            ),
            102L to (2..8).map { number ->
                TopicPost(
                    id = 2000L + number,
                    postNumber = number,
                    username = "reader",
                    cookedHtml = "<p>第${number}篇</p>",
                    plainText = "第${number}篇",
                    createdAt = Instant.parse("2026-08-22T02:00:00Z").plusSeconds(number * 60L),
                )
            },
        )
    }
}

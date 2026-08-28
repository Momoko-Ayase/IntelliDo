package moe.momokko.intellido.transport

import moe.momokko.intellido.domain.catalog.CommunityAbout
import moe.momokko.intellido.domain.catalog.CommunityCategories
import moe.momokko.intellido.domain.catalog.CommunityBadge
import moe.momokko.intellido.domain.catalog.CommunityCategory
import moe.momokko.intellido.domain.catalog.CommunityGroup
import moe.momokko.intellido.domain.catalog.CommunityTag
import moe.momokko.intellido.domain.catalog.PublicMember
import moe.momokko.intellido.domain.catalog.PublicProfile
import moe.momokko.intellido.domain.search.SearchHit
import moe.momokko.intellido.domain.site.SiteSettings
import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.domain.topic.TopicPost
import moe.momokko.intellido.domain.topic.TopicThread

/**
 * Native community client over structured LINUX DO JSON. Production uses a JCEF
 * fetcher; tests inject fixture responses. There is no JVM HTTP client.
 */
class BridgedLinuxDoCommunityClient(
    private val fetch: LinuxDoJsonFetcher,
    private val mapper: DiscourseJsonMapper,
) : LinuxDoCommunityClient {
    constructor(fetch: LinuxDoJsonFetcher) : this(fetch, DiscourseJsonMapper())

    @Volatile
    private var categoryCache: List<CommunityCategory>? = null
    private val categoryLock = Any()
    @Volatile
    private var userFieldNames: Map<Int, String>? = null
    private val userFieldLock = Any()

    override fun loadHomeTopics(page: Int): List<HomeTopic> =
        mapper.homeTopics(fetch.get(LinuxDoUrls.latest(page)), loadCategories())

    override fun loadHotTopics(page: Int): List<HomeTopic> =
        mapper.homeTopics(fetch.get(LinuxDoUrls.hot(page)), loadCategories())

    override fun loadTopTopics(page: Int): List<HomeTopic> =
        mapper.homeTopics(fetch.get(LinuxDoUrls.top(page)), loadCategories())

    override fun loadTopic(id: Long): TopicThread =
        mapper.topicThread(fetch.get(LinuxDoUrls.topic(id)), loadCategories())

    override fun loadRemainingPosts(thread: TopicThread): TopicThread {
        val have = thread.posts.map { it.id }.toSet()
        val missing = thread.streamIds.filterNot { it in have }
        if (missing.isEmpty()) {
            return thread
        }
        val extra = missing.chunked(20).flatMap { chunk ->
            fetchPostChunk(thread, chunk)
        }
        return mergePosts(thread, extra)
    }

    override fun loadNextPosts(thread: TopicThread, limit: Int): TopicThread {
        val have = thread.posts.map { it.id }.toSet()
        val missing = thread.streamIds.filterNot { it in have }.take(limit.coerceAtLeast(1))
        if (missing.isEmpty()) {
            return thread
        }
        return mergePosts(thread, fetchPostChunk(thread, missing))
    }

    override fun loadPostsAround(thread: TopicThread, streamIndex: Int, limit: Int): TopicThread {
        if (thread.streamIds.isEmpty()) {
            return thread
        }
        val last = thread.streamIds.lastIndex
        val center = streamIndex.coerceIn(0, last)
        val start = (center - limit.coerceAtLeast(1) / 2).coerceAtLeast(0)
        val end = (start + limit.coerceAtLeast(1)).coerceAtMost(thread.streamIds.size)
        val have = thread.posts.map { it.id }.toSet()
        val missing = thread.streamIds.subList(start, end).filterNot { it in have }
        if (missing.isEmpty()) {
            return thread
        }
        return mergePosts(thread, fetchPostChunk(thread, missing))
    }

    private fun fetchPostChunk(thread: TopicThread, ids: List<Long>): List<TopicPost> =
        mapper.extraPosts(
            fetch.get(LinuxDoUrls.topicPosts(thread.topic.id, ids)),
            thread.topic.title,
        )

    private fun mergePosts(thread: TopicThread, extra: List<TopicPost>): TopicThread {
        if (extra.isEmpty()) {
            return thread
        }
        val byId = (thread.posts + extra).associateBy { it.id }
        val ordered = thread.streamIds.mapNotNull { byId[it] }.ifEmpty { thread.posts }
        return thread.copy(posts = ordered)
    }

    override fun loadCategories(): List<CommunityCategory> {
        categoryCache?.let { return it }
        synchronized(categoryLock) {
            categoryCache?.let { return it }
            val top = runCatching {
                mapper.categories(fetch.get(LinuxDoUrls.categories())).filterNot { it.readRestricted }
            }.getOrDefault(emptyList())
            if (top.isNotEmpty()) {
                val merged = CommunityCategories.sidebarOrder(top, top)
                categoryCache = merged
                return merged
            }
            val all = runCatching {
                mapper.categories(fetch.get(LinuxDoUrls.site())).filterNot { it.readRestricted }
            }.getOrDefault(emptyList())
            val merged = CommunityCategories.sidebarOrder(all, all)
            if (merged.isNotEmpty()) {
                categoryCache = merged
            }
            return merged
        }
    }

    override fun loadCategoryTopics(categoryId: Long, page: Int): List<HomeTopic> =
        mapper.homeTopics(fetch.get(LinuxDoUrls.categoryLatest(categoryId, page)), loadCategories())

    override fun loadTags(): List<CommunityTag> =
        mapper.tags(fetch.get(LinuxDoUrls.tags()))

    override fun loadTagTopics(tag: String, page: Int): List<HomeTopic> =
        mapper.homeTopics(fetch.get(LinuxDoUrls.tag(tag, page)), loadCategories())

    override fun loadGroups(): List<CommunityGroup> =
        mapper.groups(fetch.get(LinuxDoUrls.groups()))

    override fun loadBadges(): List<CommunityBadge> =
        mapper.badges(fetch.get(LinuxDoUrls.badges()))

    override fun loadMembers(): List<PublicMember> =
        mapper.members(fetch.get(LinuxDoUrls.directoryItems()))

    override fun loadAbout(): CommunityAbout =
        mapper.about(fetch.get(LinuxDoUrls.about()))

    override fun searchPublic(query: String): List<HomeTopic> {
        val needle = query.trim()
        if (needle.isEmpty()) {
            return emptyList()
        }
        return mapper.searchTopics(fetch.get(LinuxDoUrls.search(needle)), loadCategories())
    }

    override fun searchHits(query: String): List<SearchHit> {
        val needle = query.trim()
        if (needle.isEmpty()) {
            return emptyList()
        }
        return mapper.searchHits(fetch.get(LinuxDoUrls.search(needle)), loadCategories())
    }

    override fun searchTopic(topicId: Long, query: String): List<SearchHit> {
        val needle = query.trim()
        if (needle.isEmpty()) {
            return emptyList()
        }
        return mapper.searchHits(fetch.get(LinuxDoUrls.searchTopic(topicId, needle)), loadCategories())
    }

    override fun loadSiteSettings(): SiteSettings =
        runCatching { mapper.siteSettings(fetch.get(LinuxDoUrls.site())) }.getOrDefault(SiteSettings())

    override fun loadPublicProfile(username: String): PublicProfile {
        val profile = mapper.publicProfile(fetch.get(LinuxDoUrls.user(username)), loadUserFieldNames())
        val summary = runCatching { mapper.userSummary(fetch.get(LinuxDoUrls.userSummary(username))) }.getOrNull()
        return if (summary == null) profile else profile.copy(summary = summary)
    }

    private fun loadUserFieldNames(): Map<Int, String> {
        userFieldNames?.let { return it }
        synchronized(userFieldLock) {
            userFieldNames?.let { return it }
            val names = runCatching { mapper.userFieldNames(fetch.get(LinuxDoUrls.site())) }.getOrDefault(emptyMap())
            userFieldNames = names
            return names
        }
    }

    override fun loadPostReplies(postId: Long): List<TopicPost> =
        mapper.extraPosts(fetch.get(LinuxDoUrls.postReplies(postId)))

    override fun loadTopicPosts(topicId: Long, postIds: List<Long>, title: String): List<TopicPost> {
        if (postIds.isEmpty()) {
            return emptyList()
        }
        return mapper.extraPosts(fetch.get(LinuxDoUrls.topicPosts(topicId, postIds)), title)
    }
}

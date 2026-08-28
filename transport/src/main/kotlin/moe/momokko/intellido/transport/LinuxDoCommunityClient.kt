package moe.momokko.intellido.transport

import moe.momokko.intellido.domain.catalog.CommunityAbout
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
 * Structured LINUX DO community operations used by native UI.
 *
 * Production implementations must execute through the JCEF transport boundary.
 * Tests and the anonymous read-only slice use a local fake that never contacts linux.do.
 */
interface LinuxDoCommunityClient {
    fun loadHomeTopics(page: Int = 0): List<HomeTopic>

    fun loadHotTopics(page: Int = 0): List<HomeTopic> = loadHomeTopics(page)

    fun loadTopTopics(page: Int = 0): List<HomeTopic> = loadHomeTopics(page)

    fun loadTopic(id: Long): TopicThread

    fun loadRemainingPosts(thread: TopicThread): TopicThread = thread

    fun loadNextPosts(thread: TopicThread, limit: Int = 20): TopicThread = loadRemainingPosts(thread)

    fun loadPostsAround(thread: TopicThread, streamIndex: Int, limit: Int = 20): TopicThread =
        loadNextPosts(thread, limit)

    fun loadCategories(): List<CommunityCategory>

    fun loadCategoryTopics(categoryId: Long, page: Int = 0): List<HomeTopic>

    fun loadTags(): List<CommunityTag>

    fun loadTagTopics(tag: String, page: Int = 0): List<HomeTopic>

    fun loadGroups(): List<CommunityGroup>

    fun loadBadges(): List<CommunityBadge>

    fun loadMembers(): List<PublicMember>

    fun loadAbout(): CommunityAbout

    fun searchPublic(query: String): List<HomeTopic>

    fun searchHits(query: String): List<SearchHit> =
        searchPublic(query).map { topic ->
            SearchHit(topic.title, topic.categoryName.orEmpty(), topic.id, slug = topic.slug)
        }

    fun searchTopic(topicId: Long, query: String): List<SearchHit> = emptyList()

    fun loadSiteSettings(): SiteSettings = SiteSettings()

    fun loadPublicProfile(username: String): PublicProfile

    fun loadPostReplies(postId: Long): List<TopicPost> = emptyList()

    fun loadTopicPosts(topicId: Long, postIds: List<Long>, title: String = "post"): List<TopicPost> = emptyList()
}

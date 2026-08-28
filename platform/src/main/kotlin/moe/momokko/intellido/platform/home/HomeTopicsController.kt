package moe.momokko.intellido.platform.home

import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.platform.live.IncomingLatestTopics
import moe.momokko.intellido.transport.LinuxDoCommunityClient

class HomeTopicsController(
    private val client: LinuxDoCommunityClient,
    private val state: HomeTopicListState = HomeTopicListState(),
    private val incoming: IncomingLatestTopics = IncomingLatestTopics(),
) {
    private var kind: Kind = Kind.Latest
    private var page: Int = 0
    private var more: Boolean = true
    private var categoryId: Long = 0
    private var tagName: String = ""

    fun load(): List<HomeTopic> = replace(Kind.Latest) { client.loadHomeTopics(0) }

    fun loadHot(): List<HomeTopic> = replace(Kind.Hot) { client.loadHotTopics(0) }

    fun loadTop(): List<HomeTopic> = replace(Kind.Top) { client.loadTopTopics(0) }

    fun loadCategory(categoryId: Long): List<HomeTopic> {
        this.categoryId = categoryId
        return replace(Kind.Category) { client.loadCategoryTopics(categoryId, 0) }
    }

    fun loadTag(name: String): List<HomeTopic> {
        tagName = name
        return replace(Kind.Tag) { client.loadTagTopics(name, 0) }
    }

    fun search(query: String): List<HomeTopic> {
        val topics = if (query.isBlank()) {
            return load()
        } else {
            client.searchPublic(query)
        }
        kind = Kind.Search
        page = 0
        more = false
        state.replaceAll(topics)
        return topics
    }

    fun loadMore(): List<HomeTopic> {
        if (!more || kind == Kind.Search) {
            return emptyList()
        }
        val extra = when (kind) {
            Kind.Latest -> client.loadHomeTopics(page + 1)
            Kind.Hot -> client.loadHotTopics(page + 1)
            Kind.Top -> client.loadTopTopics(page + 1)
            Kind.Category -> client.loadCategoryTopics(categoryId, page + 1)
            Kind.Tag -> client.loadTagTopics(tagName, page + 1)
            Kind.Search -> emptyList()
        }
        if (extra.isEmpty()) {
            more = false
            return emptyList()
        }
        page += 1
        state.append(extra)
        return extra
    }

    fun hasMore(): Boolean = more && kind != Kind.Search

    fun titles(): List<String> = state.titles()

    fun snapshot(): List<HomeTopic> = state.snapshot()

    fun noteIncoming(topicId: Long) {
        incoming.note(topicId)
    }

    fun incomingCount(): Int = incoming.count()

    fun clearIncoming() {
        incoming.clear()
    }

    fun currentKind(): String = kind.name

    fun currentCategoryId(): Long = categoryId

    private fun replace(next: Kind, block: () -> List<HomeTopic>): List<HomeTopic> {
        kind = next
        page = 0
        more = true
        incoming.clear()
        val topics = block()
        state.replaceAll(topics)
        return topics
    }

    private enum class Kind { Latest, Hot, Top, Category, Tag, Search }
}

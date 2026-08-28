package moe.momokko.intellido.transport

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import moe.momokko.intellido.domain.site.SiteSettings
import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.domain.topic.PostBoost
import moe.momokko.intellido.domain.topic.PostReaction
import moe.momokko.intellido.domain.topic.ReplyTo
import moe.momokko.intellido.domain.topic.TopicPost
import moe.momokko.intellido.domain.topic.TopicPoster
import moe.momokko.intellido.domain.topic.TopicThread
import java.time.Instant

class DiscourseJsonMapper {
    fun categories(json: String): List<CommunityCategory> {
        val root = jsonObject(json)
        val list = root.obj("category_list")?.arr("categories")
            ?: root.arr("categories")
            ?: return emptyList()
        return list.objects().mapNotNull { item ->
            val id = item.long("id") ?: return@mapNotNull null
            val name = item.str("name") ?: return@mapNotNull null
            CommunityCategory(
                id = id,
                name = name,
                slug = item.str("slug") ?: id.toString(),
                description = item.str("description_text") ?: item.str("description"),
                topicCount = item.int("topic_count") ?: 0,
                readRestricted = item.bool("read_restricted") ?: false,
                color = item.str("color"),
                icon = item.str("icon"),
                parentId = item.long("parent_category_id"),
            )
        }
    }

    fun homeTopics(json: String, categories: List<CommunityCategory>): List<HomeTopic> {
        val root = jsonObject(json)
        val users = usersById(root.arr("users"))
        val categoryById = categories.associateBy { it.id }
        val topics = root.obj("topic_list")?.arr("topics") ?: error("LINUX DO JSON missing topic_list")
        return topics.objects().mapNotNull { item -> toHomeTopic(item, users, categoryById) }
    }

    fun topicThread(json: String, categories: List<CommunityCategory> = emptyList()): TopicThread {
        val root = jsonObject(json)
        val id = root.long("id") ?: error("LINUX DO topic JSON missing id")
        val title = root.str("title") ?: error("LINUX DO topic JSON missing title")
        val posts = root.obj("post_stream")?.arr("posts")?.objects().orEmpty()
        val firstUser = posts.firstOrNull()?.str("username") ?: "unknown"
        val category = root.long("category_id")?.let { categoryId -> categories.firstOrNull { it.id == categoryId } }
        val topic = HomeTopic(
            id = id,
            title = title,
            slug = root.str("slug") ?: id.toString(),
            postsCount = root.int("posts_count") ?: posts.size,
            replyCount = root.int("reply_count") ?: (posts.size - 1).coerceAtLeast(0),
            categoryName = category?.name,
            authorUsername = firstUser,
            lastPostedAt = root.instant("last_posted_at")
                ?: posts.mapNotNull { it.instant("created_at") }.maxOrNull()
                ?: Instant.EPOCH,
            categoryColor = category?.color,
            categoryIcon = category?.icon,
            tags = root.stringList("tags"),
            views = root.int("views") ?: 0,
            pinned = root.bool("pinned") ?: false,
            likeCount = root.int("like_count") ?: 0,
            participantCount = root.int("participant_count") ?: 0,
            linkCount = root.obj("details")?.arr("links")?.size() ?: 0,
            closed = root.bool("closed") ?: false,
            archived = root.bool("archived") ?: false,
            acceptedAnswer = root.hasAcceptedAnswer(),
            wordCount = root.int("word_count") ?: 0,
            createdAt = root.instant("created_at"),
        )
        val threadPosts = posts.mapNotNull { item -> toPost(item, title) }
        require(threadPosts.isNotEmpty()) { "LINUX DO topic JSON contained no posts" }
        val streamIds = root.obj("post_stream")?.longList("stream").orEmpty()
            .ifEmpty { threadPosts.map { it.id } }
        return TopicThread(
            topic,
            threadPosts,
            streamIds,
            messageBusLastId = root.long("message_bus_last_id") ?: -1,
        )
    }

    fun extraPosts(json: String, fallbackTitle: String = "post"): List<TopicPost> {
        val trimmed = json.trim()
        val posts = if (trimmed.startsWith("[")) {
            val parsed = JsonParser.parseString(trimmed)
            if (!parsed.isJsonArray) {
                emptyList()
            } else {
                parsed.asJsonArray.objects()
            }
        } else {
            val root = jsonObject(json)
            root.obj("post_stream")?.arr("posts")?.objects()
                ?: root.arr("posts")?.objects()
                ?: emptyList()
        }
        return posts.mapNotNull { item -> toPost(item, fallbackTitle) }
    }

    fun publicProfile(json: String, fieldNames: Map<Int, String> = emptyMap()): PublicProfile {
        val root = jsonObject(json)
        val user = root.obj("user") ?: error("LINUX DO user JSON missing user")
        val id = user.long("id") ?: error("LINUX DO user JSON missing id")
        val username = user.str("username") ?: error("LINUX DO user JSON missing username")
        val badges = badgeById(root)
        val userBadges = root.arr("user_badges")?.objects().orEmpty().associateBy { it.long("id") }
        val featured = user.longList("featured_user_badge_ids").mapNotNull { grantedId ->
            val granted = userBadges[grantedId] ?: return@mapNotNull null
            val badge = badges[granted.long("badge_id")] ?: return@mapNotNull null
            toBadge(badge, granted.int("count") ?: 1)
        }
        val status = user.obj("status")
        return PublicProfile(
            id = id,
            username = username,
            displayName = user.str("name")?.takeIf { it.isNotBlank() },
            title = user.str("title")?.takeIf { it.isNotBlank() },
            bioHtml = user.str("bio_cooked")?.takeIf { it.isNotBlank() },
            trustLevel = user.int("trust_level") ?: 0,
            avatarTemplate = user.str("avatar_template"),
            createdAt = user.instant("created_at"),
            badgeCount = user.int("badge_count") ?: 0,
            location = user.str("location")?.takeIf { it.isNotBlank() },
            website = user.str("website")?.takeIf { it.isNotBlank() },
            websiteName = user.str("website_name")?.takeIf { it.isNotBlank() },
            lastPostedAt = user.instant("last_posted_at"),
            lastSeenAt = user.instant("last_seen_at"),
            profileViews = (user.int("profile_view_count") ?: 0).coerceAtLeast(0),
            admin = user.bool("admin") ?: false,
            moderator = user.bool("moderator") ?: false,
            primaryGroupName = user.str("primary_group_name")?.takeIf { it.isNotBlank() },
            flairName = user.str("flair_name")?.takeIf { it.isNotBlank() },
            flairUrl = user.str("flair_url")?.takeIf { it.isNotBlank() },
            flairBgColor = user.str("flair_bg_color")?.takeIf { it.isNotBlank() },
            flairColor = user.str("flair_color")?.takeIf { it.isNotBlank() },
            publicFields = publicFields(user.obj("user_fields"), fieldNames),
            followerCount = (user.int("total_followers") ?: 0).coerceAtLeast(0),
            gamificationScore = (user.int("gamification_score") ?: 0).coerceAtLeast(0),
            statusEmoji = status?.str("emoji")?.takeIf { it.isNotBlank() },
            statusDescription = status?.str("description")?.takeIf { it.isNotBlank() },
            featuredBadges = featured,
        )
    }

    fun userFieldNames(json: String): Map<Int, String> {
        val fields = jsonObject(json).arr("user_fields") ?: return emptyMap()
        return fields.objects().mapNotNull { item ->
            if (item.bool("show_on_profile") != true) {
                return@mapNotNull null
            }
            val id = item.int("id") ?: return@mapNotNull null
            val name = item.str("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to name
        }.toMap()
    }

    fun userSummary(json: String): PublicProfileSummary {
        val root = jsonObject(json)
        val summary = root.obj("user_summary") ?: error("LINUX DO user summary JSON missing user_summary")
        val topics = root.arr("topics")?.objects().orEmpty().associateBy { it.long("id") }
        val badges = badgeById(root)
        val canSee = summary.bool("can_see_summary_stats") ?: true
        fun topicItem(
            id: Long,
            likeCount: Int,
            createdAt: Instant?,
            postNumber: Int?,
        ): ProfileTopicItem? {
            val topic = topics[id] ?: return null
            val title = topic.str("title") ?: topic.str("fancy_title") ?: return null
            return ProfileTopicItem(
                topicId = id,
                title = title,
                likeCount = likeCount,
                createdAt = createdAt ?: topic.instant("created_at"),
                postNumber = postNumber,
                slug = topic.str("slug"),
            )
        }
        val topicItems = summary.longList("topic_ids").mapNotNull { id ->
            val topic = topics[id] ?: return@mapNotNull null
            topicItem(id, topic.int("like_count") ?: 0, topic.instant("created_at"), null)
        }
        val replies = summary.arr("replies")?.objects().orEmpty().mapNotNull { item ->
            val id = item.long("topic_id") ?: return@mapNotNull null
            topicItem(id, item.int("like_count") ?: 0, item.instant("created_at"), item.int("post_number"))
        }
        val links = summary.arr("links")?.objects().orEmpty().mapNotNull { item ->
            val url = item.str("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ProfileLink(
                url = url,
                title = item.str("title")?.takeIf { it.isNotBlank() },
                clicks = item.int("clicks") ?: 0,
                topicId = item.long("topic_id"),
            )
        }
        val categories = summary.arr("top_categories")?.objects().orEmpty().mapNotNull { item ->
            val id = item.long("id") ?: return@mapNotNull null
            val name = item.str("name") ?: return@mapNotNull null
            ProfileCategoryStat(
                id = id,
                name = name,
                color = item.str("color"),
                topicCount = item.int("topic_count") ?: 0,
                postCount = item.int("post_count") ?: 0,
                slug = item.str("slug"),
                icon = item.str("icon"),
            )
        }
        val summaryBadges = summary.arr("badges")?.objects().orEmpty().mapNotNull { granted ->
            val badge = badges[granted.long("badge_id")] ?: return@mapNotNull null
            toBadge(badge, granted.int("count") ?: 1)
        }
        return PublicProfileSummary(
            canSeeStats = canSee,
            daysVisited = if (canSee) summary.int("days_visited") ?: 0 else 0,
            timeReadSeconds = if (canSee) summary.int("time_read") ?: 0 else 0,
            recentTimeReadSeconds = if (canSee) summary.int("recent_time_read") ?: 0 else 0,
            topicsEntered = if (canSee) summary.int("topics_entered") ?: 0 else 0,
            postsRead = if (canSee) summary.int("posts_read_count") ?: 0 else 0,
            likesGiven = if (canSee) summary.int("likes_given") ?: 0 else 0,
            likesReceived = if (canSee) summary.int("likes_received") ?: 0 else 0,
            topicCount = if (canSee) summary.int("topic_count") ?: 0 else 0,
            postCount = if (canSee) summary.int("post_count") ?: 0 else 0,
            solvedCount = if (canSee) summary.int("solved_count") ?: 0 else 0,
            replies = replies,
            topics = topicItems,
            links = links,
            topCategories = categories,
            badges = summaryBadges,
            mostRepliedTo = peers(summary.arr("most_replied_to_users")),
            mostLikedBy = peers(summary.arr("most_liked_by_users")),
            mostLiked = peers(summary.arr("most_liked_users")),
        )
    }

    private fun badgeById(root: JsonObject): Map<Long, JsonObject> =
        root.arr("badges")?.objects().orEmpty().mapNotNull { badge ->
            val id = badge.long("id") ?: return@mapNotNull null
            id to badge
        }.toMap()

    private fun toBadge(badge: JsonObject, count: Int): ProfileBadge? {
        val id = badge.long("id") ?: return null
        val name = badge.str("name")?.takeIf { it.isNotBlank() } ?: return null
        return ProfileBadge(
            id = id,
            name = name,
            description = stripTags(badge.str("description").orEmpty()),
            icon = badge.str("icon")?.takeIf { it.isNotBlank() },
            count = count.coerceAtLeast(0),
        )
    }

    private fun publicFields(fields: JsonObject?, names: Map<Int, String>): List<ProfileField> {
        if (fields == null || names.isEmpty()) {
            return emptyList()
        }
        return names.mapNotNull { (id, name) ->
            val raw = fields.get(id.toString()) ?: return@mapNotNull null
            if (raw.isJsonNull) {
                return@mapNotNull null
            }
            val value = if (raw.isJsonPrimitive) raw.asString.trim() else return@mapNotNull null
            if (value.isEmpty()) {
                return@mapNotNull null
            }
            ProfileField(name, value)
        }
    }

    private fun peers(array: JsonArray?): List<ProfilePeer> =
        array?.objects().orEmpty().mapNotNull { item ->
            val id = item.long("id") ?: return@mapNotNull null
            val username = item.str("username") ?: return@mapNotNull null
            ProfilePeer(
                id = id,
                username = username,
                displayName = item.str("name")?.takeIf { it.isNotBlank() },
                avatarTemplate = item.str("avatar_template"),
                count = item.int("count") ?: 0,
            )
        }

    private fun toPost(item: JsonObject, fallbackTitle: String): TopicPost? {
        val postId = item.long("id") ?: return null
        val actionCode = item.str("action_code")?.takeIf { it.isNotBlank() }
        val postType = item.int("post_type") ?: 1
        val cooked = item.str("cooked").orEmpty()
        val small = postType == 2 || postType == 3 || actionCode != null
        if (cooked.isBlank() && !small) {
            return null
        }
        val plain = stripTags(cooked).ifBlank {
            actionCode ?: if (small) "action" else fallbackTitle
        }
        return TopicPost(
            id = postId,
            postNumber = item.int("post_number") ?: 1,
            username = item.str("username") ?: "unknown",
            cookedHtml = cooked,
            plainText = plain,
            createdAt = item.instant("created_at") ?: Instant.EPOCH,
            displayName = item.str("name")?.takeIf { it.isNotBlank() },
            avatarTemplate = item.str("avatar_template"),
            userTitle = item.str("user_title")?.takeIf { it.isNotBlank() },
            likeCount = likeCount(item),
            replyCount = item.int("reply_count") ?: 0,
            staff = item.bool("staff") == true || item.bool("admin") == true || item.bool("moderator") == true,
            postType = postType,
            actionCode = actionCode,
            replyTo = replyTo(item),
            reactions = reactions(item),
            reactionUsersCount = item.int("reaction_users_count") ?: likeCount(item),
            boosts = boosts(item),
            wiki = item.bool("wiki") ?: false,
            acceptedAnswer = item.bool("accepted_answer") ?: false,
            hidden = item.bool("hidden") ?: false,
            userDeleted = item.bool("user_deleted") ?: false,
            updatedAt = item.instant("updated_at"),
            version = item.int("version") ?: 1,
            flairName = item.str("flair_name")?.takeIf { it.isNotBlank() },
            flairUrl = item.str("flair_url")?.takeIf { it.isNotBlank() },
            primaryGroupName = item.str("primary_group_name")?.takeIf { it.isNotBlank() },
        )
    }

    private fun replyTo(item: JsonObject): ReplyTo? {
        val number = item.int("reply_to_post_number") ?: return null
        if (number <= 0) {
            return null
        }
        val user = item.obj("reply_to_user")
        val username = user?.str("username")?.takeIf { it.isNotBlank() } ?: return null
        return ReplyTo(
            postNumber = number,
            username = username,
            avatarTemplate = user.str("avatar_template"),
        )
    }

    private fun reactions(item: JsonObject): List<PostReaction> =
        item.arr("reactions")?.objects().orEmpty().mapNotNull { reaction ->
            val id = reaction.str("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PostReaction(
                id = id,
                count = reaction.int("count") ?: 0,
                type = reaction.str("type") ?: "emoji",
            )
        }

    private fun boosts(item: JsonObject): List<PostBoost> =
        item.arr("boosts")?.objects().orEmpty().mapNotNull { boost ->
            val id = boost.long("id") ?: return@mapNotNull null
            val user = boost.obj("user")
            val username = user?.str("username")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PostBoost(
                id = id,
                cookedHtml = boost.str("cooked").orEmpty(),
                username = username,
                displayName = user.str("name")?.takeIf { it.isNotBlank() },
                avatarTemplate = user.str("avatar_template"),
            )
        }

    fun tags(json: String): List<CommunityTag> {
        val root = jsonObject(json)
        val tags = root.arr("tags") ?: return emptyList()
        return tags.objects().mapNotNull { item ->
            val name = item.str("name") ?: item.str("id") ?: return@mapNotNull null
            CommunityTag(
                name,
                item.int("count") ?: item.int("topic_count") ?: 0,
                item.str("description") ?: item.str("description_text"),
            )
        }
    }

    fun groups(json: String): List<CommunityGroup> {
        val root = jsonObject(json)
        val groups = root.arr("groups") ?: return emptyList()
        return groups.objects().mapNotNull { item ->
            val id = item.long("id") ?: return@mapNotNull null
            val name = item.str("name") ?: return@mapNotNull null
            val visibility = item.int("visibility_level") ?: 0
            CommunityGroup(
                id = id,
                name = name,
                fullName = item.str("full_name"),
                memberCount = item.int("user_count") ?: 0,
                publicVisible = visibility == 0,
                bioHtml = item.str("bio_cooked") ?: item.str("bio_excerpt"),
            )
        }.filter { it.publicVisible }
    }

    fun badges(json: String): List<CommunityBadge> {
        val root = jsonObject(json)
        val types = root.arr("badge_types")?.objects().orEmpty().associate { item ->
            (item.long("id") ?: 0L) to (item.str("name") ?: "")
        }
        val badges = root.arr("badges") ?: return emptyList()
        return badges.objects().mapNotNull { item ->
            val id = item.long("id") ?: return@mapNotNull null
            val name = item.str("name") ?: return@mapNotNull null
            CommunityBadge(
                id = id,
                name = name,
                description = stripTags(item.str("description").orEmpty()),
                icon = item.str("icon")?.takeIf { it.isNotBlank() },
                grantCount = item.int("grant_count") ?: 0,
                badgeType = item.long("badge_type_id")?.let { types[it] }?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun members(json: String): List<PublicMember> {
        val root = jsonObject(json)
        val items = root.arr("directory_items") ?: return emptyList()
        return items.objects().mapNotNull { item ->
            val user = item.obj("user") ?: return@mapNotNull null
            val id = user.long("id") ?: return@mapNotNull null
            val username = user.str("username") ?: return@mapNotNull null
            PublicMember(
                id = id,
                username = username,
                name = user.str("name"),
                trustLevel = user.int("trust_level") ?: item.int("trust_level") ?: 0,
                avatarTemplate = user.str("avatar_template"),
                title = user.str("title")?.takeIf { it.isNotBlank() },
                likesReceived = item.int("likes_received") ?: 0,
            )
        }
    }

    fun about(json: String): CommunityAbout {
        val about = jsonObject(json).obj("about") ?: error("LINUX DO about JSON missing about")
        val admins = about.arr("admins")?.objects().orEmpty().mapNotNull { toStaff(it) }
        val moderators = about.arr("moderators")?.objects().orEmpty().mapNotNull { toStaff(it) }
        val stats = about.obj("stats")
        return CommunityAbout(
            title = about.str("title") ?: "LINUX DO",
            description = about.str("description").orEmpty(),
            staffUsernames = (admins + moderators).map { it.username }.distinct(),
            topicCount = stats?.int("topic_count") ?: stats?.int("topics_count") ?: 0,
            postCount = stats?.int("post_count") ?: stats?.int("posts_count") ?: 0,
            userCount = stats?.int("user_count") ?: stats?.int("users_count") ?: 0,
            likeCount = stats?.int("like_count") ?: stats?.int("likes_count") ?: 0,
            admins = admins,
            moderators = moderators,
            faqUrl = about.str("faq_url"),
            guidelinesUrl = about.str("guidelines_url") ?: about.str("guidelines"),
            tosUrl = about.str("tos_url"),
            privacyUrl = about.str("privacy_policy_url") ?: about.str("privacy_url"),
        )
    }

    fun siteSettings(json: String): SiteSettings {
        val root = jsonObject(json)
        val url = root.str("long_polling_base_url")
            ?: root.obj("site_settings")?.str("long_polling_base_url")
            ?: SiteSettings.DEFAULT_LONG_POLLING
        return SiteSettings(url.trim().trimEnd('/').ifBlank { SiteSettings.DEFAULT_LONG_POLLING })
    }

    fun searchTopics(json: String, categories: List<CommunityCategory>): List<HomeTopic> {
        val root = jsonObject(json)
        val users = usersById(root.arr("users"))
        val categoryById = categories.associateBy { it.id }
        val topics = root.arr("topics") ?: return emptyList()
        return topics.objects().mapNotNull { item -> toHomeTopic(item, users, categoryById) }
    }

    fun searchHits(json: String, categories: List<CommunityCategory>): List<moe.momokko.intellido.domain.search.SearchHit> {
        val root = jsonObject(json)
        val topics = searchTopics(json, categories)
        val byId = topics.associateBy { it.id }
        val hits = mutableListOf<moe.momokko.intellido.domain.search.SearchHit>()
        topics.forEach { topic ->
            hits += moe.momokko.intellido.domain.search.SearchHit(
                title = topic.title,
                blurb = topic.categoryName.orEmpty(),
                topicId = topic.id,
                slug = topic.slug,
            )
        }
        root.arr("posts")?.objects().orEmpty().forEach { item ->
            val topicId = item.long("topic_id") ?: return@forEach
            val topic = byId[topicId]
            hits += moe.momokko.intellido.domain.search.SearchHit(
                title = topic?.title ?: item.str("topic_title") ?: "话题",
                blurb = stripTags(item.str("blurb").orEmpty()),
                topicId = topicId,
                postNumber = item.int("post_number"),
                username = item.str("username"),
                slug = topic?.slug,
            )
        }
        return hits
    }

    private fun toStaff(item: JsonObject): PublicMember? {
        val id = item.long("id") ?: return null
        val username = item.str("username") ?: return null
        return PublicMember(
            id = id,
            username = username,
            name = item.str("name"),
            trustLevel = item.int("trust_level") ?: 0,
            avatarTemplate = item.str("avatar_template"),
            title = item.str("title")?.takeIf { it.isNotBlank() },
        )
    }

    private fun likeCount(item: JsonObject): Int {
        val reactions = item.int("reaction_users_count")
        if (reactions != null && reactions > 0) {
            return reactions
        }
        return item.arr("actions_summary")?.objects().orEmpty()
            .firstOrNull { it.int("id") == 2 }
            ?.int("count")
            ?: 0
    }

    private fun toHomeTopic(
        item: JsonObject,
        users: Map<Long, TopicPoster>,
        categoryById: Map<Long, CommunityCategory>,
    ): HomeTopic? {
        val id = item.long("id") ?: return null
        val title = item.str("title") ?: return null
        val posters = item.arr("posters")?.objects().orEmpty().mapNotNull { poster ->
            val userId = poster.long("user_id") ?: return@mapNotNull null
            users[userId]
        }
        return HomeTopic(
            id = id,
            title = title,
            slug = item.str("slug") ?: id.toString(),
            postsCount = item.int("posts_count") ?: 1,
            replyCount = item.int("reply_count") ?: 0,
            categoryName = item.long("category_id")?.let { categoryById[it]?.name },
            authorUsername = posters.firstOrNull()?.username ?: item.str("last_poster_username") ?: "unknown",
            lastPostedAt = item.instant("last_posted_at") ?: Instant.EPOCH,
            categoryColor = item.long("category_id")?.let { categoryById[it]?.color },
            categoryIcon = item.long("category_id")?.let { categoryById[it]?.icon },
            tags = item.stringList("tags"),
            views = item.int("views") ?: 0,
            pinned = item.bool("pinned") ?: false,
            posters = posters,
            closed = item.bool("closed") ?: false,
            archived = item.bool("archived") ?: false,
            acceptedAnswer = item.hasAcceptedAnswer() || item.bool("has_accepted_answer") == true,
            wordCount = item.int("word_count") ?: 0,
            createdAt = item.instant("created_at"),
        )
    }

    private fun usersById(users: JsonArray?): Map<Long, TopicPoster> =
        users?.objects().orEmpty().mapNotNull { user ->
            val id = user.long("id") ?: return@mapNotNull null
            val username = user.str("username") ?: return@mapNotNull null
            id to TopicPoster(username, user.str("avatar_template"))
        }.toMap()

    private fun jsonObject(json: String): JsonObject {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{")) {
            throw IllegalArgumentException("LINUX DO returned a non-JSON document")
        }
        val parsed = JsonParser.parseString(trimmed)
        if (!parsed.isJsonObject) {
            throw IllegalArgumentException("LINUX DO JSON was not an object")
        }
        return parsed.asJsonObject
    }

    companion object {
        fun stripTags(html: String): String =
            html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    }
}

private fun JsonObject.str(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asString else null }

private fun JsonObject.long(name: String): Long? =
    get(name)?.takeUnless { it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asLong else null }

private fun JsonObject.int(name: String): Int? =
    get(name)?.takeUnless { it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asInt else null }

private fun JsonObject.bool(name: String): Boolean? =
    get(name)?.takeUnless { it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asBoolean else null }

private fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.arr(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.longList(name: String): List<Long> {
    val array = arr(name) ?: return emptyList()
    return array.mapNotNull { el ->
        if (el.isJsonPrimitive && el.asJsonPrimitive.isNumber) el.asLong else null
    }
}

private fun JsonObject.stringList(name: String): List<String> {
    val array = arr(name) ?: return emptyList()
    return array.mapNotNull { el ->
        when {
            el.isJsonPrimitive -> el.asString
            el.isJsonObject -> el.asJsonObject.str("name")
            else -> null
        }
    }
}

private fun JsonObject.instant(name: String): Instant? =
    str(name)?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun JsonObject.hasAcceptedAnswer(): Boolean {
    if (bool("accepted_answer") == true || bool("has_accepted_answer") == true) {
        return true
    }
    val value = get("accepted_answer") ?: return false
    return value.isJsonObject
}

private fun JsonArray.objects(): List<JsonObject> =
    mapNotNull { el: JsonElement -> el.takeIf { it.isJsonObject }?.asJsonObject }

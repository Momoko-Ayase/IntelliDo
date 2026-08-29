package moe.momokko.intellido.transport

import moe.momokko.intellido.domain.catalog.ProfileField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DiscourseJsonMapperTest {
    private val mapper = DiscourseJsonMapper()

    @Test
    fun `latest json becomes public Home topics with category names`() {
        val categories = mapper.categories(resource("discourse/categories.json"))
        val topics = mapper.homeTopics(resource("discourse/latest.json"), categories)
        assertEquals(listOf("欢迎使用 IntelliDo", "如何阅读话题"), topics.map { it.title })
        assertEquals("公告", topics[0].categoryName)
        assertEquals("system", topics[0].authorUsername)
        assertEquals("helper", topics[1].authorUsername)
        assertEquals("F6C344", topics[0].categoryColor)
        assertEquals("comments", topics[0].categoryIcon)
        assertEquals("comments", categories[0].icon)
        assertEquals(3, categories.size)
        assertEquals(1L, categories[2].parentId)
        assertEquals("hard-drive", categories[2].icon)
        assertEquals(listOf("intellido"), topics[0].tags)
        assertEquals(listOf("faq", "reading"), topics[1].tags)
        assertEquals(29_700, topics[0].views)
        assertEquals(true, topics[0].pinned)
        assertEquals(listOf("system", "helper"), topics[0].posters.map { it.username })
        assertEquals(
            "https://linux.do/user_avatar/linux.do/system/48/1_2.png",
            topics[0].posters.first().avatarUrl(),
        )
        assertEquals(113, topics[1].views)
        assertEquals(false, topics[1].pinned)
        assertEquals(listOf("helper"), topics[1].posters.map { it.username })
    }

    @Test
    fun `trust gated nested subcategory still names the topic chip`() {
        val categories = mapper.categories(
            """
            {
              "category_list": {
                "categories": [
                  {
                    "id": 4,
                    "name": "开发调优",
                    "slug": "develop",
                    "topic_count": 8,
                    "read_restricted": false,
                    "color": "00AEFF",
                    "icon": "code",
                    "subcategory_list": [
                      {
                        "id": 401,
                        "name": "开发调优",
                        "slug": "develop-lv1",
                        "topic_count": 3,
                        "read_restricted": true,
                        "parent_category_id": 4,
                        "min_trust_level": 1,
                        "color": "00AEFF",
                        "icon": "code"
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        assertEquals(2, categories.size)
        val gated = categories.first { it.id == 401L }
        assertTrue(gated.readRestricted)
        assertEquals(1, gated.minTrustLevel)
        val topics = mapper.homeTopics(
            """
            {
              "users": [{ "id": 22, "username": "helper" }],
              "topic_list": {
                "topics": [
                  {
                    "id": 501,
                    "title": "JCEF 过盾",
                    "slug": "jcef-challenge",
                    "posts_count": 1,
                    "reply_count": 0,
                    "last_posted_at": "2026-08-29T00:00:00.000Z",
                    "category_id": 401,
                    "posters": [{ "user_id": 22 }]
                  }
                ]
              }
            }
            """.trimIndent(),
            categories,
        )
        assertEquals("开发调优, Lv1", topics.single().categoryName)
        assertEquals("00AEFF", topics.single().categoryColor)
    }

    @Test
    fun `compact site json still names trust gated subcategories`() {
        val categories = mapper.categories(
            """
            {
              "default_archetype": "regular",
              "categories": [
                {
                  "id": 42,
                  "name": "搞七捻三",
                  "slug": "gossip",
                  "color": "43A047",
                  "icon": "droplet",
                  "read_restricted": false
                },
                {
                  "id": 421,
                  "name": "搞七捻三, Lv1",
                  "slug": "gossip-lv1",
                  "read_restricted": true,
                  "parent_category_id": 42
                }
              ]
            }
            """.trimIndent(),
        )
        val gated = categories.first { it.id == 421L }
        assertEquals("droplet", gated.icon)
        assertEquals("搞七捻三, Lv1", gated.listLabel())
        val topics = mapper.homeTopics(
            """
            {
              "users": [{ "id": 22, "username": "helper" }],
              "topic_list": {
                "topics": [
                  {
                    "id": 7,
                    "title": "手头的 token 有点多",
                    "slug": "tokens",
                    "category_id": 421,
                    "tags": ["人工智能"],
                    "posters": [{ "user_id": 22 }],
                    "last_posted_at": "2026-08-29T00:00:00.000Z"
                  }
                ]
              }
            }
            """.trimIndent(),
            categories,
        )
        assertEquals("搞七捻三, Lv1", topics.single().categoryName)
        assertEquals(true, topics.single().categoryRestricted)
    }

    @Test
    fun `topic embedded category name is used when the catalog misses`() {
        val topics = mapper.homeTopics(
            """
            {
              "users": [{ "id": 22, "username": "helper" }],
              "topic_list": {
                "topics": [
                  {
                    "id": 8,
                    "title": "catalog miss",
                    "slug": "miss",
                    "category_id": 421,
                    "category": { "name": "搞七捻三, Lv1", "read_restricted": true, "icon": "droplet" },
                    "posters": [{ "user_id": 22 }],
                    "last_posted_at": "2026-08-29T00:00:00.000Z"
                  }
                ]
              }
            }
            """.trimIndent(),
            emptyList(),
        )
        assertEquals("搞七捻三, Lv1", topics.single().categoryName)
        assertEquals(true, topics.single().categoryRestricted)
        assertEquals("droplet", topics.single().categoryIcon)
    }

    @Test
    fun `topic json becomes a native cooked thread`() {
        val categories = mapper.categories(resource("discourse/categories.json"))
        val thread = mapper.topicThread(resource("discourse/topic.json"), categories)
        assertEquals(101L, thread.topic.id)
        assertEquals(7L, thread.messageBusLastId)
        assertEquals("欢迎使用 IntelliDo", thread.topic.title)
        assertEquals("公告", thread.topic.categoryName)
        assertEquals("F6C344", thread.topic.categoryColor)
        assertEquals(listOf("intellido"), thread.topic.tags)
        assertEquals(2, thread.posts.size)
        assertTrue(thread.posts[0].cookedHtml.contains("<strong>IntelliDo</strong>"))
        assertEquals("system", thread.posts[0].username)
        assertEquals("系统", thread.posts[0].displayName)
        assertEquals("https://linux.do/user_avatar/linux.do/system/90/1_2.png", thread.posts[0].avatarUrl())
        assertEquals(29_700, thread.topic.views)
        assertEquals(120, thread.topic.likeCount)
        assertEquals(8, thread.topic.participantCount)
        assertEquals(1, thread.topic.linkCount)
        assertEquals(true, thread.topic.pinned)
        assertEquals("系统管理员", thread.posts[0].userTitle)
        assertEquals(42, thread.posts[0].likeCount)
        assertEquals(42, thread.posts[0].reactionUsersCount)
        assertEquals(listOf("heart", "+1"), thread.posts[0].reactions.map { it.id })
        assertEquals(36, thread.posts[0].reactions[0].count)
        assertEquals(listOf("前排", "前排合影", "前排合影"), thread.posts[0].boosts.map { it.cookedHtml.replace(Regex("<[^>]+>"), "") })
        assertEquals("helper", thread.posts[0].boosts[0].username)
        assertEquals("助手", thread.posts[0].boosts[0].displayName)
        assertEquals("https://linux.do/user_avatar/linux.do/helper/48/2.png", thread.posts[0].boosts[0].avatarUrl())
        assertEquals(2, thread.posts[0].version)
        assertEquals(true, thread.posts[0].edited)
        assertEquals("admins", thread.posts[0].flairName)
        assertEquals(3, thread.posts[0].replyCount)
        assertEquals(true, thread.posts[0].staff)
        assertEquals(1, thread.posts[1].replyTo?.postNumber)
        assertEquals("system", thread.posts[1].replyTo?.username)
        assertEquals(false, thread.topic.closed)
        assertEquals(240, thread.topic.wordCount)
        assertEquals(3, thread.topic.readingMinutes())
        assertEquals(null, thread.posts[1].userTitle)
        assertEquals(4, thread.posts[1].likeCount)
        assertEquals(false, thread.posts[1].staff)
        assertEquals(Instant.parse("2026-08-22T09:00:00Z"), thread.topic.lastPostedAt)
        assertTrue(thread.topic.lastPostedAt.isAfter(thread.posts.last().createdAt))
    }

    @Test
    fun `small action posts keep the action code instead of the topic title`() {
        val json = """
            {
              "id": 9,
              "title": "如何阅读话题",
              "slug": "how-to-read-topics",
              "posts_count": 2,
              "last_posted_at": "2026-08-25T00:10:00.000Z",
              "post_stream": {
                "stream": [1, 2],
                "posts": [
                  {
                    "id": 1,
                    "post_number": 1,
                    "username": "helper",
                    "cooked": "<p>正文</p>",
                    "created_at": "2026-08-25T00:00:00.000Z"
                  },
                  {
                    "id": 2,
                    "post_number": 2,
                    "username": "helper",
                    "name": "助手",
                    "cooked": "",
                    "created_at": "2026-08-25T00:10:00.000Z",
                    "post_type": 3,
                    "action_code": "pinned_globally.enabled",
                    "user_title": "使用向导"
                  }
                ]
              }
            }
        """.trimIndent()
        val thread = mapper.topicThread(json)
        assertEquals("pinned_globally.enabled", thread.posts[1].actionCode)
        assertEquals(3, thread.posts[1].postType)
        assertTrue(thread.posts[1].isSmallAction)
        assertTrue(thread.posts[1].cookedHtml.isEmpty())
        assertFalse(thread.posts[1].plainText.contains("抽奖"))
        assertFalse(thread.posts[1].plainText.contains(thread.topic.title))
    }

    @Test
    fun `user json becomes a public profile`() {
        val profile = mapper.publicProfile(
            """
            {
              "user": {
                "id": 2,
                "username": "helper",
                "name": "助手",
                "title": "使用向导",
                "bio_cooked": "<p>本地假用户</p>",
                "trust_level": 2,
                "avatar_template": "/user_avatar/linux.do/helper/{size}/2.png",
                "created_at": "2026-01-01T00:00:00.000Z",
                "badge_count": 2
              }
            }
            """.trimIndent(),
        )
        assertEquals("helper", profile.username)
        assertEquals("助手", profile.displayName)
        assertEquals("使用向导", profile.title)
        assertEquals("<p>本地假用户</p>", profile.bioHtml)
        assertEquals(2, profile.trustLevel)
        assertEquals(2, profile.badgeCount)
        assertEquals("https://linux.do/user_avatar/linux.do/helper/120/2.png", profile.avatarUrl(120))
    }

    @Test
    fun `user json keeps guest-visible card fields`() {
        val fields = mapper.userFieldNames(resource("discourse/site-user-fields.json"))
        val profile = mapper.publicProfile(resource("discourse/user.json"), fields)
        assertEquals("测试城", profile.location)
        assertEquals("https://example.test", profile.website)
        assertEquals(false, profile.moderator)
        assertEquals(false, profile.admin)
        assertEquals(48, profile.profileViews)
        assertEquals(3, profile.followerCount)
        assertEquals(120, profile.gamificationScore)
        assertEquals("slight_smile", profile.statusEmoji)
        assertEquals("在读指南", profile.statusDescription)
        assertEquals("user", profile.flairUrl)
        assertEquals("everyone", profile.flairName)
        assertEquals(listOf(ProfileField("所属机构", "IntelliDo 测试")), profile.publicFields)
        assertEquals("首次发帖", profile.featuredBadges.single().name)
        assertEquals("pencil", profile.featuredBadges.single().icon)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), profile.createdAt)
    }

    @Test
    fun `user summary json keeps guest-visible stats and lists`() {
        val summary = mapper.userSummary(resource("discourse/user-summary.json"))
        assertEquals(true, summary.canSeeStats)
        assertEquals(9, summary.daysVisited)
        assertEquals(3600, summary.timeReadSeconds)
        assertEquals(600, summary.recentTimeReadSeconds)
        assertEquals(12, summary.topicsEntered)
        assertEquals(40, summary.postsRead)
        assertEquals(6, summary.likesGiven)
        assertEquals(4, summary.likesReceived)
        assertEquals(1, summary.topicCount)
        assertEquals(3, summary.postCount)
        assertEquals(1, summary.solvedCount)
        assertEquals("欢迎使用 IntelliDo", summary.topics.single().title)
        assertEquals(120, summary.topics.single().likeCount)
        assertEquals("如何阅读话题", summary.replies.single().title)
        assertEquals(2, summary.replies.single().postNumber)
        assertEquals("https://example.test/docs", summary.links.single().url)
        assertEquals("公告", summary.topCategories.single().name)
        assertEquals(2, summary.topCategories.single().topicCount)
        assertEquals("system", summary.mostLikedBy.single().username)
        assertEquals("system", summary.mostLiked.single().username)
        assertEquals("system", summary.mostRepliedTo.single().username)
        assertEquals("首次发帖", summary.badges.single().name)
    }

    @Test
    fun `hidden summary stats stay off the public card`() {
        val summary = mapper.userSummary(
            """
            {
              "topics": [],
              "user_summary": {
                "can_see_summary_stats": false,
                "likes_given": 9,
                "topic_ids": []
              }
            }
            """.trimIndent(),
        )
        assertEquals(false, summary.canSeeStats)
        assertEquals(0, summary.likesGiven)
        assertEquals(0, summary.daysVisited)
    }

    @Test
    fun `reply json array becomes posts`() {
        val posts = mapper.extraPosts(
            """
            [
              {
                "id": 9,
                "post_number": 9,
                "username": "helper",
                "cooked": "<p>楼中楼</p>",
                "created_at": "2026-08-22T01:15:00.000Z"
              }
            ]
            """.trimIndent(),
        )
        assertEquals(1, posts.size)
        assertEquals("helper", posts[0].username)
        assertEquals(9, posts[0].postNumber)
        assertTrue(posts[0].cookedHtml.contains("楼中楼"))
    }

    @Test
    fun `html challenge pages are rejected instead of parsed as topics`() {
        val html = "<html><body>Just a moment...</body></html>"
        assertThrows(IllegalArgumentException::class.java) { mapper.homeTopics(html, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { mapper.topicThread(html) }
    }

    @Test
    fun `about json keeps stats staff and chinese description`() {
        val about = mapper.about(resource("discourse/about.json"))
        assertEquals("LINUX DO", about.title)
        assertTrue(about.description.contains("匿名"))
        assertEquals(10, about.topicCount)
        assertEquals(20, about.postCount)
        assertEquals(5, about.userCount)
        assertEquals(30, about.likeCount)
        assertEquals(listOf("system", "helper"), about.staffUsernames)
        assertEquals("system", about.admins.single().username)
        assertEquals("助手", about.moderators.single().name)
    }

    @Test
    fun `site json exposes the message bus origin`() {
        assertEquals("https://ping.ldstatic.com", mapper.siteSettings(resource("discourse/site.json")).messageBusOrigin)
    }

    @Test
    fun `search json returns public topics and posts`() {
        val hits = mapper.searchHits(resource("discourse/search.json"), emptyList())
        assertEquals(listOf(101L, 101L), hits.map { it.topicId })
        assertEquals("欢迎使用 IntelliDo", hits.first().title)
        assertEquals(1, hits.last().postNumber)
        assertEquals("system", hits.last().username)
    }

    @Test
    fun `session current json becomes a signed-in member`() {
        val session = mapper.currentSession(resource("discourse/session-current.json"))
        val signed = session as moe.momokko.intellido.domain.session.MemberSession.SignedIn
        assertEquals("helper", signed.username)
        assertEquals(2, signed.trustLevel)
        assertEquals(2L, signed.id)
        assertEquals("助手", signed.name)
        assertEquals("/user_avatar/linux.do/helper/{size}/2.png", signed.avatarTemplate)
        assertTrue(
            mapper.currentSession(resource("discourse/session-current-anonymous.json"))
                is moe.momokko.intellido.domain.session.MemberSession.Anonymous,
        )
        assertTrue(mapper.currentSession("<html>nope</html>") is moe.momokko.intellido.domain.session.MemberSession.Anonymous)
        assertEquals("csrf-token", mapper.csrfToken("""{"csrf":"csrf-token"}"""))
    }

    @Test
    fun `directory fixtures keep public groups badges and members`() {
        assertEquals("everyone", mapper.groups(resource("discourse/groups.json")).single().name)
        assertEquals("<p>公开群组</p>", mapper.groups(resource("discourse/groups.json")).single().bioHtml)
        val badge = mapper.badges(resource("discourse/badges.json")).first()
        assertEquals("首次发帖", badge.name)
        assertEquals(12, badge.grantCount)
        assertEquals("pencil", badge.icon)
        assertEquals("铜", badge.badgeType)
        val member = mapper.members(resource("discourse/directory-items.json")).first()
        assertEquals("system", member.username)
        assertEquals(80, member.likesReceived)
        assertEquals("系统管理员", member.title)
    }

    private fun resource(path: String): String =
        javaClass.classLoader.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }
}

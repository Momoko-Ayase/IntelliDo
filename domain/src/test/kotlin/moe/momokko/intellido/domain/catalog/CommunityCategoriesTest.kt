package moe.momokko.intellido.domain.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommunityCategoriesTest {
    @Test
    fun `sidebar inserts child categories after their parent`() {
        val parent = CommunityCategory(14, "资源荟萃", "resource", null, 2, false, icon = "square-share-nodes")
        val child = CommunityCategory(94, "网盘资源", "cloud-asset", null, 1, false, icon = "hard-drive", parentId = 14)
        val other = CommunityCategory(4, "开发调优", "develop", null, 3, false, icon = "code")
        val ordered = CommunityCategories.sidebarOrder(listOf(other, parent), listOf(other, parent, child))
        assertEquals(listOf("开发调优", "资源荟萃", "网盘资源"), ordered.map { it.name })
    }

    @Test
    fun `trust gated subcategory keeps a visible list label`() {
        val parent = CommunityCategory(4, "开发调优", "develop", null, 3, false)
        val child = CommunityCategory(
            id = 401,
            name = "开发调优",
            slug = "develop-lv1",
            description = null,
            topicCount = 1,
            readRestricted = true,
            parentId = 4,
            minTrustLevel = 1,
        )
        assertEquals("开发调优, Lv1", child.listLabel(parent))
        assertEquals("开发调优", parent.listLabel())
        assertTrue(child.readRestricted)
        val named = child.copy(name = "插件开发")
        assertEquals("插件开发, Lv1", named.listLabel(parent))
    }

    @Test
    fun `children inherit parent colour and icon`() {
        val parent = CommunityCategory(42, "搞七捻三", "gossip", null, 8, false, color = "43A047", icon = "droplet")
        val child = CommunityCategory(
            id = 421,
            name = "搞七捻三, Lv1",
            slug = "gossip-lv1",
            description = null,
            topicCount = 3,
            readRestricted = true,
            parentId = 42,
        )
        val inherited = CommunityCategories.inheritParents(listOf(parent, child)).first { it.id == 421L }
        assertEquals("droplet", inherited.icon)
        assertEquals("43A047", inherited.color)
        assertEquals("搞七捻三, Lv1", inherited.listLabel(parent))
    }
}

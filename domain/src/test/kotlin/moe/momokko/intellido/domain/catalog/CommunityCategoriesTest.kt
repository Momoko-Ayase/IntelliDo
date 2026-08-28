package moe.momokko.intellido.domain.catalog

import org.junit.jupiter.api.Assertions.assertEquals
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
}

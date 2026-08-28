package moe.momokko.intellido.domain.catalog

object CommunityCategories {
    fun sidebarOrder(
        topLevel: List<CommunityCategory>,
        all: List<CommunityCategory> = topLevel,
    ): List<CommunityCategory> {
        val byId = (all + topLevel).associateBy { it.id }
        val visible = byId.values.filterNot { it.readRestricted }
        val tops = topLevel.filterNot { it.readRestricted }.filter { it.parentId == null }
            .ifEmpty { visible.filter { it.parentId == null } }
        val kids = visible.filter { it.parentId != null }.groupBy { it.parentId }
        val seen = linkedSetOf<Long>()
        return buildList {
            tops.forEach { parent ->
                if (seen.add(parent.id)) {
                    add(parent)
                }
                kids[parent.id].orEmpty().forEach { child ->
                    if (seen.add(child.id)) {
                        add(child)
                    }
                }
            }
        }
    }
}

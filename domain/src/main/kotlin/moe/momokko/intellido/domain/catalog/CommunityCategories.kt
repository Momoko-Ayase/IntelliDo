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

    /**
     * Copy parent colour / icon onto children that omitted them. LINUX DO
     * trust-gated subcategories often only send a name and `read_restricted`.
     */
    fun inheritParents(categories: List<CommunityCategory>): List<CommunityCategory> {
        val byId = categories.associateBy { it.id }
        return categories.map { category ->
            val parent = category.parentId?.let { byId[it] } ?: return@map category
            category.copy(
                color = category.color?.takeIf { it.isNotBlank() } ?: parent.color,
                icon = category.icon?.takeIf { it.isNotBlank() } ?: parent.icon,
            )
        }
    }

    fun merge(primary: List<CommunityCategory>, extra: List<CommunityCategory>): List<CommunityCategory> {
        val byId = linkedMapOf<Long, CommunityCategory>()
        extra.forEach { category -> byId[category.id] = category }
        primary.forEach { category -> byId[category.id] = category }
        return inheritParents(byId.values.toList())
    }
}

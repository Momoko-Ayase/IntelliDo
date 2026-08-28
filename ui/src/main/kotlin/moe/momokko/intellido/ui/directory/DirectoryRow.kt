package moe.momokko.intellido.ui.directory

import moe.momokko.intellido.domain.catalog.CommunityBadge
import moe.momokko.intellido.domain.catalog.CommunityCategory
import moe.momokko.intellido.domain.catalog.CommunityGroup
import moe.momokko.intellido.domain.catalog.CommunityTag
import moe.momokko.intellido.domain.catalog.PublicMember
import moe.momokko.intellido.domain.topic.HomeTopic

sealed class DirectoryRow {
    data class Category(val category: CommunityCategory) : DirectoryRow()
    data class Tag(val tag: CommunityTag) : DirectoryRow()
    data class Group(val group: CommunityGroup) : DirectoryRow()
    data class Badge(val badge: CommunityBadge) : DirectoryRow()
    data class Member(val member: PublicMember) : DirectoryRow()
    data class Topic(val topic: HomeTopic) : DirectoryRow()
    data class Message(val text: String) : DirectoryRow()

    fun label(): String = when (this) {
        is Category -> "${category.name}    ${category.topicCount}"
        is Tag -> "#${tag.name}    ${tag.topicCount}"
        is Group -> "${group.fullName ?: group.name}    ${group.memberCount}"
        is Badge -> "${badge.name}    ${badge.description}"
        is Member -> "${member.username}    TL${member.trustLevel}"
        is Topic -> topic.title
        is Message -> text
    }
}

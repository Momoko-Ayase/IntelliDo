package moe.momokko.intellido.domain.live

/**
 * Running set of users currently composing a reply in a public topic.
 */
class TopicPresenceState {
    private val users = LinkedHashMap<Long, LivePresenceUser>()

    fun apply(event: GuestLiveEvent.TopicPresence): List<LivePresenceUser> {
        event.leavingIds.forEach { users.remove(it) }
        event.entering.forEach { users[it.id] = it }
        return snapshot()
    }

    fun replace(next: List<LivePresenceUser>): List<LivePresenceUser> {
        users.clear()
        next.forEach { users[it.id] = it }
        return snapshot()
    }

    fun snapshot(): List<LivePresenceUser> = users.values.toList()

    fun clear() {
        users.clear()
    }
}

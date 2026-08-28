package moe.momokko.intellido.domain.session

/**
 * IntelliDo models exactly one LINUX DO identity. Until sign-in succeeds the
 * session is anonymous: public read-only browsing with no account tools.
 */
sealed class MemberSession {
    data object Anonymous : MemberSession()

    data class SignedIn(
        val username: String,
        val trustLevel: Int,
    ) : MemberSession() {
        init {
            require(username.isNotBlank()) { "username must not be blank" }
            require(trustLevel in 0..4) { "trustLevel must be 0-4" }
        }
    }
}

enum class MemberAction {
    READ_PUBLIC_TOPIC,
    READ_PUBLIC_CATEGORY,
    READ_PUBLIC_TAG,
    READ_PUBLIC_GROUP,
    READ_PUBLIC_BADGE,
    READ_PUBLIC_MEMBER,
    READ_COMMUNITY_ABOUT,
    SEARCH_PUBLIC,
    LIKE,
    REPLY,
    BOOKMARK,
    MESSAGE,
    CHAT,
    CONNECT,
    NOTIFICATION,
    DRAFT,
}

object MemberCapabilities {
    private val anonymousReads: Set<MemberAction> = setOf(
        MemberAction.READ_PUBLIC_TOPIC,
        MemberAction.READ_PUBLIC_CATEGORY,
        MemberAction.READ_PUBLIC_TAG,
        MemberAction.READ_PUBLIC_GROUP,
        MemberAction.READ_PUBLIC_BADGE,
        MemberAction.READ_PUBLIC_MEMBER,
        MemberAction.READ_COMMUNITY_ABOUT,
        MemberAction.SEARCH_PUBLIC,
    )

    fun isAvailable(
        session: MemberSession,
        action: MemberAction,
        serverAllows: Boolean = true,
    ): Boolean {
        if (!serverAllows) {
            return false
        }
        return when (session) {
            MemberSession.Anonymous -> action in anonymousReads
            is MemberSession.SignedIn -> true
        }
    }
}

package moe.momokko.intellido.ui.session

import com.intellij.util.messages.Topic
import moe.momokko.intellido.domain.session.MemberSession

fun interface MemberSessionListener {
    fun sessionChanged(previous: MemberSession, next: MemberSession)

    companion object {
        val TOPIC: Topic<MemberSessionListener> =
            Topic.create("intellido.memberSession", MemberSessionListener::class.java)
    }
}

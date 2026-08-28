package moe.momokko.intellido.platform.home

import moe.momokko.intellido.domain.topic.HomeTopic

class HomeTopicListState {
    private val topics: MutableList<HomeTopic> = mutableListOf()

    fun replaceAll(next: List<HomeTopic>) {
        topics.clear()
        topics.addAll(next)
    }

    fun append(next: List<HomeTopic>) {
        val have = topics.map { it.id }.toSet()
        next.filter { it.id !in have }.forEach { topics.add(it) }
    }

    fun snapshot(): List<HomeTopic> = topics.toList()

    fun titles(): List<String> = topics.map { it.title }
}

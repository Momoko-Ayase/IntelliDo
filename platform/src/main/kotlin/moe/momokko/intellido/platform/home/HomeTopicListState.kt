package moe.momokko.intellido.platform.home

import moe.momokko.intellido.domain.topic.HomeTopic

class HomeTopicListState {
    private val lock = Any()
    private val topics: MutableList<HomeTopic> = mutableListOf()

    fun replaceAll(next: List<HomeTopic>) {
        synchronized(lock) {
            topics.clear()
            topics.addAll(next)
        }
    }

    fun append(next: List<HomeTopic>) {
        synchronized(lock) {
            val have = topics.map { it.id }.toSet()
            next.filter { it.id !in have }.forEach { topics.add(it) }
        }
    }

    fun snapshot(): List<HomeTopic> = synchronized(lock) { topics.toList() }

    fun titles(): List<String> = synchronized(lock) { topics.map { it.title } }
}

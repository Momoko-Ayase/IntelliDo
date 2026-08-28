package moe.momokko.intellido.transport

/**
 * Binary LINUX DO media through the JCEF origin, never JVM HTTP.
 */
interface LinuxDoMediaLoader {
    fun load(urls: List<String>, maxEdge: Int): Map<String, ByteArray>
}

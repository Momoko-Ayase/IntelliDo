package moe.momokko.intellido.ui.jcef

/**
 * Process-lifetime media bytes with a hard entry and byte cap.
 */
class BoundedBytesCache(
    private val maxEntries: Int = 256,
    private val maxBytes: Long = 64L * 1024 * 1024,
) {
    private val lock = Any()
    private val entries = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > maxEntries
    }
    private var bytes: Long = 0

    fun get(key: String): ByteArray? = synchronized(lock) { entries[key] }

    fun put(key: String, value: ByteArray) {
        if (value.isEmpty()) {
            return
        }
        synchronized(lock) {
            entries.remove(key)?.let { previous -> bytes -= previous.size }
            while (entries.isNotEmpty() && (bytes + value.size > maxBytes || entries.size >= maxEntries)) {
                val iterator = entries.entries.iterator()
                if (!iterator.hasNext()) {
                    break
                }
                val eldest = iterator.next()
                bytes -= eldest.value.size
                iterator.remove()
            }
            entries[key] = value
            bytes += value.size
        }
    }
}

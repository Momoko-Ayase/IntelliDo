package moe.momokko.intellido.platform.i18n

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

interface LocalPreferenceStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun clear()
}

class InMemoryLocalPreferenceStore : LocalPreferenceStore {
    private val values = linkedMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun set(key: String, value: String) {
        values[key] = value
    }

    override fun clear() {
        values.clear()
    }
}

class FileLocalPreferenceStore(private val file: Path) : LocalPreferenceStore {
    override fun get(key: String): String? = load().getProperty(key)

    override fun set(key: String, value: String) {
        val properties = load()
        properties[key] = value
        Files.createDirectories(file.parent)
        file.outputStream().use { properties.store(it, "IntelliDo local application preferences") }
    }

    override fun clear() {
        Files.deleteIfExists(file)
    }

    private fun load(): Properties {
        val properties = Properties()
        if (Files.exists(file)) {
            file.inputStream().use { properties.load(it) }
        }
        return properties
    }
}

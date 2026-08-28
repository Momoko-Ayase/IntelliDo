package moe.momokko.intellido.ui.jcef

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class HashedFileCache(
    private val dir: Path,
) {
    fun read(key: String): ByteArray? {
        val file = fileFor(key)
        if (!Files.isRegularFile(file)) {
            return null
        }
        return runCatching { Files.readAllBytes(file) }.getOrNull()
    }

    fun write(key: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) {
            return
        }
        Files.createDirectories(dir)
        val file = fileFor(key)
        val tmp = file.resolveSibling("${file.fileName}.tmp")
        Files.write(tmp, bytes)
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun fileFor(key: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val hex = digest.joinToString("") { byte -> "%02x".format(byte) }
        return dir.resolve(hex)
    }

    companion object {
        const val MAX_FILE_BYTES: Int = 2_500_000
    }
}

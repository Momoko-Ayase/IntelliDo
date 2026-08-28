package moe.momokko.intellido.ui.workspace

/**
 * On-disk layout for the hidden IntelliDo workspace that hosts the standard IdeFrame.
 * This is not a user-visible project/module model.
 */
object IntelliDoWorkspaceLayout {
    const val DIRECTORY_NAME: String = "workspace"
    const val HOME_FILE_NAME: String = "Home.intellido-home"
    const val WELCOME_FILE_NAME: String = "Welcome.intellido-welcome"
    const val TOPIC_EXTENSION: String = "intellido-topic"
    const val DIRECTORY_EXTENSION: String = "intellido-directory"
    const val USER_EXTENSION: String = "intellido-user"
    const val BROWSE_EXTENSION: String = "intellido-browse"

    fun topicFileName(topicId: Long): String = "topic-$topicId.$TOPIC_EXTENSION"

    /**
     * Usernames reach this from community links, where the path segment is
     * percent-decoded first. Anything outside the safe set is re-encoded so a
     * crafted `/u/..%2F..%2Fevil` link cannot escape the workspace directory or
     * throw on a Windows-reserved character.
     */
    fun userFileName(username: String): String = "user-${encodeSegment(username.trim())}.$USER_EXTENSION"

    fun usernameFrom(fileName: String): String? {
        if (!fileName.endsWith(".$USER_EXTENSION")) {
            return null
        }
        return fileName.removeSuffix(".$USER_EXTENSION")
            .removePrefix("user-")
            .takeIf { it.isNotBlank() }
            ?.let { decodeSegment(it) }
            ?.takeIf { it.isNotBlank() }
    }

    fun browseFileName(url: String): String =
        "browse-${Integer.toHexString(url.hashCode())}.$BROWSE_EXTENSION"

    internal fun encodeSegment(raw: String): String = buildString {
        raw.toByteArray(Charsets.UTF_8).forEach { byte ->
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            val safe = code < 0x80 &&
                (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '_' || char == '-')
            if (safe) {
                append(char)
            } else {
                append('%').append("%02X".format(code))
            }
        }
    }

    internal fun decodeSegment(raw: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        var index = 0
        while (index < raw.length) {
            val hex = if (raw[index] == '%' && index + 3 <= raw.length) {
                raw.substring(index + 1, index + 3).toIntOrNull(16)
            } else {
                null
            }
            if (hex != null) {
                bytes.write(hex)
                index += 3
            } else {
                bytes.write(raw[index].code and 0xFF)
                index++
            }
        }
        return bytes.toString(Charsets.UTF_8)
    }
}

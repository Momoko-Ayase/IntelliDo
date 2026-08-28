package moe.momokko.intellido.platform.catalog

enum class DirectoryKind {
    CATEGORIES,
    TAGS,
    MEMBERS,
    BADGES,
    GROUPS,
    ABOUT,
    ;

    val fileName: String
        get() = name.lowercase() + ".intellido-directory"

    val titleKey: String
        get() = "directory.${name.lowercase()}"
}

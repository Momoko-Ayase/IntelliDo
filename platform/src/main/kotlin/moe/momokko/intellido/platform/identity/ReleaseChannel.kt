package moe.momokko.intellido.platform.identity

enum class ReleaseChannel {
    STABLE,
    NIGHTLY,
    ;

    companion object {
        fun fromProperty(value: String?): ReleaseChannel =
            if (value.equals("nightly", ignoreCase = true)) NIGHTLY else STABLE
    }
}

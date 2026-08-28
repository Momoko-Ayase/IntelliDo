package moe.momokko.intellido.platform.identity

import moe.momokko.intellido.platform.net.ClientRequestIdentity

/**
 * Stable versus Nightly product identity, directories, and branding keys.
 */
data class ProductIdentity(
    val channel: ReleaseChannel,
    val productVersion: String,
    val projectUrl: String = DEFAULT_PROJECT_URL,
) {
    val visibleProductName: String =
        if (channel == ReleaseChannel.NIGHTLY) "IntelliDo Nightly" else "IntelliDo"

    val platformPrefix: String =
        if (channel == ReleaseChannel.NIGHTLY) "IntelliDoNightly" else "IntelliDo"

    val pathsSelector: String = platformPrefix

    /**
     * Parent folder under AppData / Library / XDG. PathManager reads
     * `idea.vendor.name`, then ApplicationInfo `company@shortName`.
     * Distinct from the visible author name; must not contain spaces.
     */
    val vendorDirectoryName: String = VENDOR_DIRECTORY_NAME

    val brandingDirectory: String =
        if (channel == ReleaseChannel.NIGHTLY) "branding/nightly" else "branding/stable"

    val unofficialLabelZh: String = "非官方 LINUX DO 客户端"
    val unofficialLabelEn: String = "Unofficial LINUX DO Client"

    fun buildInfo(buildTimestamp: String, sourceRevision: String = "unknown"): String {
        val revision = sourceRevision.ifBlank { "unknown" }
        return if (channel == ReleaseChannel.NIGHTLY) {
            "$productVersion-nightly.$buildTimestamp+$revision"
        } else {
            "$productVersion+$buildTimestamp"
        }
    }

    fun requestIdentity(): ClientRequestIdentity =
        ClientRequestIdentity(
            productName = "IntelliDo",
            version = productVersion,
            projectUrl = projectUrl,
        )

    companion object {
        const val DEFAULT_PROJECT_URL: String = "https://github.com/Momoko-Ayase/IntelliDo"
        const val VENDOR_DIRECTORY_NAME: String = "Momokko"

        fun fromSystem(
            version: String = System.getProperty("intellido.version", "0.1.0"),
            channelProperty: String? = System.getProperty("intellido.channel"),
        ): ProductIdentity = ProductIdentity(ReleaseChannel.fromProperty(channelProperty), version)
    }
}

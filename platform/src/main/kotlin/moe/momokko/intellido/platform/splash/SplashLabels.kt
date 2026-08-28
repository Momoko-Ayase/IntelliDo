package moe.momokko.intellido.platform.splash

import moe.momokko.intellido.platform.identity.ProductIdentity

/**
 * Native splash overlay text. The bitmap only contains the ID mark.
 */
data class SplashLabels(
    val productName: String,
    val version: String,
    val buildInfo: String,
) {
    companion object {
        fun from(
            identity: ProductIdentity,
            buildTimestamp: String,
            sourceRevision: String = "unknown",
        ): SplashLabels = SplashLabels(
            productName = identity.visibleProductName,
            version = identity.productVersion,
            buildInfo = identity.buildInfo(buildTimestamp, sourceRevision),
        )
    }
}

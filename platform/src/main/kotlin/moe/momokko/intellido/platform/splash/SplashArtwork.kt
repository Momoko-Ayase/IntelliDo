package moe.momokko.intellido.platform.splash

/**
 * Window-sized splash is 800×450. The platform splash dialog uses the PNG pixel
 * size as the window size, so the shipped bitmap must stay this small. Labels are
 * stamped onto that bitmap at build time (keep [paintLabels] in sync with the
 * Gradle stamp task) because the native splash paints only the image.
 */
object SplashArtwork {
    const val WINDOW_WIDTH: Int = 800
    const val WINDOW_HEIGHT: Int = 450
    const val LABEL_LEFT: Int = 36
    const val LABEL_BASELINE_FROM_BOTTOM: Int = 88

    fun paintLabels(
        graphics: java.awt.Graphics2D,
        height: Int,
        labels: SplashLabels,
    ) {
        graphics.setRenderingHint(
            java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
        )
        val baseline = height - LABEL_BASELINE_FROM_BOTTOM
        graphics.color = java.awt.Color.WHITE
        graphics.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 28)
        graphics.drawString(labels.productName, LABEL_LEFT, baseline)
        graphics.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 16)
        graphics.color = java.awt.Color(0xF6, 0xC3, 0x44)
        graphics.drawString(labels.version, LABEL_LEFT, baseline + 28)
        graphics.color = java.awt.Color(0xDD, 0xDD, 0xDD)
        graphics.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12)
        graphics.drawString(labels.buildInfo, LABEL_LEFT, baseline + 50)
    }
}

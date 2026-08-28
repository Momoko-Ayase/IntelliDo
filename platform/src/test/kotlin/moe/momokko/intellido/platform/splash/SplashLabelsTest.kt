package moe.momokko.intellido.platform.splash

import moe.momokko.intellido.platform.identity.ProductIdentity
import moe.momokko.intellido.platform.identity.ReleaseChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SplashLabelsTest {
    @Test
    fun `stable splash labels are drawn from identity not the bitmap`() {
        val labels = SplashLabels.from(
            ProductIdentity(ReleaseChannel.STABLE, "0.1.0"),
            buildTimestamp = "20260822",
        )
        assertEquals("IntelliDo", labels.productName)
        assertEquals("0.1.0", labels.version)
        assertEquals("0.1.0+20260822", labels.buildInfo)
    }

    @Test
    fun `nightly splash labels use the Nightly product name`() {
        val labels = SplashLabels.from(
            ProductIdentity(ReleaseChannel.NIGHTLY, "0.1.0"),
            buildTimestamp = "20260822",
            sourceRevision = "abc1234",
        )
        assertEquals("IntelliDo Nightly", labels.productName)
        assertEquals("0.1.0-nightly.20260822+abc1234", labels.buildInfo)
    }

    @Test
    fun `artwork constants match the shipped window splash`() {
        assertEquals(800, SplashArtwork.WINDOW_WIDTH)
        assertEquals(450, SplashArtwork.WINDOW_HEIGHT)
    }

    @Test
    fun `labels paint onto the window-sized buffer`() {
        val image = java.awt.image.BufferedImage(
            SplashArtwork.WINDOW_WIDTH,
            SplashArtwork.WINDOW_HEIGHT,
            java.awt.image.BufferedImage.TYPE_INT_RGB,
        )
        val graphics = image.createGraphics()
        SplashArtwork.paintLabels(
            graphics,
            SplashArtwork.WINDOW_HEIGHT,
            SplashLabels.from(ProductIdentity(ReleaseChannel.STABLE, "0.1.0"), "20260822"),
        )
        graphics.dispose()
        assertEquals(SplashArtwork.WINDOW_WIDTH, image.width)
        assertEquals(SplashArtwork.WINDOW_HEIGHT, image.height)
    }
}

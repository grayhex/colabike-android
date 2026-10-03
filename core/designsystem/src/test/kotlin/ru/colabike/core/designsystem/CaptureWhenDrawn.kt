package ru.colabike.core.designsystem

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage

/** How many times, 100 ms apart, to look again at a window that has not been drawn. */
private const val MaxDrawTries = 20

/**
 * Captures the root of [this] into [path] once the first frame is on the screen.
 *
 * On a slow CI machine Roborazzi sometimes reads the bare window before Robolectric's render thread
 * has drawn the Compose content: an all-light image, a 100 % difference from the baseline, in the
 * slowest of a class's cases and never in the same ones twice. Advancing the virtual clock does not
 * help, because it is real time that is short. A drawn screen is never one flat colour, so a flat
 * image is waited out, in real time, and looked at again.
 */
fun ComposeContentTestRule.captureWhenDrawn(path: String) {
    var tries = 0
    while (onRoot().captureToImage().asAndroidBitmap().isFlat() && tries < MaxDrawTries) {
        tries++
        println("captureWhenDrawn: $path was not drawn yet, looking again ($tries)")
        waitForIdle()
        mainClock.advanceTimeBy(100)
        Thread.sleep(100)
    }
    onRoot().captureRoboImage(path)
}

private fun Bitmap.isFlat(): Boolean {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val first = pixels[0]
    return pixels.all { it == first }
}

package ru.colabike.app

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.shadows.ShadowLog

/** How many times, 100 ms apart, to look again at a window that has not been drawn. */
private const val MaxDrawTries = 20

/** The tree must look the same this many times in a row, a pause apart, to be called at rest. */
private const val CalmLooks = 3
private const val MaxSettleLooks = 60
private const val SettlePauseMs = 30L

/**
 * Waits until the screen has stopped changing: the semantics tree, with the place of every node, is
 * the same [CalmLooks] times in a row. Compose's own idle wait does not cover what arrives from
 * another thread (a form's findings, a loaded page), and that lands at a different moment from run
 * to run. A scroll worked out before it, or a picture taken before it, differs by that much.
 */
fun ComposeContentTestRule.settle() {
    var before = ""
    var calm = 0
    var looks = 0
    while (calm < CalmLooks && looks < MaxSettleLooks) {
        waitForIdle()
        mainClock.advanceTimeBy(100)
        Thread.sleep(SettlePauseMs)
        // A dialog is a root of its own: look at all of them.
        val now = onAllNodes(isRoot()).printToString()
        if (now == before) {
            calm++
        } else {
            calm = 0
            before = now
        }
        looks++
    }
}

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
    if (tries == MaxDrawTries) reportWhyNotDrawn(path)
    onRoot().captureRoboImage(path)
}

/**
 * The window stayed blank for the whole wait: say what Compose and the view tree look like, and
 * what Compose or the app logged, so the next red run explains itself.
 */
private fun ComposeContentTestRule.reportWhyNotDrawn(path: String) {
    println("captureWhenDrawn: $path stayed blank for ${MaxDrawTries * 100} ms")
    println(onRoot().printToString(maxDepth = 6))
    var view = onRoot().fetchSemanticsNode().root as? View
    while (view != null) {
        println(
            "  ${view.javaClass.simpleName} ${view.width}x${view.height} " +
                "attached=${view.isAttachedToWindow} laidOut=${view.isLaidOut} " +
                "shown=${view.isShown} alpha=${view.alpha}"
        )
        view = view.parent as? View
    }
    ShadowLog.getLogs()
        .filter { it.type >= Log.WARN || it.tag == "ComposeInternal" }
        .takeLast(30)
        .forEach { println("  log ${it.type} ${it.tag}: ${it.msg} ${it.throwable ?: ""}") }
    // Does anything bring the picture back? Each nudge is tried in turn and its effect printed.
    val root = onRoot().fetchSemanticsNode().root as? View ?: return
    val nudges =
        listOf<Pair<String, () -> Unit>>(
            "invalidate" to { root.invalidate() },
            "requestLayout" to { root.requestLayout() },
            "rootView.invalidate" to { root.rootView.invalidate() },
        )
    for ((name, nudge) in nudges) {
        nudge()
        waitForIdle()
        mainClock.advanceTimeBy(100)
        println("  after $name: blank=${onRoot().captureToImage().asAndroidBitmap().isFlat()}")
    }
}

private fun Bitmap.isFlat(): Boolean {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val first = pixels[0]
    return pixels.all { it == first }
}

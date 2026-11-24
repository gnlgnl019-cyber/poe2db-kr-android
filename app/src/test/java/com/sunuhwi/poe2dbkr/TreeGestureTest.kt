package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 트리 화면의 손짓을 기기 없이 확인한다.
 *
 * - 두 손가락을 벌리면 손가락 사이를 중심으로 확대되고, 오므리면 축소된다
 * - 확대·축소는 트리 전체가 보이는 배율과 노드가 손끝만 해지는 배율 사이에서 멈춘다
 * - 한 손가락으로 끌면 트리가 따라 움직이고, 끌 때는 노드가 눌리지 않는다
 * - 짧게 누르면 누른 자리를 알리고, 두 손가락으로 만진 뒤에는 눌리지 않는다
 * - 손가락 하나를 떼도 트리가 튀지 않는다 (남은 손가락 자리에서 다시 잰다)
 *
 * 화면 없이 [TreeGestures]에 손가락 자리만 넣어 보므로, 실기기에서와 같은 계산을 그대로 확인한다.
 */
class TreeGestureTest {

    private val tree: TreeDict by lazy {
        TreeDict.parse(File("src/main/assets/poe2_tree_kr.json").readText())
    }

    /** 앱에서 1080x1800 화면에 트리를 펼쳤을 때와 같은 배율 한도를 잡아 둔다 (density 3 기준) */
    private fun screenViewport(scale: Float? = null): TreeViewport {
        val viewport = TreeViewport()
        viewport.overscroll = 360f
        viewport.resize(1080, 1800)
        val geo = tree.geometry
        viewport.setBounds(geo.minX, geo.minY, geo.maxX, geo.maxY)
        val whole = viewport.wholeScale(42f)
        viewport.setZoomRange(whole * 0.92f, maxOf(26f * 3f / TreeMetrics.NORMAL, whole * 2f))
        viewport.setScale(scale ?: whole * 4f)
        return viewport
    }

    private fun point(id: Int, x: Float, y: Float) = TreePointer(id, x, y)

    private fun TreeGestures.spread(steps: Int, from: Float, to: Float) {
        down(540f - from / 2f, 900f, 0L)
        addPointer(1, 540f + from / 2f, 900f)
        for (i in 1..steps) {
            val span = from + (to - from) * i / steps
            move(listOf(point(0, 540f - span / 2f, 900f), point(1, 540f + span / 2f, 900f)))
        }
    }

    @Test
    fun pinchOutZoomsInAroundTheFingers() {
        val viewport = screenViewport()
        val before = viewport.scale
        val focusX = 540f
        val focusY = 900f
        val treeX = viewport.treeX(focusX)
        val treeY = viewport.treeY(focusY)
        val gestures = TreeGestures(viewport)
        gestures.spread(steps = 6, from = 200f, to = 800f)
        println("[TEST] 확대 ${before} → ${viewport.scale}")
        assertTrue("두 손가락을 벌렸는데 확대되지 않았다", viewport.scale > before * 3f)
        // 손가락 가운데 밑에 있던 자리는 제자리에 남는다.
        assertEquals("확대할 때 중심이 튀었다", focusX, viewport.screenX(treeX), 2f)
        assertEquals("확대할 때 중심이 튀었다", focusY, viewport.screenY(treeY), 2f)
    }

    @Test
    fun pinchInZoomsOutDownToTheWholeTree() {
        val viewport = screenViewport()
        val before = viewport.scale
        val gestures = TreeGestures(viewport)
        gestures.spread(steps = 8, from = 900f, to = 150f)
        gestures.spread(steps = 8, from = 900f, to = 150f)
        gestures.spread(steps = 8, from = 900f, to = 150f)
        println("[TEST] 축소 ${before} → ${viewport.scale} (한도 ${viewport.minScale})")
        assertTrue("두 손가락을 오므렸는데 축소되지 않았다", viewport.scale < before)
        assertTrue("트리 전체보다 더 줄어들었다", viewport.scale >= viewport.minScale - 0.0001f)
        assertTrue("배율이 0 이하로 내려갔다", viewport.scale > 0f)
    }

    @Test
    fun zoomStopsBeforeANodeFillsTheScreen() {
        val viewport = screenViewport()
        val gestures = TreeGestures(viewport)
        repeat(6) { gestures.spread(steps = 6, from = 120f, to = 1000f) }
        println("[TEST] 가장 큰 배율 ${viewport.scale} (한도 ${viewport.maxScale})")
        assertTrue("확대 한도를 넘었다", viewport.scale <= viewport.maxScale + 0.0001f)
        assertTrue("확대가 아예 안 된다", viewport.scale > viewport.minScale * 2f)
    }

    @Test
    fun oneFingerDragMovesTheTree() {
        val viewport = screenViewport(scale = screenViewport().maxScale)
        val gestures = TreeGestures(viewport, slop = 12f)
        val startX = viewport.offsetX
        val startY = viewport.offsetY
        gestures.down(540f, 900f, 0L)
        gestures.move(listOf(point(0, 440f, 850f)))
        println("[TEST] 이동 ${startX} → ${viewport.offsetX} / ${startY} → ${viewport.offsetY}")
        assertEquals("한 손가락으로 끈 만큼 움직이지 않았다", startX - 100f, viewport.offsetX, 1f)
        assertEquals("한 손가락으로 끈 만큼 움직이지 않았다", startY - 50f, viewport.offsetY, 1f)
    }

    @Test
    fun draggingDoesNotPressANode() {
        val viewport = screenViewport(scale = screenViewport().maxScale)
        val gestures = TreeGestures(viewport, slop = 12f)
        var taps = 0
        gestures.onTap = { _, _ -> taps++ }
        gestures.down(540f, 900f, 0L)
        gestures.move(listOf(point(0, 400f, 900f)))
        gestures.up(300L)
        assertEquals("끌었는데 노드가 눌렸다", 0, taps)
    }

    @Test
    fun shortPressReportsThePressedPoint() {
        val viewport = screenViewport()
        val gestures = TreeGestures(viewport)
        var tapX = -1f
        var tapY = -1f
        gestures.onTap = { x, y -> tapX = x; tapY = y }
        gestures.down(520f, 880f, 1_000L)
        gestures.up(1_150L)
        assertEquals(520f, tapX, 0.5f)
        assertEquals(880f, tapY, 0.5f)
    }

    @Test
    fun longPressIsNotAPress() {
        val viewport = screenViewport()
        val gestures = TreeGestures(viewport, tapMillis = 600L)
        var taps = 0
        gestures.onTap = { _, _ -> taps++ }
        gestures.down(520f, 880f, 0L)
        gestures.up(2_500L)
        assertEquals("오래 누른 것도 노드 누르기로 셌다", 0, taps)
    }

    @Test
    fun twoFingersNeverPressANode() {
        val viewport = screenViewport()
        val gestures = TreeGestures(viewport)
        var taps = 0
        gestures.onTap = { _, _ -> taps++ }
        gestures.down(400f, 900f, 0L)
        gestures.addPointer(1, 700f, 900f)
        gestures.up(120L)
        assertEquals("두 손가락으로 눌렀는데 노드가 눌렸다", 0, taps)
    }

    /** 손가락 하나를 떼면 트리가 남은 손가락 쪽으로 튀던 문제 */
    @Test
    fun liftingOneFingerKeepsTheTreeInPlace() {
        val viewport = screenViewport(scale = screenViewport().maxScale)
        val gestures = TreeGestures(viewport)
        gestures.down(400f, 900f, 0L)
        gestures.addPointer(1, 700f, 900f)
        gestures.move(listOf(point(0, 300f, 900f), point(1, 800f, 900f)))
        gestures.liftPointer(1)
        val afterPinch = viewport.offsetX
        gestures.move(listOf(point(0, 310f, 900f)))
        println("[TEST] 손가락을 뗀 뒤 이동 ${afterPinch} → ${viewport.offsetX}")
        assertEquals("손가락을 뗀 뒤 트리가 튀었다", afterPinch + 10f, viewport.offsetX, 1f)
        gestures.up(400L)
    }

    @Test
    fun pinchKeepsTheTreeInsideTheScreen() {
        val viewport = screenViewport(scale = viewportMaxScale())
        val gestures = TreeGestures(viewport)
        gestures.spread(steps = 4, from = 700f, to = 150f)
        // 트리를 한참 밀어도 화면 밖으로 사라지지 않는다.
        viewport.pan(100_000f, 100_000f)
        val geo = tree.geometry
        assertTrue("트리가 화면 밖으로 밀려났다", viewport.screenX(geo.minX) <= 1080f)
        assertTrue("트리가 화면 밖으로 밀려났다", viewport.screenY(geo.minY) <= 1800f)
    }

    private fun viewportMaxScale(): Float = screenViewport().maxScale

    @Test
    fun zoomRangeNeverLeavesScaleInAnImpossibleSpot() {
        val viewport = screenViewport()
        // 한도가 뒤집혀 들어와도 배율이 0이나 음수가 되지 않는다.
        viewport.setZoomRange(2f, 1f)
        assertTrue("한도가 뒤집혔다", viewport.maxScale >= viewport.minScale)
        viewport.setScale(0f)
        assertTrue("배율이 0이 되었다", viewport.scale > 0f)
        viewport.zoomAround(0f, 540f, 900f)
        viewport.zoomAround(Float.NaN, 540f, 900f)
        assertTrue("배율이 0이 되었다", viewport.scale > 0f)
    }

    @Test
    fun flingOnlyHappensAfterAFlick() {
        val viewport = screenViewport(scale = viewportMaxScale())
        val gestures = TreeGestures(viewport, slop = 12f, flickSpeed = 6f)
        var flicked = 0
        gestures.onFlick = { _, _ -> flicked++ }
        gestures.down(540f, 900f, 0L)
        gestures.move(listOf(point(0, 440f, 900f)))
        gestures.move(listOf(point(0, 340f, 900f)))
        gestures.up(120L)
        assertEquals("빠르게 민 뒤 흘러가지 않는다", 1, flicked)
        gestures.down(540f, 900f, 0L)
        gestures.up(90L)
        assertEquals("누르기만 했는데 흘러갔다", 1, flicked)
    }

    @Test
    fun gesturesAreIgnoredBeforeAnyFingerArrives() {
        val viewport = screenViewport()
        val gestures = TreeGestures(viewport)
        val before = viewport.scale
        gestures.move(listOf(point(0, 100f, 100f)))
        gestures.liftPointer(0)
        gestures.up(10L)
        assertFalse("손가락이 없는데 배율이 바뀌었다", viewport.scale != before)
    }
}

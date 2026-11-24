package com.sunuhwi.poe2dbkr

import kotlin.math.abs
import kotlin.math.hypot

/** 손가락 하나의 지금 자리 */
data class TreePointer(val id: Int, val x: Float, val y: Float)

/**
 * 트리 화면의 손짓을 세는 칸.
 *
 * - 두 손가락을 벌리거나 오므리면 그 가운데를 중심으로 확대·축소
 * - 한 손가락으로 끌면 이동 (손을 빠르게 떼면 그 방향으로 조금 더 흘러간다)
 * - 끌지 않고 짧게 누르면 그 자리를 알린다 (노드 고르기)
 *
 * 안드로이드 화면 클래스에 기대지 않고 손가락 자리만 받아 계산하므로,
 * 기기 없이 시험에서 같은 손짓을 그대로 넣어 확인할 수 있다.
 */
class TreeGestures(
    private val viewport: TreeViewport,
    /** 이만큼 넘게 움직이면 누르기가 아니라 끌기로 본다 (px) */
    private val slop: Float = 12f,
    /** 손을 뗀 시간이 이보다 짧아야 누르기로 본다 (ms) */
    private val tapMillis: Long = 600L,
    /** 한 번에 이보다 빠르게 밀었으면 손을 뗀 뒤에도 흘러가게 한다 (px) */
    private val flickSpeed: Float = 6f,
) {

    /** 트리가 움직였을 때 (화면을 다시 그린다) */
    var onMoved: (() -> Unit)? = null

    /** 화면의 한 점을 끌지 않고 눌렀을 때 */
    var onTap: ((Float, Float) -> Unit)? = null

    /** 손을 뗀 뒤에도 흘러갈 속도 (px) */
    var onFlick: ((Float, Float) -> Unit)? = null

    private val ids = ArrayList<Int>(2)
    private val xs = ArrayList<Float>(2)
    private val ys = ArrayList<Float>(2)

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var dragged = false
    private var pinched = false
    private var lastSpan = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var speedX = 0f
    private var speedY = 0f

    /** 지금 화면에 닿아 있는 손가락 수 */
    val fingers: Int get() = ids.size

    /** 두 손가락 이상으로 확대·축소하는 중인지 */
    val isPinching: Boolean get() = ids.size >= 2

    /** 첫 손가락이 닿았다 */
    fun down(x: Float, y: Float, timeMillis: Long) {
        ids.clear()
        xs.clear()
        ys.clear()
        ids.add(0)
        xs.add(x)
        ys.add(y)
        downX = x
        downY = y
        downTime = timeMillis
        lastX = x
        lastY = y
        dragged = false
        pinched = false
        lastSpan = 0f
        lastFocusX = x
        lastFocusY = y
        speedX = 0f
        speedY = 0f
    }

    /** 손가락이 하나 더 닿았다 — 이때부터는 누르기가 아니라 확대·축소다. */
    fun addPointer(id: Int, x: Float, y: Float) {
        if (ids.isEmpty()) {
            down(x, y, 0L)
            return
        }
        if (ids.contains(id)) return
        ids.add(id)
        xs.add(x)
        ys.add(y)
        pinched = true
        dragged = true
        // 손가락 사이 간격은 여기서부터 다시 잰다.
        resetSpan()
    }

    /**
     * 지금 닿아 있는 손가락 자리를 모두 받아 트리를 움직인다.
     * 넘어온 자리에 없는 손가락은 그대로 둔다 (일부만 알려 줘도 된다).
     */
    fun move(pointers: List<TreePointer>) {
        if (ids.isEmpty() || pointers.isEmpty()) return
        for (pointer in pointers) {
            val index = ids.indexOf(pointer.id)
            if (index < 0) continue
            xs[index] = pointer.x
            ys[index] = pointer.y
        }
        if (ids.size >= 2) {
            pinchStep()
            return
        }
        val x = xs[0]
        val y = ys[0]
        val dx = x - lastX
        val dy = y - lastY
        lastX = x
        lastY = y
        if (hypot(x - downX, y - downY) > slop) dragged = true
        if (dx == 0f && dy == 0f) return
        viewport.pan(dx, dy)
        speedX = dx
        speedY = dy
        onMoved?.invoke()
    }

    /** 두 손가락 사이 간격과 가운데로 확대·축소한다. */
    private fun pinchStep() {
        val span = hypot(xs[1] - xs[0], ys[1] - ys[0])
        val focusX = (xs[0] + xs[1]) / 2f
        val focusY = (ys[0] + ys[1]) / 2f
        if (lastSpan <= 0f) {
            // 처음 잰 간격은 기준으로만 두고, 다음 움직임부터 배율을 바꾼다.
            lastSpan = span
            lastFocusX = focusX
            lastFocusY = focusY
            return
        }
        if (span > 0.5f && lastSpan > 0.5f) {
            viewport.zoomAround(span / lastSpan, focusX, focusY)
        }
        // 손가락 가운데가 옮겨 간 만큼 트리도 따라 움직인다.
        val shiftX = focusX - lastFocusX
        val shiftY = focusY - lastFocusY
        if (abs(shiftX) > 0.01f || abs(shiftY) > 0.01f) viewport.pan(shiftX, shiftY)
        lastSpan = span
        lastFocusX = focusX
        lastFocusY = focusY
        speedX = 0f
        speedY = 0f
        onMoved?.invoke()
    }

    /** 손가락 하나가 떨어졌다. 남은 손가락 자리에서 다시 잰다 (트리가 튀지 않게). */
    fun liftPointer(id: Int) {
        val index = ids.indexOf(id)
        if (index < 0) return
        ids.removeAt(index)
        xs.removeAt(index)
        ys.removeAt(index)
        if (ids.isEmpty()) return
        // 남은 손가락의 지금 자리를 기준으로 삼는다.
        lastX = xs[0]
        lastY = ys[0]
        resetSpan()
    }

    /** 마지막 손가락이 떨어졌다. */
    fun up(timeMillis: Long) {
        val held = timeMillis - downTime
        val tapped = !pinched && !dragged && held <= tapMillis
        val flickX = speedX
        val flickY = speedY
        clear()
        if (tapped) {
            onTap?.invoke(downX, downY)
            return
        }
        if (pinched) return
        if (abs(flickX) + abs(flickY) > flickSpeed) onFlick?.invoke(flickX, flickY)
    }

    /** 손짓이 취소됐다 (화면이 바뀌거나 다른 창이 덮었을 때). */
    fun cancel() {
        clear()
    }

    private fun resetSpan() {
        if (ids.size >= 2) {
            lastSpan = hypot(xs[1] - xs[0], ys[1] - ys[0])
            lastFocusX = (xs[0] + xs[1]) / 2f
            lastFocusY = (ys[0] + ys[1]) / 2f
        } else {
            lastSpan = 0f
        }
    }

    private fun clear() {
        ids.clear()
        xs.clear()
        ys.clear()
        dragged = false
        pinched = false
        lastSpan = 0f
        speedX = 0f
        speedY = 0f
    }
}

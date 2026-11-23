package com.sunuhwi.poe2dbkr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 불러온 캐릭터의 패시브 트리를 앱 안에서 직접 그리는 화면.
 *
 * poe2db 트리 페이지를 웹뷰로 여는 대신, assets에 담아 둔 노드 좌표와 연결선으로 그린다.
 * - 두 손가락으로 확대·축소, 한 손가락으로 이동
 * - 찍은 노드는 황금색, 나머지는 어두운 회색
 * - 노드를 누르면 [OnNodeTap]으로 알려 준다 (한글 이름·설명은 화면 쪽에서 띄운다)
 */
class TreeCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    fun interface OnNodeTap {
        fun onTap(nodeId: Int, allocated: Boolean)
    }

    var onNodeTap: OnNodeTap? = null

    private val viewport = TreeViewport()
    private var tree: TreeDict = TreeDict.EMPTY
    private var geometry: TreeGeometry = TreeGeometry.EMPTY
    private var allocated = BooleanArray(0)

    private val density = resources.displayMetrics.density
    private val lineArray = FloatArray(26000)
    private val pointArray = FloatArray(8400)

    private val bgPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.bg) }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39404C")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val edgeOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.gold_soft)
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4E5665")
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                viewport.zoomAround(detector.scaleFactor, detector.focusX, detector.focusY)
                invalidate()
                return true
            }
        },
    )

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var dragging = false

    private val normalRadius = 30f
    private val notableRadius = 46f
    private val keystoneRadius = 62f
    private val masteryRadius = 48f
    private val jewelRadius = 46f
    private val ascendRadius = 34f
    private val startRadius = 74f

    private val gold = ContextCompat.getColor(context, R.color.gold)
    private val goldSoft = ContextCompat.getColor(context, R.color.gold_soft)

    init {
        isClickable = true
        isFocusable = true
    }

    /** 빌드의 찍은 노드로 트리를 채운다. */
    fun bind(tree: TreeDict, nodeIds: List<Int>, masteryIds: List<Int> = emptyList()) {
        this.tree = tree
        val geo = tree.geometry
        geometry = geo
        allocated = BooleanArray(geo.size)
        nodeIds.forEach { id -> mark(geo, id) }
        masteryIds.forEach { id -> mark(geo, id) }
        reset()
    }

    private fun mark(geo: TreeGeometry, id: Int) {
        val i = geo.indexOf(id)
        if (i >= 0) allocated[i] = true
    }

    fun clear() {
        geometry = TreeGeometry.EMPTY
        allocated = BooleanArray(0)
        invalidate()
    }

    fun isEmpty(): Boolean = geometry.size == 0

    /** 찍은 노드가 모인 자리를 화면에 꽉 채운다. 찍은 노드가 없으면 트리 전체. */
    fun reset() {
        if (geometry.size == 0) return
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until geometry.size) {
            if (!allocated[i]) continue
            minX = minOf(minX, geometry.xs[i])
            minY = minOf(minY, geometry.ys[i])
            maxX = maxOf(maxX, geometry.xs[i])
            maxY = maxOf(maxY, geometry.ys[i])
        }
        if (minX > maxX) {
            viewport.fit(width, height, geometry.minX, geometry.minY, geometry.maxX, geometry.maxY, 20f * density)
        } else {
            // 찍은 노드가 한 줄로 모여 있어도 너무 붙지 않게 최소 폭을 준다.
            viewport.fit(width, height, minX - 700f, minY - 700f, maxX + 700f, maxY + 700f, 24f * density)
        }
        invalidate()
    }

    /** 트리 전체를 본다. */
    fun showWholeTree() {
        if (geometry.size == 0) return
        viewport.fit(width, height, geometry.minX, geometry.minY, geometry.maxX, geometry.maxY, 20f * density)
        invalidate()
    }

    fun zoomIn() = zoom(1.6f)

    fun zoomOut() = zoom(1 / 1.6f)

    private fun zoom(factor: Float) {
        viewport.zoomAround(factor, width / 2f, height / 2f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        reset()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val geo = geometry
        if (geo.size == 0) return
        val s = viewport.scale
        val left = -viewport.offsetX / s
        val top = -viewport.offsetY / s
        val right = (width - viewport.offsetX) / s
        val bottom = (height - viewport.offsetY) / s

        drawEdges(canvas, geo, s, left, top, right, bottom)
        drawNodes(canvas, geo, s, left, top, right, bottom)
        drawLabels(canvas, geo, s, left, top, right, bottom)
    }

    private fun drawEdges(
        canvas: Canvas,
        geo: TreeGeometry,
        s: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val stroke = (10f * s).coerceAtLeast(1f)
        var plain = 0
        var i = 0
        while (i + 1 < geo.edges.size) {
            val a = geo.edges[i]
            val b = geo.edges[i + 1]
            i += 2
            if (outside(geo, a, b, left, top, right, bottom)) continue
            if (plain + 4 > lineArray.size) break
            lineArray[plain] = viewport.screenX(geo.xs[a])
            lineArray[plain + 1] = viewport.screenY(geo.ys[a])
            lineArray[plain + 2] = viewport.screenX(geo.xs[b])
            lineArray[plain + 3] = viewport.screenY(geo.ys[b])
            plain += 4
        }
        if (plain > 0) {
            edgePaint.strokeWidth = stroke
            canvas.drawLines(lineArray, 0, plain, edgePaint)
        }
        // 찍은 노드끼리 이어진 길은 황금색으로 덧그린다.
        edgeOnPaint.strokeWidth = stroke * 1.7f
        i = 0
        while (i + 1 < geo.edges.size) {
            val a = geo.edges[i]
            val b = geo.edges[i + 1]
            i += 2
            if (!allocated[a] || !allocated[b]) continue
            if (outside(geo, a, b, left, top, right, bottom)) continue
            canvas.drawLine(
                viewport.screenX(geo.xs[a]), viewport.screenY(geo.ys[a]),
                viewport.screenX(geo.xs[b]), viewport.screenY(geo.ys[b]),
                edgeOnPaint,
            )
        }
    }

    private fun outside(
        geo: TreeGeometry,
        a: Int,
        b: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean {
        val ax = geo.xs[a]
        val bx = geo.xs[b]
        if (maxOf(ax, bx) < left || minOf(ax, bx) > right) return true
        val ay = geo.ys[a]
        val by = geo.ys[b]
        return maxOf(ay, by) < top || minOf(ay, by) > bottom
    }

    private fun drawNodes(
        canvas: Canvas,
        geo: TreeGeometry,
        s: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        var points = 0
        for (i in 0 until geo.size) {
            val x = geo.xs[i]
            val y = geo.ys[i]
            if (x < left - 200 || x > right + 200 || y < top - 200 || y > bottom + 200) continue
            val kind = geo.kindAt(i)
            val on = allocated[i]
            if (!on && (kind == TreeKind.NORMAL || kind == TreeKind.ASCENDANT)) {
                if (points + 2 <= pointArray.size) {
                    pointArray[points] = viewport.screenX(x)
                    pointArray[points + 1] = viewport.screenY(y)
                    points += 2
                }
                continue
            }
            val sx = viewport.screenX(x)
            val sy = viewport.screenY(y)
            val radius = radiusOf(kind) * s
            ringPaint.strokeWidth = (radius * 0.42f).coerceAtLeast(1.5f)
            nodePaint.color = when {
                on -> gold
                kind == TreeKind.KEYSTONE -> Color.parseColor("#2A2118")
                kind == TreeKind.NOTABLE -> Color.parseColor("#1D2029")
                else -> Color.parseColor("#161A21")
            }
            ringPaint.color = when {
                on -> goldSoft
                kind == TreeKind.KEYSTONE -> Color.parseColor("#B4762F")
                kind == TreeKind.ASCENDANT_START -> Color.parseColor("#8C9AB0")
                kind == TreeKind.CLASS_START -> Color.parseColor("#9AA6B8")
                kind == TreeKind.NOTABLE -> Color.parseColor("#6B7688")
                kind == TreeKind.MASTERY -> Color.parseColor("#506A8A")
                else -> Color.parseColor("#3A414D")
            }
            canvas.drawCircle(sx, sy, radius, nodePaint)
            canvas.drawCircle(sx, sy, radius, ringPaint)
        }
        if (points > 0) {
            pointPaint.strokeWidth = (normalRadius * 0.9f * s).coerceAtLeast(1.2f)
            canvas.drawPoints(pointArray, 0, points, pointPaint)
        }
    }

    private fun drawLabels(
        canvas: Canvas,
        geo: TreeGeometry,
        s: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        // 노드가 화면에서 충분히 커졌을 때만 이름을 적는다.
        val size = 11f * density
        if (notableRadius * s < size * 1.5f) return
        labelOnPaint.textSize = size
        for (i in 0 until geo.size) {
            if (!allocated[i]) continue
            val kind = geo.kindAt(i)
            if (kind != TreeKind.KEYSTONE && kind != TreeKind.NOTABLE) continue
            val x = geo.xs[i]
            val y = geo.ys[i]
            if (x < left || x > right || y < top || y > bottom) continue
            val node = tree.node(geo.ids[i]) ?: continue
            val label = node.displayName
            if (label.isBlank()) continue
            canvas.drawText(
                label,
                viewport.screenX(x),
                viewport.screenY(y) - radiusOf(kind) * s - size * 0.3f,
                labelOnPaint,
            )
        }
    }

    private fun radiusOf(kind: TreeKind): Float = when (kind) {
        TreeKind.NOTABLE -> notableRadius
        TreeKind.KEYSTONE -> keystoneRadius
        TreeKind.MASTERY -> masteryRadius
        TreeKind.JEWEL -> jewelRadius
        TreeKind.ASCENDANT -> ascendRadius
        TreeKind.ASCENDANT_START -> startRadius
        TreeKind.CLASS_START -> startRadius
        else -> normalRadius
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> dragging = true
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (abs(dx) + abs(dy) > 2f * density) dragging = true
                    if (dragging) {
                        viewport.pan(dx, dy)
                        invalidate()
                    }
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val moved = hypot(event.x - downX, event.y - downY)
                if (!dragging && moved < 12f * density &&
                    System.currentTimeMillis() - downTime < 600
                ) {
                    pick(event.x, event.y)
                }
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    /** 누른 자리 가까이에 있는 노드를 찾아 알린다. */
    private fun pick(sx: Float, sy: Float) {
        val geo = geometry
        if (geo.size == 0) return
        val reach = (30f * density) / viewport.scale
        val index = geo.nearest(viewport.treeX(sx), viewport.treeY(sy), reach)
        if (index >= 0) onNodeTap?.onTap(geo.ids[index], allocated[index])
    }
}

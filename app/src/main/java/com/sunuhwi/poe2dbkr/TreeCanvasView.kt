package com.sunuhwi.poe2dbkr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 트리 한 노드의 이름·설명·아이콘을 배열로 모아 둔 묶음 (노드 5천 개를 객체로 다루지 않는다) */
class TreeScene(
    val geometry: TreeGeometry,
    /** 노드 아이콘 스프라이트 안의 자리 */
    val icons: Array<IntArray?>,
    val names: Array<String>,
    val stats: Array<String>,
    /** 전직 노드인지 (포인트를 전직 몫으로 센다) */
    val ascendancy: BooleanArray = BooleanArray(0),
    /** 아이콘을 모아 둔 assets 파일 */
    val iconAsset: String,
    /** 직업 시작 노드의 배열 위치 (없으면 -1) */
    val classStart: Int = -1,
    /** 전직 시작 노드의 배열 위치 (없으면 -1) */
    val ascendStart: Int = -1,
    /** 이 전직의 노드들 — 메달 둘레에 장식으로 두른다 */
    val ascendRing: IntArray = IntArray(0),
    /** 트리에 쓸 수 있는 포인트 (아틀라스는 총점, 패시브는 레벨로 계산) */
    val pointTotal: Int = 0,
) {
    val size: Int get() = geometry.size

    companion object {
        val EMPTY = TreeScene(TreeGeometry.EMPTY, emptyArray(), emptyArray(), emptyArray(), BooleanArray(0), "")

        fun of(tree: TreeDict, classIndex: Int = -1, ascendIndex: Int = 0): TreeScene {
            val geo = tree.geometry
            val icons = arrayOfNulls<IntArray>(geo.size)
            val names = Array(geo.size) { "" }
            val stats = Array(geo.size) { "" }
            val ascendancy = BooleanArray(geo.size)
            for (i in 0 until geo.size) {
                val node = tree.node(geo.ids[i]) ?: continue
                icons[i] = node.icon
                names[i] = node.displayName
                stats[i] = node.stats
                ascendancy[i] = node.isAscendancy
            }
            val classStart = if (classIndex >= 0) geo.indexOf(tree.startNode(classIndex, 0)) else -1
            val ascendStart = if (classIndex >= 0) geo.indexOf(tree.startNode(classIndex, ascendIndex)) else -1
            val ring = ArrayList<Int>()
            if (classIndex >= 0) {
                // 전직 내부 이름(예: Monk1)은 전직 시작 노드가 들고 있다.
                val ascId = tree.ascStarts[classIndex * 100 + ascendIndex]
                    ?.let { tree.nodes[it]?.ascendancy }
                    .orEmpty()
                if (ascId.isNotBlank()) {
                    tree.nodes.values.forEach { node ->
                        if (node.ascendancy.equals(ascId, ignoreCase = true) && node.isNotable) {
                            val index = geo.indexOf(node.id)
                            if (index >= 0) ring.add(index)
                        }
                    }
                }
            }
            return TreeScene(
                geometry = geo,
                icons = icons,
                names = names,
                stats = stats,
                ascendancy = ascendancy,
                iconAsset = tree.iconAsset,
                classStart = classStart,
                ascendStart = ascendStart,
                ascendRing = ring.toIntArray(),
            )
        }

        fun of(atlas: AtlasDict): TreeScene {
            val geo = atlas.geometry
            val icons = arrayOfNulls<IntArray>(geo.size)
            val names = Array(geo.size) { "" }
            val stats = Array(geo.size) { "" }
            for (i in 0 until geo.size) {
                val node = atlas.node(geo.ids[i]) ?: continue
                icons[i] = node.icon
                names[i] = node.name
                stats[i] = node.stats
            }
            return TreeScene(
                geometry = geo,
                icons = icons,
                names = names,
                stats = stats,
                iconAsset = atlas.iconAsset,
                pointTotal = atlas.totalPoints,
            )
        }
    }
}

/** 검색으로 찾은 노드 한 줄 */
data class TreeHit(
    val id: Int,
    val name: String,
    val stats: String,
)

/** 트리에서 지금까지 찍은 포인트 */
data class TreePoints(    val allocated: Int = 0,
    val total: Int = 0,
    val ascendancy: Int = 0,
    val ascendancyTotal: Int = 0,
)

/**
 * 패시브·아틀라스 트리를 앱 안에서 직접 그리는 화면.
 *
 * poe2db 트리를 웹뷰로 열지 않고, assets에 담아 둔 노드 좌표·연결선·아이콘 스프라이트로 그린다.
 * - 두 손가락으로 확대·축소, 한 손가락으로 이동(던지면 관성으로 흘러간다)
 * - 찍은 노드는 금빛으로 빛나고, 이어진 길도 금색으로 밝아진다
 * - 트리 한가운데에 그 직업의 일러스트 메달을 두고 전직 노드를 둘레에 두른다
 * - 노드를 누르면 가까운 노드를 골라 [OnNodeTap]으로 알리고, 이어진 자리면 바로 찍는다
 */
class TreeCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    fun interface OnNodeTap {
        fun onTap(nodeId: Int, allocated: Boolean)
    }

    /** 찍은 포인트가 바뀌면 알린다 (위쪽 카운터를 새로 그린다) */
    fun interface OnPointsChanged {
        fun onChanged(points: TreePoints)
    }

    var onNodeTap: OnNodeTap? = null
    var onPointsChanged: OnPointsChanged? = null

    /** 그리는 도중 예외가 나면 알린다 (앱이 종료되지 않게 poe2db 트리로 넘어가는 데 쓴다) */
    var onRenderFailed: ((Throwable) -> Unit)? = null

    /** 그리기가 실패한 뒤에는 다시 그리지 않는다 */
    private var renderFailed = false

    private val viewport = TreeViewport()
    private var scene: TreeScene = TreeScene.EMPTY
    private var tree: TreeDict = TreeDict.EMPTY
    private var alloc = TreeAllocation(TreeGeometry.EMPTY)
    private val allocated: BooleanArray get() = alloc.allocated
    private var iconSheet: SpriteSheet? = null
    private var classSheet: SpriteSheet? = null
    private var classArtRect: IntArray? = null
    private var classArtAsset: String = ""

    private var pointTotal = 0
    private var ascendancyTotal = 8

    private val density = resources.displayMetrics.density
    private val lineArray = FloatArray(64000)
    private val edgeOnArray = FloatArray(4000)
    private val pointArray = FloatArray(16000)

    private val bgPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.bg) }
    private var bgShader: Shader? = null
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A4A2E.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val edgeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66E8C060
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val edgeOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8C060.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFF0DCA8.toInt()
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5C5343.toInt()
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val medallionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val medallionRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF8C7340.toInt()
    }
    private val searchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFF6A4A.toInt()
    }

    private var highlight = -1
    private var flash = 0f
    private val clipPath = android.graphics.Path()

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                flinging = false
                viewport.zoomAround(detector.scaleFactor, detector.focusX, detector.focusY)
                clamp()
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
    private var velocityX = 0f
    private var velocityY = 0f
    private var flinging = false

    private val normalRadius = 26f
    private val notableRadius = 44f
    private val keystoneRadius = 60f
    private val masteryRadius = 40f
    private val jewelRadius = 34f
    private val ascendRadius = 32f

    private val gold = 0xFFE8C060.toInt()
    private val goldSoft = 0xFFF0DCA8.toInt()
    private val bronze = 0xFF8C7340.toInt()

    init {
        isClickable = true
        isFocusable = true
    }

    /** 직업 일러스트 (메달) — MainActivity가 한 번 넣어 둔다 */
    fun bindClassArt(arts: ClassArts) {
        classArtAsset = arts.file
        classSheet = if (arts.file.isBlank()) null else SpriteSheet.of(context, arts.file)
    }

    /**
     * 직업·전직을 바꾼다. 메달 그림과 전직 링이 함께 바뀌고, 그 직업 시작점으로 화면을 옮긴다.
     */
    fun setClass(tree: TreeDict, classIndex: Int, ascendIndex: Int, arts: ClassArts? = null) {
        if (tree.isEmpty()) return
        this.tree = tree
        val next = TreeScene.of(tree, classIndex, ascendIndex)
        val previous = alloc.allocatedIds()
        scene = next
        if (next.size == 0) {
            clear()
            return
        }
        iconSheet = SpriteSheet.of(context, next.iconAsset)
        classArtRect = arts?.of(tree.classes.getOrNull(classIndex)?.nameEn.orEmpty())
            ?: classArtRect
        bindAllocation(previous)
        alloc.markBound()
        centerOnStart()
        notifyPoints()
        invalidate()
    }

    /** PoB 빌드의 직업·전직·찍은 노드로 트리를 채운다. */
    fun bind(tree: TreeDict, nodeIds: List<Int>, masteryIds: List<Int> = emptyList()) {
        bindClassArt(ClassArts.of(context))
        val classIndex = tree.classIndex(classOf(tree, nodeIds))
        bind(tree, nodeIds, masteryIds, classIndex, 1)
    }

    /** 직업을 직접 지정해 빌드 트리를 채운다 (PoB 로드 경로). */
    fun bind(
        tree: TreeDict,
        nodeIds: List<Int>,
        masteryIds: List<Int>,
        classIndex: Int,
        ascendIndex: Int,
    ) {
        this.tree = tree
        scene = TreeScene.of(tree, classIndex, ascendIndex)
        if (scene.size == 0) {
            clear()
            return
        }
        iconSheet = SpriteSheet.of(context, scene.iconAsset)
        if (classSheet == null) bindClassArt(ClassArts.of(context))
        classArtRect = classSheet?.let { _ ->
            ClassArts.of(context).of(tree.classes.getOrNull(classIndex)?.nameEn.orEmpty())
        }
        pointTotal = totalFor(tree, classIndex)
        bindAllocation(nodeIds + masteryIds)
        alloc.markBound()
        centerOnStart()
        notifyPoints()
        invalidate()
    }

    /** 아틀라스 트리를 채운다. */
    fun bindAtlas(atlas: AtlasDict, nodeIds: List<Int> = emptyList()) {
        this.tree = TreeDict.EMPTY
        scene = TreeScene.of(atlas)
        if (scene.size == 0) {
            clear()
            return
        }
        iconSheet = SpriteSheet.of(context, scene.iconAsset)
        classSheet = null
        classArtRect = null
        pointTotal = atlas.totalPoints
        bindAllocation(nodeIds)
        alloc.markBound()
        viewport.fit(width, height, scene.geometry.minX, scene.geometry.minY, scene.geometry.maxX, scene.geometry.maxY, 18f * density)
        invalidate()
        notifyPoints()
    }

    /** 아틀라스의 지금 찍은 노드 (PoB가 주지 않으므로 직접 찍은 것만) */
    fun allocatedIds(): List<Int> {
        val out = ArrayList<Int>()
        for (i in allocated.indices) if (allocated[i]) out.add(scene.geometry.ids[i])
        return out
    }

    private fun bindAllocation(ids: List<Int>) {
        alloc = TreeAllocation(
            geometry = scene.geometry,
            ascendancy = scene.ascendancy,
            classStart = scene.classStart,
            ascendStart = scene.ascendStart,
            pointTotal = pointTotal,
            ascendancyTotal = ascendancyTotal,
        )
        alloc.bind(ids)
    }

    fun clear() {
        scene = TreeScene.EMPTY
        alloc = TreeAllocation(TreeGeometry.EMPTY)
        iconSheet = null
        invalidate()
    }

    fun isEmpty(): Boolean = scene.size == 0

    /** 찍은 포인트 수 (위쪽 카운터용) */
    fun points(): TreePoints {
        alloc.pointTotal = pointTotal
        alloc.ascendancyTotal = ascendancyTotal
        return alloc.points()
    }

    fun nodeStats(id: Int): String {
        val index = scene.geometry.indexOf(id)
        return if (index >= 0) scene.stats[index] else ""
    }

    fun nodeName(id: Int): String {
        val index = scene.geometry.indexOf(id)
        return if (index >= 0) scene.names[index] else ""
    }

    fun isAllocated(id: Int): Boolean = alloc.isAllocated(id)

    /** 이어진 자리면 찍는다. 규칙에 걸리면 이유를 돌려준다. */
    fun allocate(id: Int): TreeAllocation.Result {
        val result = alloc.allocate(id)
        if (result == TreeAllocation.Result.DONE) {
            notifyPoints()
            invalidate()
        }
        return result
    }

    /** 찍은 노드를 되돌린다. 다른 노드가 끊기면 되돌리지 않는다. */
    fun deallocate(id: Int): TreeAllocation.Result {
        val result = alloc.deallocate(id)
        if (result == TreeAllocation.Result.DONE) {
            notifyPoints()
            invalidate()
        }
        return result
    }

    /** 빌드에서 불러온 상태로 되돌린다. */
    fun resetAllocation() {
        if (!alloc.resetToBound()) return
        notifyPoints()
        invalidate()
    }

    fun hasAllocationToReset(): Boolean = alloc.hasChanges

    /** 누르지 않고 이름으로 노드를 찾는다 (검색) */
    fun find(query: String, limit: Int = 8): List<TreeHit> {
        val key = query.trim()
        if (key.isEmpty() || scene.size == 0) return emptyList()
        val out = ArrayList<TreeHit>()
        for (i in 0 until scene.size) {
            val name = scene.names[i]
            if (name.isBlank() || !name.contains(key, ignoreCase = true)) continue
            out.add(TreeHit(scene.geometry.ids[i], name, scene.stats[i]))
            if (out.size >= limit) break
        }
        return out
    }

    /** 검색 결과로 화면을 옮기고 표시한다. */
    fun focusNode(id: Int, zoomIn: Boolean = true): Boolean {
        val index = scene.geometry.indexOf(id)
        if (index < 0) return false
        highlight = index
        flash = 1.6f
        val targetScale = if (zoomIn) maxOf(viewport.scale, viewport.fitScale * 3.2f) else viewport.scale
        viewport.setScale(targetScale)
        viewport.center(scene.geometry.xs[index], scene.geometry.ys[index], width, height)
        clamp()
        invalidate()
        return true
    }

    fun zoomIn() = zoom(1.6f)

    fun zoomOut() = zoom(1 / 1.6f)

    private fun zoom(factor: Float) {
        viewport.zoomAround(factor, width / 2f, height / 2f)
        clamp()
        invalidate()
    }

    /** 찍은 노드가 모인 자리를 화면에 꽉 채운다. 찍은 노드가 없으면 트리 전체. */
    fun reset() {
        if (scene.size == 0) return
        val geo = scene.geometry
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until geo.size) {
            if (!allocated[i]) continue
            minX = minOf(minX, geo.xs[i])
            minY = minOf(minY, geo.ys[i])
            maxX = maxOf(maxX, geo.xs[i])
            maxY = maxOf(maxY, geo.ys[i])
        }
        if (minX > maxX) {
            viewport.fit(width, height, geo.minX, geo.minY, geo.maxX, geo.maxY, 20f * density)
        } else {
            viewport.fit(width, height, minX - 900f, minY - 900f, maxX + 900f, maxY + 900f, 26f * density)
        }
        invalidate()
    }

    /** 트리 전체를 본다. */
    fun showWholeTree() {
        if (scene.size == 0) return
        val geo = scene.geometry
        viewport.fit(width, height, geo.minX, geo.minY, geo.maxX, geo.maxY, 20f * density)
        invalidate()
    }

    /** 시작 노드와 찍은 노드를 함께 담는 자리로 화면을 맞춘다. */
    private fun centerOnStart() {
        if (scene.size == 0) return
        val geo = scene.geometry
        val start = scene.classStart
        var minX = if (start >= 0) geo.xs[start] else Float.MAX_VALUE
        var minY = if (start >= 0) geo.ys[start] else Float.MAX_VALUE
        var maxX = if (start >= 0) geo.xs[start] else -Float.MAX_VALUE
        var maxY = if (start >= 0) geo.ys[start] else -Float.MAX_VALUE
        for (i in allocated.indices) {
            if (!allocated[i]) continue
            // 전직 노드는 트리 반대편에 모여 있어 화면 맞춤에서 뺀다 (메달 둘레에 따로 그린다).
            if (i == scene.ascendStart || scene.ascendancy.getOrElse(i) { false }) continue
            minX = minOf(minX, geo.xs[i])
            minY = minOf(minY, geo.ys[i])
            maxX = maxOf(maxX, geo.xs[i])
            maxY = maxOf(maxY, geo.ys[i])
        }
        if (minX > maxX || minY > maxY) {
            reset()
            return
        }
        val padX = (maxX - minX) * 0.22f + 700f
        val padY = (maxY - minY) * 0.22f + 700f
        viewport.fit(width, height, minX - padX, minY - padY, maxX + padX, maxY + padY, 8f * density)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 폭이나 높이가 0인 순간(화면 회전·분할 화면)에는 RadialGradient가 예외를 던진다.
        if (w > 0 && h > 0) {
            bgShader = RadialGradient(
                w * 0.5f, h * 0.45f, maxOf(w, h) * 0.78f,
                intArrayOf(0xFF131A26.toInt(), 0xFF0B0E14.toInt(), 0xFF05070A.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        // 처음 그려지거나 화면이 돌아가면 지금 트리에 맞춰 다시 앉힌다.
        if (scene.size == 0) return
        centerOnStart()
    }

    override fun onDraw(canvas: Canvas) {
        if (renderFailed) return
        try {
            drawScene(canvas)
        } catch (t: Throwable) {
            // 커스텀 뷰는 그리다가 예외가 나면 앱이 그대로 종료된다.
            // 한 번 실패하면 다시 그리지 않고, 화면이 poe2db 트리로 넘어가게 알린다.
            renderFailed = true
            post { onRenderFailed?.invoke(t) }
        }
    }

    private fun drawScene(canvas: Canvas) {
        bgPaint.shader = bgShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = null
        val geo = scene.geometry
        if (geo.size == 0) return
        if (flash > 0f) flash = maxOf(0f, flash - 0.03f)
        val s = viewport.scale
        val left = -viewport.offsetX / s
        val top = -viewport.offsetY / s
        val right = (width - viewport.offsetX) / s
        val bottom = (height - viewport.offsetY) / s

        drawEdges(canvas, geo, s, left, top, right, bottom)
        drawNodes(canvas, geo, s, left, top, right, bottom)
        drawMedallion(canvas, geo, s, left, top, right, bottom)
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
        val stroke = (6f * s).coerceIn(0.7f, 3.2f)
        var plain = 0
        var on = 0
        var i = 0
        while (i + 1 < geo.edges.size) {
            val a = geo.edges[i]
            val b = geo.edges[i + 1]
            i += 2
            if (outside(geo, a, b, left, top, right, bottom)) continue
            val x1 = viewport.screenX(geo.xs[a])
            val y1 = viewport.screenY(geo.ys[a])
            val x2 = viewport.screenX(geo.xs[b])
            val y2 = viewport.screenY(geo.ys[b])
            if (allocated[a] && allocated[b]) {
                if (on + 4 <= edgeOnArray.size) {
                    edgeOnArray[on] = x1
                    edgeOnArray[on + 1] = y1
                    edgeOnArray[on + 2] = x2
                    edgeOnArray[on + 3] = y2
                    on += 4
                }
            } else if (plain + 4 <= lineArray.size) {
                lineArray[plain] = x1
                lineArray[plain + 1] = y1
                lineArray[plain + 2] = x2
                lineArray[plain + 3] = y2
                plain += 4
            }
        }
        if (plain > 0) {
            edgePaint.strokeWidth = stroke
            canvas.drawLines(lineArray, 0, plain, edgePaint)
        }
        // 찍은 노드끼리 이어진 길은 금빛으로 빛나게 (넓고 옅은 선 + 가는 밝은 선)
        if (on > 0) {
            edgeGlowPaint.strokeWidth = stroke * 4.2f
            canvas.drawLines(edgeOnArray, 0, on, edgeGlowPaint)
            edgeOnPaint.strokeWidth = stroke * 1.6f
            canvas.drawLines(edgeOnArray, 0, on, edgeOnPaint)
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
        val sheet = iconSheet
        // 아이콘을 그릴 만큼 확대됐는지 (작을 때는 점으로 그려 빠르게)
        val iconsVisible = s > 0.02f
        var points = 0
        for (i in 0 until geo.size) {
            val x = geo.xs[i]
            val y = geo.ys[i]
            if (x < left - 200 || x > right + 200 || y < top - 200 || y > bottom + 200) continue
            val kind = geo.kindAt(i)
            if (kind == TreeKind.CLASS_START) continue
            val on = allocated[i]
            val sx = viewport.screenX(x)
            val sy = viewport.screenY(y)
            val radius = radiusOf(kind) * s
            val small = kind == TreeKind.NORMAL || kind == TreeKind.ASCENDANT
            if (!on && small && (!iconsVisible || radius < 3.2f)) {
                if (points + 2 <= pointArray.size) {
                    pointArray[points] = sx
                    pointArray[points + 1] = sy
                    points += 2
                }
                continue
            }
            if (!on && small && radius < 6f) {
                if (points + 2 <= pointArray.size) {
                    pointArray[points] = sx
                    pointArray[points + 1] = sy
                    points += 2
                }
                continue
            }
            val icon = scene.icons[i]
            // 찍힌 노드는 뒤에 금빛 번짐을 깐다.
            if (on) {
                glowPaint.color = 0x33E8C060
                canvas.drawCircle(sx, sy, radius * 1.75f, glowPaint)
                glowPaint.color = 0x55E8C060
                canvas.drawCircle(sx, sy, radius * 1.25f, glowPaint)
            }
            nodePaint.color = when {
                on -> 0xFF3A2E18.toInt()
                kind == TreeKind.KEYSTONE -> 0xFF241C12.toInt()
                kind == TreeKind.NOTABLE -> 0xFF1A1D26.toInt()
                else -> 0xFF121219.toInt()
            }
            canvas.drawCircle(sx, sy, radius * 1.06f, nodePaint)
            if (sheet != null && icon != null && radius > 3f) {
                // 큰 아이콘은 원 안에 가둬 네모난 모서리가 드러나지 않게 한다.
                if (radius > 9f) {
                    clipPath.reset()
                    clipPath.addCircle(sx, sy, radius * 1.02f, android.graphics.Path.Direction.CW)
                    canvas.save()
                    canvas.clipPath(clipPath)
                    sheet.draw(canvas, icon, sx, sy, radius * 1.02f, if (on) 255 else 150)
                    canvas.restore()
                } else {
                    sheet.draw(canvas, icon, sx, sy, radius * 1.02f, if (on) 255 else 150)
                }
            } else if (icon == null) {
                nodePaint.color = if (on) gold else 0xFF3C3A34.toInt()
                canvas.drawCircle(sx, sy, radius * 0.42f, nodePaint)
            }
            ringPaint.strokeWidth = (radius * 0.26f).coerceIn(0.8f, 3.4f)
            ringPaint.color = when {
                on -> goldSoft
                kind == TreeKind.KEYSTONE -> 0xFFB4762F.toInt()
                kind == TreeKind.NOTABLE -> 0xFF7C6A44.toInt()
                kind == TreeKind.MASTERY -> 0xFF4E6A8C.toInt()
                kind == TreeKind.JEWEL -> 0xFF8C7A46.toInt()
                kind == TreeKind.ASCENDANT_START -> 0xFF9AA6B8.toInt()
                else -> 0xFF3F3A2E.toInt()
            }
            canvas.drawCircle(sx, sy, radius, ringPaint)
        }
        if (points > 0) {
            pointPaint.strokeWidth = (normalRadius * 0.55f * s).coerceIn(1f, 3.4f)
            canvas.drawPoints(pointArray, 0, points, pointPaint)
        }
    }

    /** 직업 메달 — 가운데 일러스트와 전직 노드 링 */
    private fun drawMedallion(
        canvas: Canvas,
        geo: TreeGeometry,
        s: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val start = scene.classStart
        if (start < 0) return
        val x = geo.xs[start]
        val y = geo.ys[start]
        if (x < left - 4000 || x > right + 4000 || y < top - 4000 || y > bottom + 4000) return
        val sx = viewport.screenX(x)
        val sy = viewport.screenY(y)
        val radius = (900f * s).coerceIn(42f * density, 96f * density)

        // 뒤로 퍼지는 따뜻한 빛
        glowPaint.color = 0x22E8C060
        canvas.drawCircle(sx, sy, radius * 1.7f, glowPaint)
        glowPaint.color = 0x18C69A4A
        canvas.drawCircle(sx, sy, radius * 2.3f, glowPaint)

        // 일러스트를 원 안에 넣는다
        val art = classArtRect
        val sheet = classSheet
        canvas.save()
        clipPath.reset()
        clipPath.addCircle(sx, sy, radius * 0.97f, android.graphics.Path.Direction.CW)
        canvas.clipPath(clipPath)
        medallionPaint.color = 0xFF0D1218.toInt()
        canvas.drawCircle(sx, sy, radius, medallionPaint)
        if (sheet != null && art != null) {
            sheet.draw(canvas, art, sx, sy, radius, 255)
        }
        canvas.restore()

        // 두 겹의 금동 테두리
        medallionRing.strokeWidth = radius * 0.075f
        medallionRing.color = 0xFF9A7F46.toInt()
        canvas.drawCircle(sx, sy, radius, medallionRing)
        medallionRing.strokeWidth = radius * 0.028f
        medallionRing.color = 0xFFD8B76A.toInt()
        canvas.drawCircle(sx, sy, radius * 0.93f, medallionRing)

        // 전직 노드 링 (아이콘은 노드 스프라이트에서 잘라 쓴다 — 직업 일러스트 시트와 자리가 다르다)
        val ring = scene.ascendRing
        if (ring.isEmpty()) return
        val ringSheet = iconSheet
        val ringRadius = radius * 1.32f
        val size = radius * 0.2f
        ring.forEachIndexed { order, index ->
            if (index !in geo.ids.indices) return@forEachIndexed
            val angle = (Math.PI * 2.0 * order / ring.size - Math.PI / 2).toFloat()
            val nx = sx + cos(angle) * ringRadius
            val ny = sy + sin(angle) * ringRadius
            val on = allocated[index]
            if (on) {
                glowPaint.color = 0x44E8C060
                canvas.drawCircle(nx, ny, size * 1.5f, glowPaint)
            }
            nodePaint.color = if (on) 0xFF3A2E18.toInt() else 0xFF15161C.toInt()
            canvas.drawCircle(nx, ny, size, nodePaint)
            val icon = scene.icons[index]
            if (ringSheet != null && icon != null) {
                clipPath.reset()
                clipPath.addCircle(nx, ny, size, android.graphics.Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clipPath)
                ringSheet.draw(canvas, icon, nx, ny, size * 1.0f, if (on) 255 else 205)
                canvas.restore()
            }
            ringPaint.strokeWidth = (size * 0.22f).coerceAtLeast(1f)
            ringPaint.color = if (on) goldSoft else 0xFF8C7340.toInt()
            canvas.drawCircle(nx, ny, size, ringPaint)
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
        val size = 10.5f * density
        if (notableRadius * s < size * 1.2f) return
        labelPaint.textSize = size
        for (i in 0 until geo.size) {
            if (!allocated[i]) continue
            val kind = geo.kindAt(i)
            if (kind != TreeKind.KEYSTONE && kind != TreeKind.NOTABLE) continue
            val x = geo.xs[i]
            val y = geo.ys[i]
            if (x < left || x > right || y < top || y > bottom) continue
            val label = scene.names[i]
            if (label.isBlank()) continue
            canvas.drawText(
                label,
                viewport.screenX(x),
                viewport.screenY(y) - radiusOf(kind) * s - size * 0.35f,
                labelPaint,
            )
        }
        // 검색으로 찾은 노드는 붉은 고리로 표시한다.
        if (highlight >= 0 && highlight < geo.size && flash > 0f) {
            searchPaint.strokeWidth = 2.2f * density
            searchPaint.alpha = (200 * (flash / 1.6f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(
                viewport.screenX(geo.xs[highlight]),
                viewport.screenY(geo.ys[highlight]),
                radiusOf(geo.kindAt(highlight)) * s * 1.8f,
                searchPaint,
            )
        }
    }

    private fun radiusOf(kind: TreeKind): Float = when (kind) {
        TreeKind.NOTABLE -> notableRadius
        TreeKind.KEYSTONE -> keystoneRadius
        TreeKind.MASTERY -> masteryRadius
        TreeKind.JEWEL -> jewelRadius
        TreeKind.ASCENDANT -> ascendRadius
        TreeKind.ASCENDANT_START -> ascendRadius * 1.6f
        TreeKind.CLASS_START -> 0f
        else -> normalRadius
    }

    // ------------------------------------------------------------ 손짓

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
                flinging = false
                velocityX = 0f
                velocityY = 0f
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
                        clamp()
                        velocityX = dx
                        velocityY = dy
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
                } else if (abs(velocityX) + abs(velocityY) > 6f * density) {
                    // 손을 떼도 잠깐 더 흘러간다.
                    fling()
                }
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun fling() {
        flinging = true
        postOnAnimation(flingStep)
    }

    private val flingStep = object : Runnable {
        override fun run() {
            if (!flinging) return
            viewport.pan(velocityX, velocityY)
            velocityX *= 0.90f
            velocityY *= 0.90f
            clamp()
            invalidate()
            if (abs(velocityX) + abs(velocityY) > 0.4f * density) {
                postOnAnimation(this)
            } else {
                flinging = false
            }
        }
    }

    /** 확대·이동이 트리에서 너무 멀어지지 않게 붙잡아 둔다. */
    private fun clamp() {
        if (scene.size == 0 || width == 0) return
        val geo = scene.geometry
        val s = viewport.scale
        val margin = 120f * density
        val spanX = (geo.maxX - geo.minX) * s
        val spanY = (geo.maxY - geo.minY) * s
        val minOffsetX = margin - geo.maxX * s
        val maxOffsetX = width - margin - geo.minX * s
        val minOffsetY = margin - geo.maxY * s
        val maxOffsetY = height - margin - geo.minY * s
        val ox = if (spanX < width - margin * 2) {
            (width - spanX) / 2f - geo.minX * s
        } else {
            viewport.offsetX.coerceIn(minOffsetX, maxOffsetX)
        }
        val oy = if (spanY < height - margin * 2) {
            (height - spanY) / 2f - geo.minY * s
        } else {
            viewport.offsetY.coerceIn(minOffsetY, maxOffsetY)
        }
        viewport.setOffset(ox, oy)
    }

    /** 누른 자리 가까이에 있는 노드를 찾아 고른다. 이어진 자리면 바로 찍는다. */
    private fun pick(sx: Float, sy: Float) {
        val geo = scene.geometry
        if (geo.size == 0) return
        val reach = (26f * density) / viewport.scale
        val index = geo.nearest(viewport.treeX(sx), viewport.treeY(sy), reach)
        if (index < 0) return
        highlight = index
        flash = 1.6f
        val id = geo.ids[index]
        val wasAllocated = allocated[index]
        if (wasAllocated) {
            deallocate(id)
        } else {
            allocate(id)
        }
        onNodeTap?.onTap(id, wasAllocated)
        invalidate()
    }

    private fun notifyPoints() {
        onPointsChanged?.onChanged(points())
    }

    // ------------------------------------------------------------ 포인트 계산

    private fun totalFor(tree: TreeDict, classIndex: Int): Int {
        if (classIndex < 0) return 0
        val level = bindLevel
        return if (level > 1) level - 1 + QUEST_POINTS else 0
    }

    /** 빌드 레벨 (포인트 총량 계산용) */
    var bindLevel: Int = 0

    fun setPointTotal(total: Int) {
        pointTotal = total
        notifyPoints()
    }

    companion object {
        /**
         * 캠페인·퀘스트로 얻는 패시브 포인트.
         * 레벨 90 빌드가 114개를 찍고 있어 25로 잡는다 (레벨-1 + 25).
         */
        const val QUEST_POINTS = 25

        /** 트리 자료에서 빌드의 직업을 찾는다 (PoB에 직업 이름이 없을 때) */
        fun classOf(tree: TreeDict, nodeIds: List<Int>): String {
            val ids = nodeIds.toSet()
            tree.classStarts.entries.firstOrNull { ids.contains(it.value) }?.let { (index, _) ->
                return tree.classes.getOrNull(index)?.nameEn.orEmpty()
            }
            return ""
        }
    }
}

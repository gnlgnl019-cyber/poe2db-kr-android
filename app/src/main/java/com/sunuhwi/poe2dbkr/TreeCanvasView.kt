package com.sunuhwi.poe2dbkr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import kotlin.math.abs

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
    /** 고른 직업의 한국어 이름 (시작 노드 옆에 적는다) */
    val className: String = "",
    /** 고른 전직의 한국어 이름 */
    val ascendName: String = "",
    /** 고른 직업의 번호 (같은 직업인지 가리는 데 쓴다) */
    val classIndex: Int = -1,
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
            val cls = tree.classes.getOrNull(classIndex)
            val classStart = if (classIndex >= 0) geo.indexOf(tree.startNode(classIndex, 0)) else -1
            val ascendStart = if (classIndex >= 0) geo.indexOf(tree.startNode(classIndex, ascendIndex)) else -1
            val className = cls?.nameKo?.ifBlank { cls.nameEn }.orEmpty()
            val ascendName = cls?.ascendancies?.getOrNull(ascendIndex - 1)?.second.orEmpty()
            return TreeScene(
                geometry = geo,
                icons = icons,
                names = names,
                stats = stats,
                ascendancy = ascendancy,
                iconAsset = tree.iconAsset,
                classStart = classStart,
                ascendStart = ascendStart,
                className = className,
                ascendName = if (ascendName == "WIP") "" else ascendName,
                classIndex = classIndex,
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
data class TreePoints(
    val allocated: Int = 0,
    val total: Int = 0,
    val ascendancy: Int = 0,
    val ascendancyTotal: Int = 0,
)

/**
 * 패시브·아틀라스 트리를 앱 안에서 직접 그리는 화면.
 *
 * poe2db 화면을 웹뷰로 띄우지 않는다. assets에 담아 둔 노드 좌표·연결선·아이콘 스프라이트로
 * 노드와 선을 앱이 직접 그린다.
 * - 노드는 실제 트리 좌표 그대로, 종류별 크기의 원 안에 아이콘을 넣는다
 * - 가운데 직업 일러스트·금색 고리·동심원 같은 장식은 두지 않는다 (직업 시작점만 작게 표시)
 * - 두 손가락으로 확대·축소, 한 손가락으로 이동(던지면 관성으로 흘러간다)
 * - 찍은 노드는 금빛으로 빛나고, 이어진 길도 금색으로 밝아진다
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

    /** 찍지 못했을 때 이유를 알린다 (이어지지 않은 노드를 눌렀거나 포인트가 모자랄 때) */
    fun interface OnRefused {
        fun onRefused(result: TreeAllocation.Result)
    }

    /**
     * 이 화면에서 노드를 찍고 뺄 수 있는지.
     *
     * PoB 탭처럼 빌드를 보여 주기만 하는 자리에서는 false로 두어, 눌러도 이름과 설명만 뜨고
     * 찍은 노드가 이 화면과 다른 화면에서 서로 달라지지 않게 한다.
     */
    var editable: Boolean = true

    var onNodeTap: OnNodeTap? = null
    var onPointsChanged: OnPointsChanged? = null
    var onRefused: OnRefused? = null

    /** 한 조각을 그리다 예외가 났을 때 알린다 (트리는 화면에 그대로 둔다) */
    var onDrawProblem: ((Throwable) -> Unit)? = null

    /** 같은 자리에서 되풀이해 실패하면 그 조각은 건너뛴다 (매 프레임 로그·예외가 쌓이지 않게) */
    private val sectionFailures = HashMap<String, Int>()

    private val viewport = TreeViewport()
    /** 시작 노드로 당겨 둔 자리 — 화면 크기가 잡힐 때 이 자리를 다시 가운데로 둔다 */
    private var focusX = Float.NaN
    private var focusY = Float.NaN
    private var scene: TreeScene = TreeScene.EMPTY
    private var tree: TreeDict = TreeDict.EMPTY
    private var alloc = TreeAllocation(TreeGeometry.EMPTY)
    private val allocated: BooleanArray get() = alloc.allocated

    /** 화면에 그릴 것만 골라 둔 계산 (자료가 바뀔 때 새로 만든다) */
    private var cull: TreeCull? = null
    private var iconSheet: SpriteSheet? = null

    private var pointTotal = 0
    private var ascendancyTotal = 8

    private val density = resources.displayMetrics.density

    private val bgPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.bg) }
    private var bgShader: Shader? = null
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6A5533.toInt()
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
    private val classPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFE7B0.toInt()
        isFakeBoldText = true
    }
    private val searchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFF6A4A.toInt()
    }

    private var highlight = -1
    private var flash = 0f
    private val clipPath = android.graphics.Path()

    /** 손짓을 세는 칸 — 두 손가락 확대·축소, 한 손가락 이동, 짧게 누르기 */
    private val gestures = TreeGestures(
        viewport,
        slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        flickSpeed = 6f * density,
    )

    private var velocityX = 0f
    private var velocityY = 0f
    private var flinging = false

    private val gold = 0xFFE8C060.toInt()
    private val goldSoft = 0xFFF0DCA8.toInt()
    private val bronze = 0xFF8C7340.toInt()

    /** 빌드 레벨 (포인트 총량 계산용) */
    var bindLevel: Int = 0

    init {
        isClickable = true
        isFocusable = true
        // 트리를 화면 밖으로 조금은 밀어 볼 수 있게 둔다.
        viewport.overscroll = 120f * density
        gestures.onMoved = {
            flinging = false
            invalidate()
        }
        gestures.onTap = { x, y -> pick(x, y) }
        gestures.onFlick = { dx, dy -> startFling(dx, dy) }
    }

    /**
     * 직업·전직을 바꾼다.
     *
     * 같은 직업에서 전직만 바꾸면 찍은 노드를 그대로 두고, 다른 직업을 고르면
     * 그 직업의 시작 노드 하나만 남기고 비운 뒤 시작점으로 화면을 옮긴다.
     * (다른 직업의 시작점과 이어지지 않는 노드를 들고 가면 찍을 수도 되돌릴 수도 없다.)
     */
    fun setClass(tree: TreeDict, classIndex: Int, ascendIndex: Int) {
        if (tree.isEmpty()) return
        this.tree = tree
        val sameClass = scene.size > 0 && scene.classIndex == classIndex
        val previous = if (sameClass) {
            // 다른 직업의 시작점은 새 직업 트리에서 뜻이 없으므로 들고 가지 않는다.
            alloc.allocatedIds().filter { id -> tree.node(id)?.kind != 'c' }
        } else {
            emptyList()
        }
        scene = TreeScene.of(tree, classIndex, ascendIndex)
        if (scene.size == 0) {
            clear()
            return
        }
        prepare()
        iconSheet = SpriteSheet.of(context, scene.iconAsset)
        pointTotal = totalFor(classIndex)
        bindAllocation(previous)
        alloc.markBound()
        focusStart()
        notifyPoints()
        invalidate()
    }

    /** PoB 빌드의 직업·전직·찍은 노드로 트리를 채운다. */
    fun bind(tree: TreeDict, nodeIds: List<Int>, masteryIds: List<Int> = emptyList()) {
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
        prepare()
        iconSheet = SpriteSheet.of(context, scene.iconAsset)
        pointTotal = totalFor(classIndex)
        bindAllocation(nodeIds + masteryIds)
        alloc.markBound()
        centerOnStart()
        notifyPoints()
        invalidate()
    }

    /**
     * 불러온 빌드 없이 트리만 펼친다 (직업 시작점에서 둘러보고 직접 찍어 보는 화면).
     * 포인트 제한 없이 찍을 수 있다.
     */
    fun bindDefault(tree: TreeDict, classIndex: Int, ascendIndex: Int) {
        this.tree = tree
        scene = TreeScene.of(tree, classIndex, ascendIndex)
        if (scene.size == 0) {
            clear()
            return
        }
        prepare()
        iconSheet = SpriteSheet.of(context, scene.iconAsset)
        pointTotal = 0
        bindAllocation(emptyList())
        alloc.markBound()
        focusStart()
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
        prepare()
        iconSheet = SpriteSheet.of(context, scene.iconAsset)
        pointTotal = atlas.totalPoints
        bindAllocation(nodeIds)
        alloc.markBound()
        refreshZoomRange()
        viewport.fit(width, height, scene.geometry.minX, scene.geometry.minY, scene.geometry.maxX, scene.geometry.maxY, 18f * density)
        invalidate()
        notifyPoints()
    }

    /**
     * 새 자료로 그릴 준비를 한다.
     *
     * 노드 배열(아이콘·이름·설명·전직 표시)과 찍은 노드 배열의 길이를 기하 배열에 맞춰 둔다.
     * 어긋난 채로 그리면 배열 범위를 넘어 IndexOutOfBoundsException이 나 트리가 멈추므로,
     * 여기서 길이를 확정하고 그리기 계산([TreeCull])을 새로 만든다.
     */
    private fun prepare() {
        sectionFailures.clear()
        cull = null
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
        cull = TreeCull(scene.geometry, alloc.allocated, scene.icons, scene.ascendancy)
    }

    fun clear() {
        scene = TreeScene.EMPTY
        alloc = TreeAllocation(TreeGeometry.EMPTY)
        iconSheet = null
        cull = null
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
        refreshZoomRange()
        val targetScale = if (zoomIn) maxOf(viewport.scale, viewport.fitScale * 4f) else viewport.scale
        viewport.setScale(targetScale)
        viewport.center(scene.geometry.xs[index], scene.geometry.ys[index])
        invalidate()
        return true
    }

    fun zoomIn() = zoom(1.6f)

    fun zoomOut() = zoom(1 / 1.6f)

    private fun zoom(factor: Float) {
        viewport.zoomAround(factor, width / 2f, height / 2f)
        invalidate()
    }

    /** 찍은 노드가 모인 자리를 화면에 꽉 채운다. 찍은 노드가 없으면 직업 시작점 둘레. */
    fun reset() {
        if (scene.size == 0) return
        fitAllocated()
        invalidate()
    }

    /** 트리 전체를 본다. */
    fun showWholeTree() {
        if (scene.size == 0) return
        val geo = scene.geometry
        refreshZoomRange()
        viewport.fit(width, height, geo.minX, geo.minY, geo.maxX, geo.maxY, 14f * density)
        invalidate()
    }

    /**
     * 확대·축소 한도를 화면 크기와 트리 자리에서 다시 잡는다.
     *
     * 제일 작게 줄여도 트리 전체가 화면에 들어오는 배율에서 멈추고,
     * 제일 크게 키우면 보통 노드의 반지름이 26dp쯤 된다 (그 위로는 노드가 손끝만 해진다).
     */
    private fun refreshZoomRange() {
        if (scene.size == 0 || width <= 0 || height <= 0) return
        val geo = scene.geometry
        viewport.setBounds(geo.minX, geo.minY, geo.maxX, geo.maxY)
        val whole = viewport.wholeScale(14f * density)
        val biggest = maxOf(26f * density / TreeMetrics.NORMAL, whole * 2f)
        viewport.setZoomRange(whole * 0.92f, biggest)
    }

    /**
     * 시작 노드와 찍은 노드를 함께 담는 자리로 화면을 맞춘다.
     * 찍은 것이 없으면 시작 노드 둘레 6천 거리를 보여 준다 (노드가 알아볼 만한 크기).
     */
    private fun centerOnStart() {
        if (scene.size == 0) return
        fitAllocated()
        invalidate()
    }

    /** 직업 시작 노드로 화면을 옮긴다. 그 자리에서 이어진 노드를 바로 찍을 수 있는 배율로 당긴다. */
    fun focusStart() {
        if (scene.size == 0) return
        val geo = scene.geometry
        val start = scene.classStart
        if (start < 0) {
            centerOnStart()
            return
        }
        // 테두리 고리 반지름이 13dp쯤 되게 당긴다 — 한 화면에 노드 여섯 줄이 보인다.
        refreshZoomRange()
        viewport.setScale(13f * density / TreeMetrics.NORMAL)
        focusX = geo.xs[start]
        focusY = geo.ys[start]
        viewport.center(focusX, focusY)
        highlight = start
        flash = 1.6f
        invalidate()
    }

    private fun fitAllocated() {
        // 찍은 노드 전체를 보는 자리로 옮기면 시작점 고정은 푼다.
        focusX = Float.NaN
        focusY = Float.NaN
        val geo = scene.geometry
        val start = scene.classStart
        var minX = if (start >= 0) geo.xs[start] else Float.MAX_VALUE
        var minY = if (start >= 0) geo.ys[start] else Float.MAX_VALUE
        var maxX = if (start >= 0) geo.xs[start] else -Float.MAX_VALUE
        var maxY = if (start >= 0) geo.ys[start] else -Float.MAX_VALUE
        var any = start >= 0
        for (i in allocated.indices) {
            if (!allocated[i]) continue
            // 전직 노드는 트리 바깥 동그라미에 따로 모여 있어 화면 맞춤에서 뺀다.
            // (넣으면 본 트리가 작아져 노드가 안 보인다. 끌어서 보면 된다.)
            if (i == scene.ascendStart || scene.ascendancy.getOrElse(i) { false }) continue
            minX = minOf(minX, geo.xs[i])
            minY = minOf(minY, geo.ys[i])
            maxX = maxOf(maxX, geo.xs[i])
            maxY = maxOf(maxY, geo.ys[i])
            any = true
        }
        if (!any) {
            // 찍은 노드도 시작점도 없는 아틀라스 — 트리 전체를 본다.
            refreshZoomRange()
            viewport.fit(width, height, geo.minX, geo.minY, geo.maxX, geo.maxY, 16f * density)
            return
        }
        // 너무 가까이 붙으면 노드 하나만 크게 보이므로 최소 둘레를 둔다.
        val spanX = (maxX - minX).coerceAtLeast(MIN_SPAN)
        val spanY = (maxY - minY).coerceAtLeast(MIN_SPAN)
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val padX = spanX * 0.10f + 180f
        val padY = spanY * 0.10f + 180f
        refreshZoomRange()
        viewport.fit(
            width,
            height,
            cx - spanX / 2f - padX,
            cy - spanY / 2f - padY,
            cx + spanX / 2f + padX,
            cy + spanY / 2f + padY,
            10f * density,
        )
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
        // 직업을 골라 시작 노드로 당겨 둔 자리는 그 자리를 계속 가운데에 둔다.
        viewport.resize(w, h)
        refreshZoomRange()
        if (focusX.isFinite() && focusY.isFinite() && (oldw == 0 || oldh == 0)) {
            viewport.center(focusX, focusY)
            invalidate()
            return
        }
        centerOnStart()
    }

    override fun onDraw(canvas: Canvas) {
        // 한 조각이 실패해도 나머지는 그린다.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint.apply { shader = bgShader })
        bgPaint.shader = null
        val geo = scene.geometry
        if (geo.size == 0) return
        if (flash > 0f) flash = maxOf(0f, flash - 0.03f)
        val s = viewport.scale

        val plan = cull ?: TreeCull(geo, allocated, scene.icons, scene.ascendancy).also { cull = it }
        section("cull") { plan.compute(s, viewport.offsetX, viewport.offsetY, width, height) }
        section("edges") { drawEdges(canvas, plan, s) }
        section("nodes") { drawNodes(canvas, plan, geo, s) }
        section("labels") { drawLabels(canvas, geo, s) }
    }

    /** 그리기 한 조각을 막아 둔다. 같은 조각이 되풀이해 실패하면 그 조각은 건너뛴다. */
    private inline fun section(name: String, block: () -> Unit) {
        val failed = sectionFailures[name] ?: 0
        if (failed >= 6) return
        try {
            block()
            if (failed > 0) sectionFailures.remove(name)
        } catch (t: Throwable) {
            sectionFailures[name] = failed + 1
            Log.e(LOG_TAG, "트리 '$name' 그리기를 건너뛰었습니다 (${failed + 1}번째)", t)
            if (failed == 0) {
                post { onDrawProblem?.invoke(t) }
            }
        }
    }

    private fun drawEdges(canvas: Canvas, plan: TreeCull, s: Float) {
        val stroke = (4.2f * s * density).coerceIn(0.55f * density, 2.2f * density)
        if (plan.plainCount > 0) {
            edgePaint.strokeWidth = stroke
            canvas.drawLines(plan.plainEdges, 0, plan.plainCount, edgePaint)
        }
        // 찍은 노드끼리 이어진 길은 금빛으로 빛나게 (넓고 옅은 선 + 가는 밝은 선)
        if (plan.onCount > 0) {
            edgeGlowPaint.strokeWidth = stroke * 4.0f
            canvas.drawLines(plan.onEdges, 0, plan.onCount, edgeGlowPaint)
            edgeOnPaint.strokeWidth = stroke * 1.5f
            canvas.drawLines(plan.onEdges, 0, plan.onCount, edgeOnPaint)
        }
    }

    private fun drawNodes(canvas: Canvas, plan: TreeCull, geo: TreeGeometry, s: Float) {
        val dotScale = TreeMetrics.isDotScale(s)
        // 작은 노드를 먼저, 큰 노드를 나중에 그려 겹칠 때 주요 노드가 위로 오게 한다.
        for (pass in 0..1) {
            for (order in 0 until plan.nodeCount) {
                val i = plan.nodes[order]
                if (!plan.has(i)) continue
                val kind = plan.kindOf(i)
                val big = kind != TreeKind.NORMAL && kind != TreeKind.ASCENDANT
                if (big != (pass == 1)) continue
                val on = plan.isAllocated(i)
                val sx = viewport.screenX(geo.xs[i])
                val sy = viewport.screenY(geo.ys[i])
                if (!sx.isFinite() || !sy.isFinite()) continue
                if (dotScale) {
                    drawDot(canvas, kind, on, sx, sy)
                    continue
                }
                val radius = TreeMetrics.radiusOf(kind) * s
                if (!radius.isFinite() || radius <= 0.35f) continue
                drawNode(canvas, plan, i, kind, on, sx, sy, radius)
            }
        }
    }

    private fun drawDot(canvas: Canvas, kind: TreeKind, on: Boolean, sx: Float, sy: Float) {
        val r = TreeMetrics.dotRadius(kind, density)
        if (on) {
            glowPaint.color = 0x55E8C060
            canvas.drawCircle(sx, sy, r * 2.0f, glowPaint)
        }
        nodePaint.color = when {
            on -> gold
            kind == TreeKind.KEYSTONE -> 0xFFB4762F.toInt()
            kind == TreeKind.NOTABLE -> 0xFF9A8455.toInt()
            kind == TreeKind.CLASS_START -> 0xFFFFE7B0.toInt()
            kind == TreeKind.MASTERY -> 0xFF5B7BA0.toInt()
            else -> 0xFF4A4336.toInt()
        }
        canvas.drawCircle(sx, sy, r, nodePaint)
    }

    /**
     * 노드 하나 — poe2db 화면과 같은 구성이다.
     *
     * 안쪽에는 아이콘만큼 작은 원을 깔고 그 안에 아이콘을 가뒤 넣는다.
     * 테두리 고리는 아이콘 바깥에 가는 선으로 둘러, 확대해도 이웃 노드를 가리지 않는다.
     */
    private fun drawNode(
        canvas: Canvas,
        plan: TreeCull,
        index: Int,
        kind: TreeKind,
        on: Boolean,
        sx: Float,
        sy: Float,
        radius: Float,
    ) {
        val icon = plan.iconOf(index)
        val iconR = TreeMetrics.iconRadiusOf(kind) * viewport.scale
        // 찍힌 노드는 뒤에 금빛 번짐을 깔아 둔다 (고리 바깥으로 살짝만).
        if (on) {
            glowPaint.color = 0x33E8C060
            canvas.drawCircle(sx, sy, radius * 1.30f, glowPaint)
            glowPaint.color = 0x55E8C060
            canvas.drawCircle(sx, sy, radius * 1.05f, glowPaint)
        }
        // 아이콘 뒤의 어두운 바탕 — 아이콘 크기에만 깐다.
        nodePaint.color = when {
            on -> 0xFF3A2E18.toInt()
            kind == TreeKind.KEYSTONE -> 0xFF241C12.toInt()
            kind == TreeKind.NOTABLE -> 0xFF1A1D26.toInt()
            kind == TreeKind.CLASS_START -> 0xFF221A10.toInt()
            else -> 0xFF0E1118.toInt()
        }
        val plate = maxOf(iconR * 1.04f, radius * 0.30f)
        canvas.drawCircle(sx, sy, plate, nodePaint)
        val showIcon = icon != null && TreeMetrics.showsIcon(kind, viewport.scale, density)
        if (showIcon) {
            val sheet = iconSheet
            if (sheet != null) {
                // 아이콘은 늘 원 안에 가둔다 (네모난 모서리가 드러나면 트리가 지저분해진다).
                clipPath.reset()
                clipPath.addCircle(sx, sy, iconR, android.graphics.Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clipPath)
                sheet.draw(canvas, icon!!, sx, sy, iconR, if (on) 255 else 190)
                canvas.restore()
            }
        } else {
            nodePaint.color = if (on) gold else 0xFF3C3A34.toInt()
            canvas.drawCircle(sx, sy, maxOf(radius * 0.16f, 1f * density), nodePaint)
        }
        // 테두리 고리 — 가는 선이라도 이웃 노드 위에 얹혀도 가리지 않는다.
        ringPaint.strokeWidth = (radius * 0.10f).coerceIn(0.7f * density, 2.4f * density)
        ringPaint.color = when {
            on -> goldSoft
            kind == TreeKind.CLASS_START -> 0xFFE0BE78.toInt()
            kind == TreeKind.KEYSTONE -> 0xFFB4762F.toInt()
            kind == TreeKind.NOTABLE -> 0xFF8A7549.toInt()
            kind == TreeKind.JEWEL -> 0xFF8C7A46.toInt()
            plan.isAscendancy(index) || kind == TreeKind.ASCENDANT_START -> 0xFF7E97B8.toInt()
            else -> 0xFF453E30.toInt()
        }
        canvas.drawCircle(sx, sy, radius, ringPaint)
        if (kind == TreeKind.KEYSTONE || kind == TreeKind.NOTABLE) {
            // 주요 노드는 아이콘과 고리 사이에 얇은 고리를 하나 더 두어 DB의 두겁 테두리처럼 보이게 한다.
            ringPaint.strokeWidth = (radius * 0.05f).coerceIn(0.5f * density, 1.4f * density)
            ringPaint.color = if (on) 0x88FFE7B0.toInt() else 0x553E3728
            canvas.drawCircle(sx, sy, radius * 0.80f, ringPaint)
        }
    }

    private fun drawLabels(canvas: Canvas, geo: TreeGeometry, s: Float) {
        // 직업 시작점 이름 — 트리 가운데 여섯 자리를 알아볼 수 있게 적는다.
        if (s >= 0.04f) {
            classPaint.textSize = 11f * density
            for (i in 0 until geo.size) {
                if (geo.kindAt(i) != TreeKind.CLASS_START) continue
                val x = viewport.screenX(geo.xs[i])
                val y = viewport.screenY(geo.ys[i])
                if (!x.isFinite() || !y.isFinite()) continue
                if (x < -100f || x > width + 100f || y < -50f || y > height + 50f) continue
                val sceneName = scene.names.getOrNull(i).orEmpty()
                val label = if (i == scene.classStart && scene.className.isNotBlank()) scene.className else sceneName
                if (label.isBlank()) continue
                canvas.drawText(label, x, y - TreeMetrics.CLASS_START * s - 6f * density, classPaint)
            }
        }
        val size = 10.5f * density
        if (TreeMetrics.NOTABLE * s < size * 1.2f) return
        labelPaint.textSize = size
        val left = -viewport.offsetX / s
        val top = -viewport.offsetY / s
        val right = (width - viewport.offsetX) / s
        val bottom = (height - viewport.offsetY) / s
        for (i in 0 until geo.size) {
            if (!allocated.getOrElse(i) { false }) continue
            val kind = geo.kindAt(i)
            if (kind != TreeKind.KEYSTONE && kind != TreeKind.NOTABLE) continue
            val x = geo.xs[i]
            val y = geo.ys[i]
            if (x < left || x > right || y < top || y > bottom) continue
            val label = scene.names.getOrNull(i).orEmpty()
            if (label.isBlank()) continue
            canvas.drawText(
                label,
                viewport.screenX(x),
                viewport.screenY(y) - TreeMetrics.radiusOf(kind) * s - size * 0.35f,
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
                TreeMetrics.radiusOf(geo.kindAt(highlight)) * s * 1.45f,
                searchPaint,
            )
        }
    }

    // ------------------------------------------------------------ 손짓

    /**
     * 손가락 하나하나를 [TreeGestures]에 그대로 넘긴다.
     *
     * 두 손가락이 닿는 순간부터는 화면을 감싼 쪽(스크롤·탭 넘김)이 손짓을 가져가지 않게 막는다.
     * 이 신호가 없으면 핀치를 시작할 때 부모가 먼저 반응해 확대가 아예 먹지 않는다.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                gestures.down(event.x, event.y, event.eventTime)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                gestures.addPointer(
                    event.getPointerId(event.actionIndex),
                    event.getX(event.actionIndex),
                    event.getY(event.actionIndex),
                )
            }
            MotionEvent.ACTION_MOVE -> gestures.move(pointersOf(event))
            MotionEvent.ACTION_POINTER_UP -> gestures.liftPointer(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_UP -> {
                gestures.up(event.eventTime)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                gestures.cancel()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    /** 지금 닿아 있는 손가락의 자리를 모은다. */
    private fun pointersOf(event: MotionEvent): List<TreePointer> {
        val out = ArrayList<TreePointer>(event.pointerCount)
        for (i in 0 until event.pointerCount) {
            out.add(TreePointer(event.getPointerId(i), event.getX(i), event.getY(i)))
        }
        return out
    }

    /** 손을 뗀 뒤에도 그 방향으로 잠깐 더 흘러가게 한다. */
    private fun startFling(dx: Float, dy: Float) {
        velocityX = dx
        velocityY = dy
        flinging = true
        postOnAnimation(flingStep)
    }

    private val flingStep = object : Runnable {
        override fun run() {
            if (!flinging) return
            viewport.pan(velocityX, velocityY)
            velocityX *= 0.90f
            velocityY *= 0.90f
            invalidate()
            if (abs(velocityX) + abs(velocityY) > 0.4f * density) {
                postOnAnimation(this)
            } else {
                flinging = false
            }
        }
    }

    /** 누른 자리 가까이에 있는 노드를 찾아 고른다. 이어진 자리면 바로 찍는다. */
    private fun pick(sx: Float, sy: Float) {
        val geo = scene.geometry
        if (geo.size == 0) return
        val reach = maxOf(26f * density, TreeMetrics.NORMAL * viewport.scale * 0.75f) / viewport.scale
        // 숙련 노드는 주요 노드 위에 겹쳐 있으므로 누를 때는 건너뛴다.
        val index = geo.nearest(viewport.treeX(sx), viewport.treeY(sy), reach) {
            geo.kindAt(it) != TreeKind.MASTERY
        }
        if (index < 0) return
        highlight = index
        flash = 1.6f
        val id = geo.ids[index]
        val wasAllocated = allocated[index]
        if (!editable) {
            onNodeTap?.onTap(id, wasAllocated)
            invalidate()
            return
        }
        val result = if (wasAllocated) deallocate(id) else allocate(id)
        if (result != TreeAllocation.Result.DONE) onRefused?.onRefused(result)
        onNodeTap?.onTap(id, wasAllocated)
        invalidate()
    }

    private fun notifyPoints() {
        onPointsChanged?.onChanged(points())
    }

    // ------------------------------------------------------------ 포인트 계산

    private fun totalFor(classIndex: Int): Int {
        if (classIndex < 0) return 0
        val level = bindLevel
        return if (level > 1) level - 1 + QUEST_POINTS else 0
    }

    fun setPointTotal(total: Int) {
        pointTotal = total
        notifyPoints()
    }

    companion object {
        private const val LOG_TAG = "TreeCanvas"

        /** 찍은 것이 없을 때 보여 줄 최소 둘레 (트리 좌표) */
        private const val MIN_SPAN = 6000f

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

package com.sunuhwi.poe2dbkr

/**
 * 트리 노드의 그리기 크기. 화면 배율과 곱해 반지름을 낸다.
 * (그리기 코드와 시험이 같은 값을 보도록 한곳에 모아 둔다.)
 */
object TreeMetrics {
    const val NORMAL = 26f
    const val NOTABLE = 44f
    const val KEYSTONE = 60f
    const val MASTERY = 40f
    const val JEWEL = 34f
    const val ASCENDANT = 32f

    fun radiusOf(kind: TreeKind): Float = when (kind) {
        TreeKind.NOTABLE -> NOTABLE
        TreeKind.KEYSTONE -> KEYSTONE
        TreeKind.MASTERY -> MASTERY
        TreeKind.JEWEL -> JEWEL
        TreeKind.ASCENDANT -> ASCENDANT
        TreeKind.ASCENDANT_START -> ASCENDANT * 1.6f
        TreeKind.CLASS_START -> 0f
        else -> NORMAL
    }

    /** 아이콘 대신 점으로 그리는 노드인지 (작게 보일 때) */
    fun isDot(kind: TreeKind, allocated: Boolean, scale: Float): Boolean {
        if (allocated) return false
        val small = kind == TreeKind.NORMAL || kind == TreeKind.ASCENDANT
        if (!small) return false
        val radius = radiusOf(kind) * scale
        return scale <= 0.02f || radius < 3.2f || radius < 6f
    }
}

/**
 * 지금 화면에 보이는 노드·연결선을 골라 두는 계산.
 *
 * 그리기(Canvas)와 떼어 두어, 화면이 없는 JVM 시험에서도 배율·위치를 바꿔 가며
 * 같은 계산을 그대로 돌려 볼 수 있게 한다.
 *
 * 자료(기하 배열)와 찍은 노드 배열의 길이가 어긋나 있어도 예외가 나지 않도록
 * 모든 배열 읽기를 범위 안으로 막아 둔다 — 트리를 그리다 앱이 멈추던 자리다.
 */
private const val LONG_EDGE = 2500f

class TreeCull(
    geometry: TreeGeometry,
    private val allocated: BooleanArray,
    private val icons: Array<IntArray?>,
    private val ascendancy: BooleanArray = BooleanArray(0),
) {
    val geometry: TreeGeometry = geometry
    private val size = geometry.size

    /** 화면에 그릴 연결선 [x1, y1, x2, y2, …] */
    val plainEdges: FloatArray = FloatArray(maxOf(0, geometry.edges.size) * 2 + 8)
    var plainCount = 0
        private set

    /** 찍은 노드끼리 이어진 연결선 (금빛으로 밝게 그린다) */
    val onEdges: FloatArray = FloatArray(maxOf(0, geometry.edges.size) * 2 + 8)
    var onCount = 0
        private set

    /** 점으로 그릴 노드 [x, y, …] */
    val dots: FloatArray = FloatArray(size * 2 + 8)
    var dotCount = 0
        private set

    /** 원·아이콘으로 그릴 노드의 배열 위치 */
    val nodes: IntArray = IntArray(size)
    var nodeCount = 0
        private set

    /** 이 배열 위치가 지금 그려도 되는지 */
    fun has(index: Int): Boolean = index in 0 until size

    fun isAllocated(index: Int): Boolean = allocated.getOrElse(index) { false }

    fun iconOf(index: Int): IntArray? = icons.getOrNull(index)

    fun isAscendancy(index: Int): Boolean = ascendancy.getOrElse(index) { false }

    /**
     * 가운데 직업 메달로 이어지는 선인지.
     *
     * 직업 시작점과 전직 시작점은 메달·전직 링으로 따로 그린다. 이 노드들에서 뻗는 선은
     * 화면을 가로지르는 긴 금빛 줄이 되어 트리를 어지럽히므로(사용자 지적) 긋지 않는다.
     */
    fun medallionEdge(index: Int): Boolean =
        kindOf(index) == TreeKind.CLASS_START || kindOf(index) == TreeKind.ASCENDANT_START

    /**
     * 트리 반대편까지 가로지르는 긴 연결선인지.
     * 전직 통로처럼 멀리 떨어진 노드를 잇는 선은 화면을 가로질러 지저분해진다.
     */
    fun isLongEdge(a: Int, b: Int): Boolean {
        if (a !in 0 until size || b !in 0 until size) return true
        val dx = geometry.xs[a] - geometry.xs[b]
        val dy = geometry.ys[a] - geometry.ys[b]
        return dx * dx + dy * dy > LONG_EDGE * LONG_EDGE
    }

    fun kindOf(index: Int): TreeKind = if (index in 0 until size) geometry.kindAt(index) else TreeKind.NORMAL

    /**
     * 화면 배율·이동값으로 보이는 것만 골라 담는다.
     * [width]·[height]가 0이면 아무것도 담지 않는다 (화면 회전·분할 화면 순간).
     */
    fun compute(scale: Float, offsetX: Float, offsetY: Float, width: Int, height: Int) {
        plainCount = 0
        onCount = 0
        dotCount = 0
        nodeCount = 0
        if (size == 0 || width <= 0 || height <= 0) return
        if (!scale.isFinite() || scale <= 0f) return
        if (!offsetX.isFinite() || !offsetY.isFinite()) return

        val left = -offsetX / scale
        val top = -offsetY / scale
        val right = (width - offsetX) / scale
        val bottom = (height - offsetY) / scale

        // 연결선
        var i = 0
        while (i + 1 < geometry.edges.size) {
            val a = geometry.edges[i]
            val b = geometry.edges[i + 1]
            i += 2
            if (a !in 0 until size || b !in 0 until size) continue
            // 메달·전직 링으로 이어지는 선과 화면을 가로지르는 긴 선은 긋지 않는다.
            if (medallionEdge(a) || medallionEdge(b)) continue
            if (isLongEdge(a, b)) continue
            val ax = geometry.xs[a]
            val ay = geometry.ys[a]
            val bx = geometry.xs[b]
            val by = geometry.ys[b]
            if (maxOf(ax, bx) < left || minOf(ax, bx) > right) continue
            if (maxOf(ay, by) < top || minOf(ay, by) > bottom) continue
            val x1 = offsetX + ax * scale
            val y1 = offsetY + ay * scale
            val x2 = offsetX + bx * scale
            val y2 = offsetY + by * scale
            if (isAllocated(a) && isAllocated(b)) {
                if (onCount + 4 <= onEdges.size) {
                    onEdges[onCount] = x1
                    onEdges[onCount + 1] = y1
                    onEdges[onCount + 2] = x2
                    onEdges[onCount + 3] = y2
                    onCount += 4
                }
            } else if (plainCount + 4 <= plainEdges.size) {
                plainEdges[plainCount] = x1
                plainEdges[plainCount + 1] = y1
                plainEdges[plainCount + 2] = x2
                plainEdges[plainCount + 3] = y2
                plainCount += 4
            }
        }

        // 노드
        val pad = 200f / scale
        for (index in 0 until size) {
            val x = geometry.xs[index]
            val y = geometry.ys[index]
            if (x < left - pad || x > right + pad || y < top - pad || y > bottom + pad) continue
            val kind = geometry.kindAt(index)
            if (kind == TreeKind.CLASS_START) continue
            val on = isAllocated(index)
            val sx = offsetX + x * scale
            val sy = offsetY + y * scale
            if (TreeMetrics.isDot(kind, on, scale)) {
                if (dotCount + 2 <= dots.size) {
                    dots[dotCount] = sx
                    dots[dotCount + 1] = sy
                    dotCount += 2
                }
                continue
            }
            if (nodeCount < nodes.size) {
                nodes[nodeCount] = index
                nodeCount++
            }
        }
    }
}

/**
 * 전직 노드를 직업 메달 둘레에 두는 자리.
 * 링 배열의 값이 어긋나 있어도(없는 노드 번호) 자리만 건너뛰고 예외를 내지 않는다.
 */
object TreeRing {
    /** 링에 올릴 수 있는 최대 노드 수 (화면이 지저분해지지 않게) */
    const val MAX = 40

    fun placement(
        ring: IntArray,
        size: Int,
        centerX: Float,
        centerY: Float,
        radius: Float,
        outX: FloatArray,
        outY: FloatArray,
        outIndex: IntArray,
    ): Int {
        if (ring.isEmpty()) return 0
        var count = 0
        val step = Math.PI * 2.0 / ring.size
        for (order in ring.indices) {
            if (count >= outX.size || count >= outY.size || count >= outIndex.size) break
            if (count >= MAX) break
            val index = ring[order]
            if (index !in 0 until size) continue
            val angle = (step * order - Math.PI / 2).toFloat()
            outX[count] = centerX + kotlin.math.cos(angle) * radius
            outY[count] = centerY + kotlin.math.sin(angle) * radius
            outIndex[count] = index
            count++
        }
        return count
    }
}

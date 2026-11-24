package com.sunuhwi.poe2dbkr

import kotlin.math.hypot

/**
 * 트리 노드의 그리기 크기. poe2db 자료의 좌표 단위(노드 사이가 평균 200쯤)에 맞춘 반지름이다.
 * (그리기 코드와 시험이 같은 값을 보도록 한곳에 모아 둔다.)
 */
object TreeMetrics {
    const val NORMAL = 108f
    const val NOTABLE = 158f
    const val KEYSTONE = 196f
    const val MASTERY = 138f
    const val JEWEL = 124f
    const val ASCENDANT = 120f
    const val ASCENDANT_START = 158f
    const val CLASS_START = 168f

    fun radiusOf(kind: TreeKind): Float = when (kind) {
        TreeKind.NOTABLE -> NOTABLE
        TreeKind.KEYSTONE -> KEYSTONE
        TreeKind.MASTERY -> MASTERY
        TreeKind.JEWEL -> JEWEL
        TreeKind.ASCENDANT -> ASCENDANT
        TreeKind.ASCENDANT_START -> ASCENDANT_START
        TreeKind.CLASS_START -> CLASS_START
        else -> NORMAL
    }

    /** 이 배율에서 아이콘을 넣을 만큼 노드가 큰지 (작으면 속을 비운 원으로만 그린다) */
    fun showsIcon(kind: TreeKind, scale: Float, dp: Float): Boolean =
        radiusOf(kind) * scale >= 2.4f * dp

    /**
     * 화면에서 점으로만 그리는 배율인지.
     * 트리 전체를 한눈에 볼 때는 노드 5천 개가 서로 겹쳐 아이콘을 넣어도 알아볼 수 없으므로
     * 종류별 크기만 다른 점으로 그린다.
     */
    fun isDotScale(scale: Float): Boolean = scale < 0.055f

    /** 점으로 그릴 때의 반지름(px). 종류가 클수록 조금 크게 찍어 별자리처럼 보이게 한다. */
    fun dotRadius(kind: TreeKind, dp: Float): Float = when (kind) {
        TreeKind.KEYSTONE -> 1.9f * dp
        TreeKind.NOTABLE -> 1.5f * dp
        TreeKind.ASCENDANT_START -> 1.5f * dp
        TreeKind.MASTERY -> 1.2f * dp
        TreeKind.JEWEL -> 1.2f * dp
        TreeKind.ASCENDANT -> 1.1f * dp
        TreeKind.CLASS_START -> 2.2f * dp
        else -> 0.85f * dp
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

    /** 원·아이콘으로 그릴 노드의 배열 위치 */
    val nodes: IntArray = IntArray(size)
    var nodeCount = 0
        private set

    /** 이 배열 위치가 지금 그려도 되는지 */
    fun has(index: Int): Boolean = index in 0 until size

    fun isAllocated(index: Int): Boolean = allocated.getOrElse(index) { false }

    fun iconOf(index: Int): IntArray? = icons.getOrNull(index)

    fun isAscendancy(index: Int): Boolean = ascendancy.getOrElse(index) { false }

    fun kindOf(index: Int): TreeKind = if (index in 0 until size) geometry.kindAt(index) else TreeKind.NORMAL

    /**
     * 트리 반대편까지 가로지르는 긴 연결선인지.
     *
     * 직업 시작점에서 전직 시작점으로 뻗는 통로(1만 5천~2만 거리)는 화면을 가로질러
     * 트리를 어지럽히므로 긋지 않는다. 전직 덩어리 안쪽의 짧은 연결선은 그대로 그린다.
     */
    fun isLongEdge(a: Int, b: Int): Boolean {
        if (a !in 0 until size || b !in 0 until size) return true
        val dx = geometry.xs[a] - geometry.xs[b]
        val dy = geometry.ys[a] - geometry.ys[b]
        return dx * dx + dy * dy > LONG_EDGE * LONG_EDGE
    }

    /**
     * 화면 배율·이동값으로 보이는 것만 골라 담는다.
     * [width]·[height]가 0이면 아무것도 담지 않는다 (화면 회전·분할 화면 순간).
     */
    fun compute(scale: Float, offsetX: Float, offsetY: Float, width: Int, height: Int) {
        plainCount = 0
        onCount = 0
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

        // 노드 — 종류별 반지름만큼 화면 밖으로 걸친 것도 담는다.
        for (index in 0 until size) {
            val x = geometry.xs[index]
            val y = geometry.ys[index]
            val pad = TreeMetrics.radiusOf(geometry.kindAt(index)) * 1.4f + 40f
            if (x * scale + offsetX < -pad || x * scale + offsetX > width + pad) continue
            if (y * scale + offsetY < -pad || y * scale + offsetY > height + pad) continue
            if (nodeCount < nodes.size) {
                nodes[nodeCount] = index
                nodeCount++
            }
        }
    }
}

/** 두 점 사이 거리 (시험·검증용) */
internal fun treeDistance(a: TreeNode, b: TreeNode): Float = hypot(a.x - b.x, a.y - b.y)

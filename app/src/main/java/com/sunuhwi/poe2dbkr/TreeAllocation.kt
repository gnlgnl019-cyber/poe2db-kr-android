package com.sunuhwi.poe2dbkr

/**
 * 트리에 노드를 찍고 되돌리는 규칙.
 *
 * 그리기(Canvas)와 떼어 두어 JVM 테스트에서 그대로 돌려 볼 수 있게 한다.
 * - 이웃이 찍혀 있는 자리만 찍을 수 있다 (직업 시작 노드는 늘 찍혀 있다)
 * - 되돌릴 때 길이 끊기는 노드가 남으면 되돌리지 않는다
 * - 포인트가 모자라면 더 찍지 않는다
 */
class TreeAllocation(
    private val geometry: TreeGeometry,
    /** 전직 노드 표시 (배열이 비어 있으면 종류로 판단한다) */
    private val ascendancy: BooleanArray = BooleanArray(0),
    /** 직업 시작 노드의 배열 위치 (없으면 -1) */
    val classStart: Int = -1,
    /** 전직 시작 노드의 배열 위치 (없으면 -1) */
    val ascendStart: Int = -1,
    /** 찍을 수 있는 총 포인트 (0이면 제한 없음) */
    var pointTotal: Int = 0,
    /** 전직 포인트 총량 */
    var ascendancyTotal: Int = 8,
) {

    enum class Result { DONE, NOT_FOUND, ALREADY, NOT_ALLOCATED, NOT_CONNECTED, NO_POINTS, START_NODE, BREAKS_PATH }

    val allocated: BooleanArray = BooleanArray(geometry.size)
    private var bound: BooleanArray = BooleanArray(0)
    private var adjacency: Array<IntArray> = EMPTY_ADJ

    init {
        rebuild()
    }

    val size: Int get() = geometry.size

    private fun rebuild() {
        if (geometry.size == 0) return
        val lists = Array(geometry.size) { ArrayList<Int>(3) }
        var i = 0
        while (i + 1 < geometry.edges.size) {
            val a = geometry.edges[i]
            val b = geometry.edges[i + 1]
            lists[a].add(b)
            lists[b].add(a)
            i += 2
        }
        adjacency = Array(geometry.size) { lists[it].toIntArray() }
    }

    fun indexOf(id: Int): Int = geometry.indexOf(id)

    fun isAllocated(id: Int): Boolean {
        val index = indexOf(id)
        return index >= 0 && allocated[index]
    }

    fun allocatedIds(): List<Int> {
        val out = ArrayList<Int>()
        for (i in allocated.indices) if (allocated[i]) out.add(geometry.ids[i])
        return out
    }

    /** 빌드에서 불러온 노드로 채운다. 시작 노드는 늘 포함한다. */
    fun bind(ids: Collection<Int>) {
        java.util.Arrays.fill(allocated, false)
        if (classStart >= 0) allocated[classStart] = true
        ids.forEach { id ->
            val index = indexOf(id)
            if (index >= 0) allocated[index] = true
        }
    }

    /** 지금 상태를 '빌드에서 불러온 상태'로 기억한다 (되돌리기 기준). */
    fun markBound() {
        bound = allocated.copyOf()
    }

    val hasChanges: Boolean
        get() = bound.size == allocated.size && !bound.contentEquals(allocated)

    /** 기억해 둔 상태로 되돌린다. */
    fun resetToBound(): Boolean {
        if (bound.size != allocated.size) return false
        System.arraycopy(bound, 0, allocated, 0, bound.size)
        return true
    }

    /** 포인트 (전직 노드는 따로 센다) */
    fun points(): TreePoints {
        if (geometry.size == 0) return TreePoints()
        var normal = 0
        var ascend = 0
        for (i in allocated.indices) {
            if (!allocated[i]) continue
            val kind = geometry.kindAt(i)
            if (kind == TreeKind.CLASS_START || kind == TreeKind.ASCENDANT_START) continue
            if (isAscendancy(i)) ascend++ else normal++
        }
        return TreePoints(normal, pointTotal, ascend, ascendancyTotal)
    }

    /** 이어진 자리면 찍는다. */
    fun allocate(id: Int): Result {
        val index = indexOf(id)
        if (index < 0) return Result.NOT_FOUND
        if (allocated[index]) return Result.ALREADY
        if (!canReach(index)) return Result.NOT_CONNECTED
        val spent = points()
        if (pointTotal > 0) {
            val asc = isAscendancy(index)
            val used = if (asc) spent.ascendancy else spent.allocated
            val limit = if (asc) ascendancyTotal else pointTotal
            if (limit > 0 && used >= limit) return Result.NO_POINTS
        }
        allocated[index] = true
        return Result.DONE
    }

    /** 찍은 노드를 되돌린다. 길이 끊기면 되돌리지 않는다. */
    fun deallocate(id: Int): Result {
        val index = indexOf(id)
        if (index < 0) return Result.NOT_FOUND
        if (!allocated[index]) return Result.NOT_ALLOCATED
        if (index == classStart || index == ascendStart) return Result.START_NODE
        if (geometry.kindAt(index) == TreeKind.CLASS_START) return Result.START_NODE
        allocated[index] = false
        if (!stillConnected()) {
            allocated[index] = true
            return Result.BREAKS_PATH
        }
        return Result.DONE
    }

    /** 전직 노드인지 — 자료가 표시해 주면 그것을, 아니면 종류로 판단한다 */
    fun isAscendancy(index: Int): Boolean {
        if (index in ascendancy.indices) return ascendancy[index]
        val kind = geometry.kindAt(index)
        return kind == TreeKind.ASCENDANT || kind == TreeKind.ASCENDANT_START
    }

    /** 이 노드를 지금 찍을 수 있는지 */
    fun canReach(index: Int): Boolean {
        if (index == classStart) return true
        val neighbors = adjacency.getOrNull(index) ?: return false
        if (neighbors.any { allocated[it] }) return true
        // 아틀라스처럼 시작점이 없는 트리는 찍힌 노드가 하나도 없을 때만 첫 노드를 연다.
        return classStart < 0 && allocated.none { it }
    }

    /** 찍은 노드가 시작점에서 모두 이어져 있는지 */
    fun stillConnected(): Boolean {
        if (geometry.size == 0) return true
        val roots = ArrayList<Int>()
        if (classStart >= 0 && allocated.getOrElse(classStart) { false }) roots.add(classStart)
        if (roots.isEmpty()) {
            for (i in allocated.indices) if (allocated[i]) {
                roots.add(i)
                break
            }
        }
        if (roots.isEmpty()) return true
        val seen = BooleanArray(geometry.size)
        val queue = ArrayDeque<Int>()
        roots.forEach { seen[it] = true; queue.add(it) }
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            adjacency.getOrNull(at)?.forEach { next ->
                if (!seen[next] && allocated[next]) {
                    seen[next] = true
                    queue.add(next)
                }
            }
        }
        for (i in allocated.indices) if (allocated[i] && !seen[i]) return false
        return true
    }

    private companion object {
        val EMPTY_ADJ = emptyArray<IntArray>()
    }
}

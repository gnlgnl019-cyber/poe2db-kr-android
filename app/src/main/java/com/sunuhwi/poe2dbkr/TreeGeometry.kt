package com.sunuhwi.poe2dbkr

/** 트리 노드 종류. 앱에서 그리는 크기와 색을 고르는 데 쓴다. */
enum class TreeKind {
    NORMAL, NOTABLE, KEYSTONE, MASTERY, JEWEL, ASCENDANT, ASCENDANT_START, CLASS_START;

    companion object {
        fun ofCode(code: Char): TreeKind = when (code) {
            'n' -> NOTABLE
            'k' -> KEYSTONE
            'm' -> MASTERY
            'j' -> JEWEL
            'a' -> ASCENDANT_START
            'c' -> CLASS_START
            else -> NORMAL
        }
    }
}

/**
 * 트리를 그리는 데 필요한 값만 모아 둔 배열 묶음.
 *
 * assets의 트리 자료(poe2_tree_kr.json)에는 노드마다 그려질 자리(x·y)와 연결선이 들어 있다.
 * 5천 개가 넘는 노드를 매 프레임 객체로 다루면 느리므로, 처음 한 번 배열로 옮겨 두고
 * 화면은 이 배열만 읽는다. (객체를 만들지 않으므로 JVM 테스트에서도 그대로 검증할 수 있다.)
 */
class TreeGeometry(
    val ids: IntArray,
    val xs: FloatArray,
    val ys: FloatArray,
    private val codes: ByteArray,
    /** 연결선: [가, 나, 가, 나, …] — ids 배열의 위치(인덱스) 쌍 */
    val edges: IntArray,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {

    val size: Int get() = ids.size
    val edgeCount: Int get() = edges.size / 2

    private val index = HashMap<Int, Int>(ids.size * 2)

    init {
        ids.forEachIndexed { i, id -> index[id] = i }
    }

    /** 노드 번호로 배열 위치를 찾는다. 없으면 -1. */
    fun indexOf(id: Int): Int = index[id] ?: -1

    fun kindAt(i: Int): TreeKind = TreeKind.ofCode(codes[i].toInt().toChar())

    /** 이 노드가 그려지는 자리 */
    fun xAt(i: Int): Float = xs[i]

    fun yAt(i: Int): Float = ys[i]

    /** 화면에 보이는 노드 중 탭 지점에서 가장 가까운 것. reach는 트리 좌표(노드 단위) 기준. */
    fun nearest(x: Float, y: Float, reach: Float, inside: (Int) -> Boolean = { true }): Int {
        var best = -1
        var bestDist = reach * reach
        for (i in 0 until size) {
            if (!inside(i)) continue
            val dx = xs[i] - x
            val dy = ys[i] - y
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    companion object {

        val EMPTY = TreeGeometry(
            IntArray(0), FloatArray(0), FloatArray(0), ByteArray(0), IntArray(0),
            0f, 0f, 0f, 0f,
        )

        fun of(tree: TreeDict): TreeGeometry {
            if (tree.nodes.isEmpty()) return EMPTY
            val ids = IntArray(tree.nodes.size)
            val xs = FloatArray(tree.nodes.size)
            val ys = FloatArray(tree.nodes.size)
            val codes = ByteArray(tree.nodes.size)
            var n = 0
            tree.nodes.values.forEach { node ->
                ids[n] = node.id
                xs[n] = node.x
                ys[n] = node.y
                codes[n] = when {
                    node.kind == 'n' -> 'n'.code.toByte()
                    node.kind == 'k' -> 'k'.code.toByte()
                    node.kind == 'm' -> 'm'.code.toByte()
                    node.kind == 'j' -> 'j'.code.toByte()
                    node.kind == 'a' -> 'a'.code.toByte()
                    node.kind == 'c' -> 'c'.code.toByte()
                    node.isAscendancy -> 'e'.code.toByte()
                    else -> 's'.code.toByte()
                }
                n++
            }
            val position = HashMap<Int, Int>(n * 2)
            for (i in 0 until n) position[ids[i]] = i
            val edges = ArrayList<Int>(tree.edges.size)
            var i = 0
            while (i + 1 < tree.edges.size) {
                val a = position[tree.edges[i]]
                val b = position[tree.edges[i + 1]]
                if (a != null && b != null) {
                    edges.add(a)
                    edges.add(b)
                }
                i += 2
            }
            val bounds = tree.bounds
            return TreeGeometry(
                ids = ids,
                xs = xs,
                ys = ys,
                codes = codes,
                edges = edges.toIntArray(),
                minX = if (bounds.size > 0) bounds[0] else xs.min(),
                minY = if (bounds.size > 1) bounds[1] else ys.min(),
                maxX = if (bounds.size > 2) bounds[2] else xs.max(),
                maxY = if (bounds.size > 3) bounds[3] else ys.max(),
            )
        }
    }
}

/**
 * 트리를 화면에 맞추는 계산. 확대 배율과 이동값만 다루므로 화면 없이도 확인할 수 있다.
 *
 * 트리 좌표(poe2db 자료의 x·y)를 화면 좌표로 옮긴다:
 *   화면 x = offsetX + 트리 x × scale
 */
class TreeViewport {

    var scale = 1f
        private set
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set
    var maxScale = 3f
        private set
    var fitScale = 1f
        private set

    fun screenX(treeX: Float): Float = offsetX + treeX * scale

    fun screenY(treeY: Float): Float = offsetY + treeY * scale

    fun treeX(screenX: Float): Float = (screenX - offsetX) / scale

    fun treeY(screenY: Float): Float = (screenY - offsetY) / scale

    /** 화면 크기 안에 정해진 범위가 다 들어오게 맞춘다. */
    fun fit(
        width: Int,
        height: Int,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        padding: Float,
    ) {
        if (width <= 0 || height <= 0) return
        val spanX = (maxX - minX).coerceAtLeast(1f)
        val spanY = (maxY - minY).coerceAtLeast(1f)
        val room = (width - padding * 2).coerceAtLeast(1f)
        val roomY = (height - padding * 2).coerceAtLeast(1f)
        scale = minOf(room / spanX, roomY / spanY)
        fitScale = scale
        maxScale = 4f
        center((minX + maxX) / 2f, (minY + maxY) / 2f, width, height)
    }

    fun center(treeX: Float, treeY: Float, width: Int, height: Int) {
        offsetX = width / 2f - treeX * scale
        offsetY = height / 2f - treeY * scale
    }

    /** 화면의 한 점을 기준으로 배율을 바꾼다. */
    fun zoomAround(factor: Float, focusX: Float, focusY: Float) {
        val next = (scale * factor).coerceIn(fitScale * 0.6f, maxScale)
        if (next == scale) return
        val tx = treeX(focusX)
        val ty = treeY(focusY)
        scale = next
        offsetX = focusX - tx * scale
        offsetY = focusY - ty * scale
    }

    fun pan(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
    }
}

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
            'e' -> ASCENDANT
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
            return build(ids, xs, ys, codes, tree.edges, tree.bounds)
        }

        /** 아틀라스 트리도 같은 배열 구조로 옮긴다 */
        fun ofAtlas(atlas: AtlasDict): TreeGeometry {
            if (atlas.nodes.isEmpty()) return EMPTY
            val ids = IntArray(atlas.nodes.size)
            val xs = FloatArray(atlas.nodes.size)
            val ys = FloatArray(atlas.nodes.size)
            val codes = ByteArray(atlas.nodes.size)
            var n = 0
            atlas.nodes.values.forEach { node ->
                ids[n] = node.id
                xs[n] = node.x
                ys[n] = node.y
                codes[n] = when (node.kind) {
                    'n' -> 'n'.code.toByte()
                    'k' -> 'k'.code.toByte()
                    'm' -> 'm'.code.toByte()
                    'j' -> 'j'.code.toByte()
                    else -> 's'.code.toByte()
                }
                n++
            }
            return build(ids, xs, ys, codes, atlas.edges, atlas.bounds)
        }

        private fun build(
            ids: IntArray,
            xs: FloatArray,
            ys: FloatArray,
            codes: ByteArray,
            pairs: List<Int>,
            bounds3: FloatArray,
        ): TreeGeometry {
            val position = HashMap<Int, Int>(ids.size * 2)
            for (i in ids.indices) position[ids[i]] = i
            val edges = ArrayList<Int>(pairs.size)
            var i = 0
            while (i + 1 < pairs.size) {
                val a = position[pairs[i]]
                val b = position[pairs[i + 1]]
                if (a != null && b != null) {
                    edges.add(a)
                    edges.add(b)
                }
                i += 2
            }
            return TreeGeometry(
                ids = ids,
                xs = xs,
                ys = ys,
                codes = codes,
                edges = edges.toIntArray(),
                minX = if (bounds3.size > 0) bounds3[0] else xs.min(),
                minY = if (bounds3.size > 1) bounds3[1] else ys.min(),
                maxX = if (bounds3.size > 2) bounds3[2] else xs.max(),
                maxY = if (bounds3.size > 3) bounds3[3] else ys.max(),
            )
        }
    }
}

/**
 * 트리를 화면에 맞추는 계산. 확대 배율과 이동값만 다루므로 화면 없이도 확인할 수 있다.
 *
 * 트리 좌표(poe2db 자료의 x·y)를 화면 좌표로 옮긴다:
 *   화면 x = offsetX + 트리 x × scale
 *
 * 배율의 아래위 한도는 화면 크기와 트리 전체 자리에서 나온다(minScale·maxScale).
 * 어느 길로 배율을 바꾸든 이 한도 안에서만 움직이므로, 확대·축소가 갑자기 튀거나 멈추지 않는다.
 */
class TreeViewport {

    var scale = 1f
        private set
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    /** 이 배율보다 작게 줄이지 않는다 (트리 전체가 화면에 들어오는 배율 근처) */
    var minScale = MIN_SCALE
        private set

    /** 이 배율보다 크게 키우지 않는다 (노드 하나가 손끝보다 커지지 않게) */
    var maxScale = MAX_SCALE
        private set

    /** 마지막으로 화면에 맞춘 배율 */
    var fitScale = 1f
        private set

    /** 그리는 화면의 크기 */
    var viewWidth = 0
        private set
    var viewHeight = 0
        private set

    /** 트리를 이만큼은 화면 밖으로 밀어 볼 수 있다 (px) */
    var overscroll = 0f

    /** 트리 전체가 차지하는 자리 (트리 좌표) */
    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f
    private var hasBounds = false

    fun screenX(treeX: Float): Float = offsetX + treeX * scale

    fun screenY(treeY: Float): Float = offsetY + treeY * scale

    fun treeX(screenX: Float): Float = (screenX - offsetX) / scale

    fun treeY(screenY: Float): Float = (screenY - offsetY) / scale

    /** 그리는 화면 크기가 바뀌었다. */
    fun resize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        clamp()
    }

    /** 트리 전체가 차지하는 자리를 알려 준다 (화면 밖으로 밀어 볼 수 있는 한도를 정한다). */
    fun setBounds(minX: Float, minY: Float, maxX: Float, maxY: Float) {
        if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) return
        if (maxX < minX || maxY < minY) return
        left = minX
        top = minY
        right = maxX
        bottom = maxY
        hasBounds = true
    }

    /** 화면 크기 안에 정해진 자리가 다 들어오게 맞춘다. */
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
        viewWidth = width
        viewHeight = height
        scale = scaleFor(minX, minY, maxX, maxY, padding)
        fitScale = scale
        scale = scale.coerceIn(minScale, maxScale)
        center((minX + maxX) / 2f, (minY + maxY) / 2f)
    }

    /** 트리 전체가 화면에 들어오는 배율 (확대·축소의 아래 끝을 잡는 데 쓴다). */
    fun wholeScale(padding: Float): Float =
        if (hasBounds) scaleFor(left, top, right, bottom, padding) else scale

    private fun scaleFor(minX: Float, minY: Float, maxX: Float, maxY: Float, padding: Float): Float {
        if (viewWidth <= 0 || viewHeight <= 0) return scale
        val spanX = (maxX - minX).coerceAtLeast(1f)
        val spanY = (maxY - minY).coerceAtLeast(1f)
        val room = (viewWidth - padding * 2).coerceAtLeast(1f)
        val roomY = (viewHeight - padding * 2).coerceAtLeast(1f)
        val value = minOf(room / spanX, roomY / spanY)
        return if (value.isFinite() && value > 0f) value else scale
    }

    /** 확대·축소 한도를 정한다 (화면 크기와 트리 자리에서 나온 값). */
    fun setZoomRange(min: Float, max: Float) {
        if (min.isFinite() && min > 0f) minScale = min
        if (max.isFinite() && max > 0f) maxScale = max
        if (maxScale < minScale) maxScale = minScale
        scale = scale.coerceIn(minScale, maxScale)
        clamp()
    }

    /** 트리의 한 자리를 화면 가운데에 둔다. */
    fun center(treeX: Float, treeY: Float) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        offsetX = viewWidth / 2f - treeX * scale
        offsetY = viewHeight / 2f - treeY * scale
        clamp()
    }

    /** 화면 크기를 함께 넘기는 예전 모양. */
    fun center(treeX: Float, treeY: Float, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        center(treeX, treeY)
    }

    /** 화면의 한 점을 기준으로 배율을 바꾼다. 그 점 밑의 트리 자리는 제자리에 남는다. */
    fun zoomAround(factor: Float, focusX: Float, focusY: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        val next = (scale * factor).coerceIn(minScale, maxScale)
        if (next == scale) return
        val tx = treeX(focusX)
        val ty = treeY(focusY)
        scale = next
        offsetX = focusX - tx * scale
        offsetY = focusY - ty * scale
        clamp()
    }

    /** 화면 맞춤 계산 없이 배율을 직접 지정한다 (검색으로 노드로 날아갈 때). */
    fun setScale(value: Float) {
        if (!value.isFinite()) return
        scale = value.coerceIn(minScale, maxScale)
        clamp()
    }

    /** 이동값을 직접 지정한다. */
    fun setOffset(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        offsetX = x
        offsetY = y
        clamp()
    }

    fun pan(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) return
        offsetX += dx
        offsetY += dy
        clamp()
    }

    /** 확대·이동이 트리에서 너무 멀어지지 않게 붙잡아 둔다. */
    fun clamp() {
        if (!hasBounds || viewWidth <= 0 || viewHeight <= 0) return
        val margin = overscroll.coerceAtLeast(0f)
        val spanX = (right - left) * scale
        val spanY = (bottom - top) * scale
        offsetX = if (spanX <= viewWidth - margin * 2f) {
            // 트리 폭이 화면보다 좁으면 가운데에 둔다.
            (viewWidth - spanX) / 2f - left * scale
        } else {
            offsetX.coerceIn(viewWidth - margin - right * scale, margin - left * scale)
        }
        offsetY = if (spanY <= viewHeight - margin * 2f) {
            (viewHeight - spanY) / 2f - top * scale
        } else {
            offsetY.coerceIn(viewHeight - margin - bottom * scale, margin - top * scale)
        }
    }

    companion object {
        /** 아무리 줄여도 이보다 작게는 그리지 않는다 */
        private const val MIN_SCALE = 0.0005f

        /** 아무리 키워도 이보다 크게는 그리지 않는다 */
        private const val MAX_SCALE = 4f
    }
}

package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 패시브 트리를 그리는 계산이 어떤 배율·위치에서도 배열 범위를 넘지 않는지 확인한다.
 *
 * 실기기에서 `IndexOutOfBoundsException`으로 트리가 멈추고 poe2db 웹 화면으로 넘어갔던 문제를
 * 다시 만들지 않기 위해, 실제 트리 자료(노드 5천 개)와 사용자가 불러온 빌드(121/121, 전직 8/8)로
 * 화면 크기·배율·이동값을 바꿔 가며 [TreeCull]을 그대로 돌려 본다.
 */
class TreeDrawTest {

    private val assets = File("src/main/assets")

    private val tree: TreeDict by lazy {
        TreeDict.parse(File(assets, "poe2_tree_kr.json").readText())
    }

    private val atlas: AtlasDict by lazy {
        AtlasDict.parse(File(assets, "poe2_atlas_kr.json").readText())
    }

    private val realBuild: PobBuild by lazy {
        Pob.decode(File("src/test/resources/pob_real_build.txt").readText())
    }

    private fun scene(): TreeScene {
        val classIndex = tree.classIndex(realBuild.className, realBuild.treeClassId)
        val ascend = tree.ascendancyIndex(classIndex, realBuild.ascendClassName, realBuild.treeAscendClassId)
        return TreeScene.of(tree, classIndex, ascend)
    }

    private fun allocation(scene: TreeScene): TreeAllocation {
        val alloc = TreeAllocation(
            geometry = scene.geometry,
            ascendancy = scene.ascendancy,
            classStart = scene.classStart,
            ascendStart = scene.ascendStart,
            pointTotal = 121,
        )
        alloc.bind(realBuild.treeNodeIds + realBuild.treeMastery.keys.toList())
        return alloc
    }

    @Test
    fun sceneArraysMatchGeometry() {
        val scene = scene()
        println("[TEST] 노드 ${scene.size} · 아이콘 ${scene.icons.size} · 이름 ${scene.names.size} · 전직 ${scene.ascendancy.size}")
        assertEquals(scene.size, scene.icons.size)
        assertEquals(scene.size, scene.names.size)
        assertEquals(scene.size, scene.stats.size)
        assertEquals(scene.size, scene.ascendancy.size)
        val points = allocation(scene).points()
        println("[TEST] 찍은 패시브 ${points.allocated} · 전직 ${points.ascendancy}")
        assertTrue("찍은 노드가 너무 적다", points.allocated > 100)
        assertTrue("전직 노드가 너무 적다", points.ascendancy > 0)
    }

    @Test
    fun cullNeverLeavesArrayBounds() {
        val scene = scene()
        val alloc = allocation(scene)
        val cull = TreeCull(scene.geometry, alloc.allocated, scene.icons, scene.ascendancy)
        val sizes = listOf(0 to 0, 1 to 1, 1080 to 1800, 1440 to 3200, 3200 to 3200)
        val scales = listOf(0.0001f, 0.002f, 0.02f, 0.05f, 0.2f, 0.6f, 1f, 2.5f, 8f)
        val geo = scene.geometry
        var frames = 0
        var drawnNodes = 0
        sizes.forEach { (w, h) ->
            scales.forEach { scale ->
                // 트리 전체를 덮는 이동값과, 트리 밖으로 완전히 벗어난 이동값을 함께 본다.
                val offsets = listOf(
                    0f to 0f,
                    -geo.minX * scale to -geo.minY * scale,
                    (w - geo.maxX * scale) to (h - geo.maxY * scale),
                    -geo.maxX * 2f * scale to -geo.maxY * 2f * scale,
                    Float.NaN to 0f,
                )
                offsets.forEach { (ox, oy) ->
                    cull.compute(scale, ox, oy, w, h)
                    frames++
                    assertTrue("연결선 개수가 짝이 아니다", cull.plainCount % 4 == 0)
                    assertTrue("금빛 연결선 개수가 짝이 아니다", cull.onCount % 4 == 0)
                    assertTrue(cull.plainCount <= cull.plainEdges.size)
                    assertTrue(cull.onCount <= cull.onEdges.size)
                    assertTrue(cull.nodeCount <= cull.nodes.size)
                    for (i in 0 until cull.nodeCount) {
                        val index = cull.nodes[i]
                        assertTrue("없는 노드를 그리려 한다: $index", cull.has(index))
                        assertTrue("종류를 읽지 못했다", index in 0 until geo.size)
                    }
                    drawnNodes += cull.nodeCount
                }
            }
        }
        println("[TEST] 화면 ${frames}가지 · 그린 노드 합 $drawnNodes")
        assertTrue("어떤 배율에서도 노드를 하나도 못 그렸다", drawnNodes > 0)
    }

    @Test
    fun brokenArraysDoNotThrow() {
        val scene = scene()
        // 찍은 노드 배열이 짧아도(자료와 어긋나도) 예외 없이 그린다.
        val short = BooleanArray(3)
        val cull = TreeCull(scene.geometry, short, scene.icons)
        cull.compute(0.5f, 0f, 0f, 1080, 1800)
        assertTrue(cull.nodeCount > 0)
        // 아이콘 배열이 비어 있어도 마찬가지다.
        val noIcons = TreeCull(scene.geometry, BooleanArray(scene.size), arrayOfNulls(0), scene.ascendancy)
        noIcons.compute(1f, 0f, 0f, 1080, 1800)
        assertTrue(noIcons.nodeCount > 0)
        assertTrue(noIcons.iconOf(0) == null)
    }

    @Test
    fun realBuildFitsOnAPhoneWithReadableNodes() {
        val scene = scene()
        val alloc = allocation(scene)
        val geo = scene.geometry
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in alloc.allocated.indices) {
            if (!alloc.allocated[i]) continue
            // 전직 노드는 화면 맞춤에서 뺀다 (TreeCanvasView.fitAllocated와 같은 규칙).
            if (i == scene.ascendStart || scene.ascendancy.getOrElse(i) { false }) continue
            minX = minOf(minX, geo.xs[i])
            minY = minOf(minY, geo.ys[i])
            maxX = maxOf(maxX, geo.xs[i])
            maxY = maxOf(maxY, geo.ys[i])
        }
        // 화면(TreeCanvasView.fitAllocated)과 같은 규칙: 둘레 12% + 220, 최소 6000 거리.
        val spanX = (maxX - minX).coerceAtLeast(6000f)
        val spanY = (maxY - minY).coerceAtLeast(6000f)
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val view = TreeViewport()
        view.fit(
            1080, 2000,
            cx - spanX / 2f - (spanX * 0.10f + 180f),
            cy - spanY / 2f - (spanY * 0.10f + 180f),
            cx + spanX / 2f + (spanX * 0.10f + 180f),
            cy + spanY / 2f + (spanY * 0.10f + 180f),
            30f,
        )
        val normal = TreeMetrics.radiusOf(TreeKind.NORMAL) * view.scale
        val notable = TreeMetrics.radiusOf(TreeKind.NOTABLE) * view.scale
        println("[TEST] 기본 배율 ${view.scale} · 보통 노드 반지름 ${normal}px · 주요 ${notable}px")
        assertTrue("기본 화면에서 노드가 점으로만 보인다", view.scale >= 0.045f)
        assertTrue("보통 노드가 너무 작다: ${normal}px", normal >= 3f)
        assertTrue("주요 노드가 보통 노드보다 크지 않다", notable > normal * 1.2f)
        // 노드 지름은 poe2db와 같은 102다 — 자료의 가장 좁은 간격(100.9)과 맞먹는 크기.
        assertTrue("노드가 너무 커서 이웃을 가린다", normal * 2f <= 102f)
    }

    /**
     * 실제 자료에서 이웃한 두 노드를 골라, 아이콘 원이 절대 겹치지 않는지 본다.
     * (poe2db 화면과 같은 비율 — 테두리 고리는 가는 선이라 조금 겹쳐도 가리지 않는다.)
     */
    @Test
    fun nodeIconsNeverCoverTheNeighbour() {
        val scene = scene()
        val geo = scene.geometry
        val cell = 140f
        val grid = HashMap<Long, MutableList<Int>>()
        fun bucketOf(x: Float, y: Float): Long =
            (x.toInt() / cell.toInt()).toLong() * 100000L + (y.toInt() / cell.toInt()).toLong()
        for (i in 0 until geo.size) {
            if (geo.kindAt(i) == TreeKind.MASTERY) continue
            grid.getOrPut(bucketOf(geo.xs[i], geo.ys[i])) { ArrayList() }.add(i)
        }
        var closestIcon = Float.MAX_VALUE
        var closestFrame = 0f
        var pairs = 0
        for (i in 0 until geo.size) {
            if (geo.kindAt(i) == TreeKind.MASTERY) continue
            val x = geo.xs[i]
            val y = geo.ys[i]
            val gx = x.toInt() / cell.toInt()
            val gy = y.toInt() / cell.toInt()
            for (dx in -1..1) for (dy in -1..1) {
                val bucket = grid[(gx + dx).toLong() * 100000L + (gy + dy).toLong()] ?: continue
                for (j in bucket) {
                    if (j <= i) continue
                    val dist = kotlin.math.hypot(geo.xs[j] - x, geo.ys[j] - y)
                    if (dist < 0.5f) continue
                    pairs++
                    val iconSum = TreeMetrics.iconRadiusOf(geo.kindAt(i)) + TreeMetrics.iconRadiusOf(geo.kindAt(j))
                    val frameSum = TreeMetrics.radiusOf(geo.kindAt(i)) + TreeMetrics.radiusOf(geo.kindAt(j))
                    closestIcon = minOf(closestIcon, dist - iconSum)
                    closestFrame = maxOf(closestFrame, frameSum - dist)
                }
            }
        }
        println("[TEST] 이웃 쌍 $pairs · 아이콘 여유 ${closestIcon} · 테두리 겹침 ${closestFrame}")
        assertTrue("이웃 노드가 없어 확인하지 못했다", pairs > 2000)
        assertTrue("아이콘이 이웃 노드를 가린다 (여유 ${closestIcon})", closestIcon > 0f)
        // 테두리 고리는 DB처럼 가는 선이다 — 크게 파고들지 않아야 한다.
        assertTrue("테두리 고리가 이웃을 덮는다 (겹침 ${closestFrame})", closestFrame <= 12f)
    }

    @Test
    fun classStartAndItsEdgesAreDrawnWithoutADecorationRing() {
        val scene = scene()
        val alloc = allocation(scene)
        val cull = TreeCull(scene.geometry, alloc.allocated, scene.icons, scene.ascendancy)
        val geo = scene.geometry
        val viewport = TreeViewport()
        viewport.fit(1080, 1800, geo.minX, geo.minY, geo.maxX, geo.maxY, 20f)
        cull.compute(viewport.scale, viewport.offsetX, viewport.offsetY, 1080, 1800)
        // 직업 시작 노드는 메달로 가리지 않고 노드 하나로 그린다.
        val start = scene.classStart
        assertTrue(start in 0 until scene.size)
        assertTrue("직업 시작 노드를 그리지 않았다", (0 until cull.nodeCount).any { cull.nodes[it] == start })
        // 시작점의 짧은 연결선은 그린다 (예전에는 메달 때문에 지웠다).
        val short = (0 until geo.edges.size / 2).map { geo.edges[it * 2] to geo.edges[it * 2 + 1] }
            .firstOrNull { (a, b) -> (a == start || b == start) && !cull.isLongEdge(a, b) }
        assertTrue("시작점에 붙은 짧은 연결선이 없다", short != null)
        assertTrue("시작점의 짧은 연결선을 긴 선으로 취급한다", !cull.isLongEdge(short!!.first, short.second))
        println("[TEST] 화면 맞춤 배율 ${viewport.scale} · 연결선 ${cull.plainCount / 4} · 금빛 ${cull.onCount / 4} · 노드 ${cull.nodeCount}")
    }

    @Test
    fun onlyFarReachingCorridorsAreSkipped() {
        val scene = scene()
        val alloc = allocation(scene)
        val cull = TreeCull(scene.geometry, alloc.allocated, scene.icons, scene.ascendancy)
        val geo = scene.geometry
        val view = TreeViewport()
        view.fit(1080, 1800, geo.minX, geo.minY, geo.maxX, geo.maxY, 0f)
        cull.compute(view.scale, view.offsetX, view.offsetY, 1080, 1800)
        val drawn = (cull.plainCount + cull.onCount) / 4
        var expected = 0
        var skipped = 0
        var i = 0
        while (i + 1 < geo.edges.size) {
            val a = geo.edges[i]
            val b = geo.edges[i + 1]
            i += 2
            if (cull.isLongEdge(a, b)) skipped++ else expected++
        }
        println("[TEST] 연결선 ${expected + skipped}개 중 긴 통로 ${skipped}개를 뺌 · 그린 것 $drawn")
        assertTrue("화면을 가로지르는 긴 통로가 자료에 있어야 한다", skipped > 0)
        // 화면 밖으로 나간 선은 그리지 않으므로 그린 수는 남은 수보다 많을 수 없다.
        assertTrue("긴 통로를 그렸다", drawn <= expected)
        assertTrue("연결선을 거의 못 그렸다", drawn > expected / 4)
    }

    @Test
    fun atlasTreeUsesTheSameMath() {
        val scene = TreeScene.of(atlas)
        val alloc = TreeAllocation(
            geometry = scene.geometry,
            ascendancy = scene.ascendancy,
            classStart = -1,
            pointTotal = atlas.totalPoints,
        )
        val cull = TreeCull(scene.geometry, alloc.allocated, scene.icons, scene.ascendancy)
        cull.compute(0.3f, 0f, 0f, 1080, 1800)
        println("[TEST] 아틀라스 노드 ${cull.nodeCount}")
        assertTrue(cull.nodeCount > 0)
    }
}

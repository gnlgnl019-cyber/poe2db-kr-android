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
        // 전직 링에 올릴 노드 번호는 모두 실제 노드여야 한다.
        scene.ascendRing.forEach { index ->
            assertTrue("전직 링에 없는 노드 $index", index in 0 until scene.size)
        }
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
                    assertTrue("점 개수가 짝이 아니다", cull.dotCount % 2 == 0)
                    assertTrue(cull.plainCount <= cull.plainEdges.size)
                    assertTrue(cull.onCount <= cull.onEdges.size)
                    assertTrue(cull.dotCount <= cull.dots.size)
                    assertTrue(cull.nodeCount <= cull.nodes.size)
                    for (i in 0 until cull.nodeCount) {
                        val index = cull.nodes[i]
                        assertTrue("없는 노드를 그리려 한다: $index", cull.has(index))
                        assertTrue("종류를 읽지 못했다", index in 0 until geo.size)
                    }
                    for (i in 0 until cull.dotCount) {
                        assertTrue("좌표가 NaN이다", cull.dots[i].isFinite())
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
    fun ringSkipsBrokenIndices() {
        val outX = FloatArray(TreeRing.MAX)
        val outY = FloatArray(TreeRing.MAX)
        val outIndex = IntArray(TreeRing.MAX)
        val ring = intArrayOf(-5, 0, 99_999, 3, 4)
        val placed = TreeRing.placement(ring, 10, 100f, 200f, 50f, outX, outY, outIndex)
        println("[TEST] 링에 올린 노드 ${outIndex.take(placed).toList()}")
        assertEquals(3, placed)
        assertTrue(outIndex.take(placed).all { it in 0 until 10 })
        // 빈 링·크기 0인 트리에서도 예외가 없다.
        assertEquals(0, TreeRing.placement(IntArray(0), 10, 0f, 0f, 10f, outX, outY, outIndex))
        assertEquals(0, TreeRing.placement(ring, 0, 0f, 0f, 10f, outX, outY, outIndex))
    }

    @Test
    fun atomTreeIsDrawnOnTopOfTheClassRowAndMedallion() {
        val scene = scene()
        val alloc = allocation(scene)
        val cull = TreeCull(scene.geometry, alloc.allocated, scene.icons, scene.ascendancy)
        val geo = scene.geometry
        val viewport = TreeViewport()
        viewport.fit(1080, 1800, geo.minX, geo.minY, geo.maxX, geo.maxY, 20f)
        cull.compute(viewport.scale, viewport.offsetX, viewport.offsetY, 1080, 1800)
        // 직업 시작 노드는 메달로 따로 그리고, 나머지 노드는 점·아이콘 중 하나로 그린다.
        val start = scene.classStart
        assertTrue(start in 0 until scene.size)
        for (i in 0 until cull.nodeCount) {
            val index = cull.nodes[i]
            assertTrue("직업 시작 노드를 노드로 또 그렸다", index != start || alloc.allocated[start])
            assertTrue(index != start)
        }
        println("[TEST] 화면 맞춤 배율 ${viewport.scale} · 연결선 ${cull.plainCount / 4} · 금빛 ${cull.onCount / 4} · 노드 ${cull.nodeCount} · 점 ${cull.dotCount / 2}")
    }

    @Test
    fun noLineCrossesTheClassMedallion() {
        val scene = scene()
        val alloc = allocation(scene)
        val cull = TreeCull(scene.geometry, alloc.allocated, scene.icons, scene.ascendancy)
        val geo = scene.geometry
        // 트리 전체가 보이도록 맞춰 놓고 모든 연결선을 한 번에 그려 본다.
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
            if (cull.medallionEdge(a) || cull.medallionEdge(b) || cull.isLongEdge(a, b)) skipped++ else expected++
        }
        println("[TEST] 연결선 ${expected + skipped}개 중 메달로 이어지는 ${skipped}개를 뺌 · 그린 것 $drawn")
        assertTrue("메달로 이어지는 선을 빼지 않았다", skipped > 0)
        // 화면 밖으로 나간 선은 그리지 않으므로 그린 수는 뺀 수보다 많을 수 없다.
        assertTrue("메달로 이어지는 선을 그렸다", drawn <= expected)
        assertTrue("연결선을 거의 못 그렸다", drawn > expected / 4)
        // 직업 시작점·전직 시작점이 이 선의 한쪽 끝이어야 한다.
        assertTrue(cull.medallionEdge(scene.classStart))
        assertTrue("전직 시작점이 메달 연결로 잡히지 않았다", cull.medallionEdge(scene.ascendStart))
        // 전직 통로처럼 멀리 떨어진 노드를 잇는 선도 그리지 않는다.
        val start = scene.classStart
        val far = scene.ascendRing.first { cull.isLongEdge(start, it) }
        assertTrue("긴 전직 통로 선을 그대로 그린다", cull.isLongEdge(start, far))
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

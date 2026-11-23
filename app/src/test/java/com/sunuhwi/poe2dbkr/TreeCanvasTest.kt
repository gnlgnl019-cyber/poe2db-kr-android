package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 앱이 직접 그리는 패시브 트리가 불러온 캐릭터를 제대로 그리는지 확인한다.
 *
 * - 트리 자료에 모든 노드의 자리와 연결선이 있는지
 * - 빌드의 찍은 노드가 모두 그 자료 안에 있는지 (없으면 화면에서 빠진다)
 * - 화면 맞춤·확대 계산이 노드를 화면 안에 두는지
 * - 앱이 만들어 주는 poe2db 트리 주소가 실제로 열렸던 주소와 같은지
 */
class TreeCanvasTest {

    private val tree: TreeDict by lazy {
        val file = File("src/main/assets/poe2_tree_kr.json")
        assertTrue("트리 자료가 없다: ${file.absolutePath}", file.isFile)
        TreeDict.parse(file.readText())
    }

    private val realBuild: PobBuild by lazy {
        Pob.decode(File("src/test/resources/pob_real_build.txt").readText())
    }

    @Test
    fun everyNodeHasAPosition() {
        val geo = tree.geometry
        println("[TEST] 노드 ${geo.size} · 연결선 ${geo.edgeCount} · 범위 ${geo.minX}..${geo.maxX} / ${geo.minY}..${geo.maxY}")
        assertTrue("노드가 너무 적다: ${geo.size}", geo.size > 5000)
        assertTrue("연결선이 너무 적다: ${geo.edgeCount}", geo.edgeCount > 5000)
        var offscreen = 0
        for (i in 0 until geo.size) {
            if (geo.xs[i] == 0f && geo.ys[i] == 0f) offscreen++
            assertTrue("범위를 벗어난 노드: ${geo.ids[i]}", geo.xs[i] in geo.minX..geo.maxX)
            assertTrue("범위를 벗어난 노드: ${geo.ids[i]}", geo.ys[i] in geo.minY..geo.maxY)
        }
        // 시작 지점(0,0)에 겹쳐 있는 노드는 없어야 한다.
        assertTrue("자리가 없는 노드 ${offscreen}개", offscreen == 0)
    }

    @Test
    fun everyEdgePointsAtARealNode() {
        val geo = tree.geometry
        assertEquals("연결선 배열이 짝이 맞지 않는다", 0, geo.edges.size % 2)
        geo.edges.forEach { index ->
            assertTrue("없는 노드를 가리키는 연결선", index in 0 until geo.size)
        }
        // 같은 노드끼리 이어진 선은 없다.
        var i = 0
        while (i + 1 < geo.edges.size) {
            assertTrue("자기 자신과 이어진 노드", geo.edges[i] != geo.edges[i + 1])
            i += 2
        }
    }

    @Test
    fun allocatedNodesOfRealBuildAreAllDrawn() {
        val geo = tree.geometry
        val missing = realBuild.treeNodeIds.filter { geo.indexOf(it) < 0 }
        println("[TEST] 찍은 노드 ${realBuild.treeNodeIds.size}개 중 자료에 없는 것: $missing")
        assertTrue("트리 자료에 없는 찍은 노드: $missing", missing.isEmpty())
        // 찍은 노드끼리 이어진 길도 그릴 수 있어야 한다 (황금색 선).
        val allocated = realBuild.treeNodeIds.toSet()
        var linked = 0
        var i = 0
        while (i + 1 < geo.edges.size) {
            val a = allocated.contains(geo.ids[geo.edges[i]])
            val b = allocated.contains(geo.ids[geo.edges[i + 1]])
            if (a && b) linked++
            i += 2
        }
        println("[TEST] 찍은 노드끼리 이어진 연결선 ${linked}개")
        assertTrue("찍은 노드 사이 연결선을 하나도 못 찾았다", linked > 80)
    }

    @Test
    fun fitPutsAllocatedAreaOnScreen() {
        val geo = tree.geometry
        val allocated = realBuild.treeNodeIds.map { geo.indexOf(it) }.filter { it >= 0 }
        val minX = allocated.minOf { geo.xs[it] }
        val maxX = allocated.maxOf { geo.xs[it] }
        val minY = allocated.minOf { geo.ys[it] }
        val maxY = allocated.maxOf { geo.ys[it] }
        val view = TreeViewport()
        view.fit(1080, 1800, minX, minY, maxX, maxY, 60f)
        allocated.forEach { i ->
            val x = view.screenX(geo.xs[i])
            val y = view.screenY(geo.ys[i])
            assertTrue("노드가 화면 밖으로 나갔다: $x", x in -1f..1081f)
            assertTrue("노드가 화면 밖으로 나갔다: $y", y in -1f..1801f)
        }
        // 찍은 자리가 화면을 절반 이상 채운다.
        val width = view.screenX(maxX) - view.screenX(minX)
        val height = view.screenY(maxY) - view.screenY(minY)
        println("[TEST] 맞춤 배율 ${view.scale} · 찍은 자리 ${width.toInt()}x${height.toInt()}px")
        assertTrue("찍은 자리가 너무 작게 잡혔다", width > 540f || height > 900f)
    }

    @Test
    fun zoomKeepsThePointUnderTheFingers() {
        val geo = tree.geometry
        val view = TreeViewport()
        view.fit(1080, 1800, geo.minX, geo.minY, geo.maxX, geo.maxY, 0f)
        val focusX = 640f
        val focusY = 900f
        val treeX = view.treeX(focusX)
        val treeY = view.treeY(focusY)
        view.zoomAround(2.5f, focusX, focusY)
        assertEquals(focusX, view.screenX(treeX), 0.5f)
        assertEquals(focusY, view.screenY(treeY), 0.5f)
        view.pan(-120f, 80f)
        assertEquals(focusX - 120f, view.screenX(treeX), 0.5f)
        assertEquals(focusY + 80f, view.screenY(treeY), 0.5f)
        // 너무 작게 줄이지 않는다.
        repeat(20) { view.zoomAround(0.2f, focusX, focusY) }
        assertTrue("배율이 0 이하로 내려갔다", view.scale > 0f)
    }

    @Test
    fun tapFindsTheNodeUnderTheFinger() {
        val geo = tree.geometry
        val index = geo.indexOf(realBuild.treeNodeIds.first())
        assertTrue(index >= 0)
        val found = geo.nearest(geo.xs[index] + 10f, geo.ys[index] - 10f, 60f)
        assertEquals("가장 가까운 노드를 찾지 못했다", geo.ids[index], geo.ids[found])
        // 동떨어진 자리에서는 아무 노드도 잡히지 않는다.
        assertEquals(-1, geo.nearest(geo.minX - 5000f, geo.minY - 5000f, 60f))
    }

    @Test
    fun treeUrlIsTheOneVerifiedOnPoe2db() {
        // 이 주소는 실제 크롬에서 열어 찍은 노드 122개(전직 8 포함)가 그려지는 것을 확인했다.
        val url = Poe2Tree.url(realBuild, tree)
        assertTrue(
            "주소가 바뀌었다. poe2db에서 다시 확인해야 한다: $url",
            url.startsWith("https://poe2db.tw/kr/passive-skill-tree/AAAABwoBAHoBRgAAAtIAAAWMAAAGywAACEEAAAk5AAAKFgAAC3gAAAyzAAAQPQ"),
        )
        val state = tree.decode(url)
        assertNotNull(state)
        assertEquals(10, state!!.classIndex)
        assertEquals(1, state.ascendancyIndex)
        assertEquals(122, state.nodes.size)
    }

    @Test
    fun ascendancyStartNodesAreKnown() {
        assertEquals(11495, tree.startNode(10, 1))
        assertEquals(44683, tree.startNode(10, 0))
        assertEquals(50459, tree.startNode(2, 0))
    }
}

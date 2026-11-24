package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 앱 안에서 직접 그리는 패시브·아틀라스 트리 화면의 바탕 자료와 찍기 규칙을 확인한다.
 *
 * - 노드 아이콘·직업 일러스트가 오프라인 자산으로 들어 있는지
 * - 아틀라스 트리 좌표·연결선·포인트가 자료에 있는지
 * - 이어진 자리만 찍히고, 길이 끊기면 되돌리지 않는지
 * - 불러온 빌드의 직업·전직·찍은 노드가 그대로 화면 자료로 옮겨지는지
 */
class TreeScreenTest {

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


    @Test
    fun treeTabDrawsTheTreeItselfInsteadOfShowingTheSite() {
        val layout = File("src/main/res/layout/pane_tree.xml").readText()
        println("[TEST] 트리 화면에 TreeCanvasView=${layout.contains("TreeCanvasView")} · WebView=${layout.contains("WebView")}")
        assertFalse("트리 화면에 웹뷰가 남아 있다", layout.contains("WebView"))
        assertTrue("트리 화면에 앱이 직접 그리는 캔버스가 없다", layout.contains("com.sunuhwi.poe2dbkr.TreeCanvasView"))
        // 예전에 사이트 화면을 띄우던 코드가 남아 있으면 안 된다.
        val activity = File("src/main/java/com/sunuhwi/poe2dbkr/MainActivity.kt").readText()
        assertFalse("트리 탭이 아직 poe2db 주소를 연다", activity.contains("passive-skill-tree/\""))
        assertFalse("트리 웹뷰 코드가 남아 있다", activity.contains("treePane.treeWebView"))
    }

    @Test
    fun everyNodeHasAnIconInTheSprite() {
        val without = tree.nodes.values.count { it.icon == null }
        val withIcon = tree.nodes.size - without
        println("[TEST] 아이콘 있는 노드 $withIcon / ${tree.nodes.size} (자료에 아이콘이 없는 연결 노드 $without)")
        assertEquals("아이콘 묶음 파일 이름이 바뀌었다", "tree_icons.webp", tree.iconAsset)
        assertTrue("아이콘 묶음 파일이 없다", File(assets, tree.iconAsset).isFile)
        // 자료에 그림이 없는 연결용 노드(244)만 빠지고, 그림이 있는 노드는 모두 아이콘이 있어야 한다.
        assertTrue("아이콘이 빠진 노드가 너무 많다: $without", without <= 250)
        assertTrue("아이콘이 있는 노드가 너무 적다: $withIcon", withIcon > 4800)
        tree.nodes.values.take(50).forEach { node ->
            val rect = node.icon ?: return@forEach
            assertTrue("아이콘 자리가 이상하다: ${rect.toList()}", rect.size == 4 && rect[2] > 0 && rect[3] > 0)
        }
    }

    @Test
    fun atlasTreeCarriesNodesEdgesAndPoints() {
        println("[TEST] 아틀라스 노드 ${atlas.nodes.size} · 연결선 ${atlas.edges.size / 2} · 포인트 ${atlas.totalPoints}")
        assertTrue("아틀라스 노드가 너무 적다", atlas.nodes.size > 500)
        assertTrue("아틀라스 연결선이 너무 적다", atlas.edges.size > 500)
        assertEquals("아틀라스 총 포인트", 123, atlas.totalPoints)
        assertTrue("아틀라스 아이콘 묶음이 없다", File(assets, atlas.iconAsset).isFile)
        val bounds = atlas.bounds
        atlas.nodes.values.forEach { node ->
            assertTrue("자리가 없는 아틀라스 노드 ${node.id}", node.x != 0f || node.y != 0f)
            assertTrue("범위를 벗어난 아틀라스 노드 ${node.id}", node.x in bounds[0]..bounds[2])
            assertTrue("범위를 벗어난 아틀라스 노드 ${node.id}", node.y in bounds[1]..bounds[3])
        }
        // 아틀라스 이름·설명이 한글로 들어와 있어야 한다.
        val korean = atlas.nodes.values.count { it.stats.any { ch -> ch in '가'..'힣' } }
        assertTrue("아틀라스 설명이 한글이 아니다", korean > 400)
    }

    @Test
    fun classArtIsBundledForEveryClass() {
        val arts = ClassArts.parse(File(assets, "class_art.json").readText())
        println("[TEST] 직업 일러스트 ${arts.arts.keys}")
        assertTrue("직업 일러스트 파일이 없다", File(assets, arts.file).isFile)
        assertEquals("직업 수", 12, arts.arts.size)
        assertNotNull("몽크 일러스트를 찾지 못했다", arts.of("Monk"))
        assertNotNull("한글 이름으로도 찾아야 한다", arts.of("monk"))
    }

    @Test
    fun sceneKeepsNamesStatsAndClassStart() {
        val classIndex = tree.classIndex(realBuild.className, realBuild.treeClassId)
        assertEquals(10, classIndex)
        val scene = TreeScene.of(tree, classIndex, 1)
        assertEquals(tree.nodes.size, scene.size)
        val start = scene.geometry.indexOf(tree.startNode(classIndex, 0))
        assertEquals("직업 시작 노드 자리", start, scene.classStart)
        assertEquals("직업 이름", "몽크", scene.className)
        assertTrue("전직 시작 노드가 없다", scene.ascendStart >= 0)
        val named = (0 until scene.size).count { scene.names[it].isNotBlank() }
        println("[TEST] 이름 있는 노드 $named · 직업 ${scene.className} · 전직 ${scene.ascendName}")
        assertTrue("노드 이름이 비었다", named > 4000)
    }

    // ------------------------------------------------------------- 찍기 규칙

    /** 1-2-3-4 사슬과 1에서 갈라지는 5 (테스트용 작은 트리) */
    private fun chainGeometry(): TreeGeometry {
        val ids = intArrayOf(11, 12, 13, 14, 15)
        val xs = floatArrayOf(0f, 100f, 200f, 300f, 100f)
        val ys = floatArrayOf(0f, 0f, 0f, 0f, 100f)
        val codes = byteArrayOf('c'.code.toByte(), 0, 0, 0, 0)
        val edges = intArrayOf(0, 1, 1, 2, 2, 3, 1, 4)
        return TreeGeometry(ids, xs, ys, codes, edges, 0f, 0f, 300f, 100f)
    }

    @Test
    fun onlyConnectedNodesCanBeAllocated() {
        val alloc = TreeAllocation(chainGeometry(), classStart = 0, pointTotal = 10)
        alloc.bind(emptyList())
        assertTrue("직업 시작 노드는 찍혀 있어야 한다", alloc.isAllocated(11))
        assertEquals(TreeAllocation.Result.NOT_CONNECTED, alloc.allocate(13))
        assertEquals(TreeAllocation.Result.DONE, alloc.allocate(12))
        assertEquals(TreeAllocation.Result.DONE, alloc.allocate(13))
        assertEquals(TreeAllocation.Result.DONE, alloc.allocate(15))
        for (id in listOf(11, 12, 13, 15)) assertTrue(alloc.isAllocated(id))
        assertFalse(alloc.isAllocated(14))
        println("[TEST] 포인트 ${alloc.points()}")
        assertEquals(3, alloc.points().allocated)
    }

    @Test
    fun deallocateNeverBreaksThePath() {
        val alloc = TreeAllocation(chainGeometry(), classStart = 0, pointTotal = 10)
        alloc.bind(listOf(12, 13, 14))
        assertTrue(alloc.isAllocated(14))
        // 가운데를 빼면 14가 끊긴다.
        assertEquals(TreeAllocation.Result.BREAKS_PATH, alloc.deallocate(13))
        assertTrue("끊긴 채로 빠지면 안 된다", alloc.isAllocated(13))
        // 끝은 뺄 수 있다.
        assertEquals(TreeAllocation.Result.DONE, alloc.deallocate(14))
        assertEquals(TreeAllocation.Result.DONE, alloc.deallocate(13))
        // 시작 노드는 뺄 수 없다.
        assertEquals(TreeAllocation.Result.START_NODE, alloc.deallocate(11))
    }

    @Test
    fun pointLimitStopsAllocation() {
        val alloc = TreeAllocation(chainGeometry(), classStart = 0, pointTotal = 1)
        alloc.bind(emptyList())
        assertEquals(TreeAllocation.Result.DONE, alloc.allocate(12))
        assertEquals(TreeAllocation.Result.NO_POINTS, alloc.allocate(13))
    }

    @Test
    fun buildNodesRoundTrip() {
        val scene = TreeScene.of(tree, 10, 1)
        val geo = scene.geometry
        val alloc = TreeAllocation(
            geometry = geo,
            ascendancy = scene.ascendancy,
            classStart = scene.classStart,
            ascendStart = scene.ascendStart,
            pointTotal = 120,
        )
        alloc.bind(realBuild.treeNodeIds)
        println("[TEST] 찍힌 노드 ${alloc.allocatedIds().size} · ${alloc.points()}")
        assertEquals(realBuild.treeNodeIds.size, alloc.allocatedIds().size)
        assertEquals("전직 노드 8개를 세야 한다", 8, alloc.points().ascendancy)
        // 레벨 90 빌드는 패시브를 114개 쓴다 (레벨-1 + 퀘스트 25).
        assertEquals(114, alloc.points().allocated)
        alloc.markBound()
        assertFalse("바꾼 것이 없는데 변경으로 나온다", alloc.hasChanges)
        // 아직 안 찍혔지만 찍힌 노드와 이어진 자리를 하나 찾아 찍어 본다.
        val next = (0 until geo.size).firstOrNull { index ->
            !alloc.allocated[index] && geo.xs[index] != 0f &&
                alloc.allocate(geo.ids[index]) == TreeAllocation.Result.DONE
        }
        assertNotNull("이어진 빈 자리를 찾지 못했다", next)
        assertTrue("찍기 변경을 못 알아챘다", alloc.hasChanges)
        assertEquals(115, alloc.points().allocated)
        alloc.resetToBound()
        assertEquals(realBuild.treeNodeIds.size, alloc.allocatedIds().size)
        assertFalse("되돌린 뒤에도 변경으로 나온다", alloc.hasChanges)
    }

    @Test
    fun atlasAllocationStartsFromFirstTap() {
        val geo = atlas.geometry
        assertTrue("아틀라스 노드가 너무 적다", geo.size > 500)
        val alloc = TreeAllocation(geo, pointTotal = atlas.totalPoints)
        alloc.bind(emptyList())
        val first = geo.ids[0]
        assertEquals("아틀라스는 첫 노드를 찍을 수 있어야 한다", TreeAllocation.Result.DONE, alloc.allocate(first))
        assertEquals(1, alloc.points().allocated)
    }
}

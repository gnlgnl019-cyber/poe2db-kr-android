package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PoB <Tree><Spec nodes="..."> 를 읽어 poe2db 패시브 트리 주소로 옮기는지 확인한다.
 * 주소는 poe2db 트리 뷰어가 그대로 읽는 형식이므로, 만든 주소를 다시 풀어 노드가 남아 있는지 본다.
 */
class Poe2TreeTest {

    private val tree: TreeDict by lazy {
        val file = File("src/main/assets/poe2_tree_kr.json")
        assertTrue("트리 자료가 없다: ${file.absolutePath}", file.isFile)
        TreeDict.parse(file.readText())
    }

    private val specXml = """
        <PathOfBuilding>
          <Build level="96" className="Ranger" ascendClassName="Deadeye"/>
          <Tree activeSpec="1">
            <Spec title="라이트닝 애로우" nodesCount="4" classId="2" ascendClassId="1"
                  masteryEffects="{4111,2001}"
                  nodes="18684,17894,307,42761"/>
          </Tree>
        </PathOfBuilding>
    """.trimIndent()

    @Test
    fun specNodesAreParsed() {
        val build = Pob.parseXml(specXml)
        println("[TEST] nodes=${build.treeNodeIds} class=${build.treeClassId} asc=${build.treeAscendClassId} mastery=${build.treeMastery}")
        assertEquals(listOf(18684, 17894, 307, 42761), build.treeNodeIds)
        assertEquals(4, build.treeNodes)
        assertEquals(2, build.treeClassId)
        assertEquals(1, build.treeAscendClassId)
        assertEquals(2001, build.treeMastery[4111])
    }

    @Test
    fun classAndAscendancyResolveToPoe2dbIndexes() {
        assertEquals(2, tree.classIndex("Ranger"))
        assertEquals(1, tree.ascendancyIndex(2, "Deadeye", 0))
        assertEquals(3, tree.ascendancyIndex(2, "", 3))
        assertEquals(0, tree.ascendancyIndex(2, "Unknown", 0))
        assertEquals("데드아이", tree.classes[2].ascendancies[0].second)
    }

    @Test
    fun treeUrlKeepsAllocatedNodes() {
        val build = Pob.parseXml(specXml)
        val url = Poe2Tree.url(build, tree)
        println("[TEST] url len=${url.length} ${url.take(72)}…")
        assertTrue("트리 주소가 아니다: $url", url.startsWith("https://poe2db.tw/kr/passive-skill-tree/"))
        val state = tree.decode(url)
        assertNotNull("주소를 다시 풀지 못했다", state)
        assertEquals(7, state!!.version)
        assertEquals(2, state.classIndex)
        assertEquals(1, state.ascendancyIndex)
        // 전직 시작 노드(42761)는 poe2db가 직업·전직에서 자동으로 켜므로 목록에서 빠진다.
        assertEquals(listOf(18684, 17894, 307), state.nodes)
    }

    @Test
    fun summaryListsKeystoneAndNotablesInKorean() {
        val build = Pob.parseXml(specXml)
        val summary = Poe2Tree.summarize(build, tree)
        assertNotNull(summary)
        println("[TEST] ${summary!!.classKo} · ${summary.ascendKo} allocated=${summary.allocated} " +
            "keystone=${summary.keystones.map { it.displayName }} notables=${summary.notables.map { it.displayName }}")
        assertEquals("레인저", summary.classKo)
        assertEquals("데드아이", summary.ascendKo)
        assertEquals(4, summary.allocated)
        assertEquals(listOf("불의 화신"), summary.keystones.map { it.displayName })
        assertTrue(summary.notables.any { it.displayName == "그녀의 마지막 저항" })
        assertTrue(summary.keystones.first().stats.any { it.code > 0x7F })
    }

    @Test
    fun buildWithoutNodesHasNoTreeUrl() {
        val build = Pob.parseXml("<PathOfBuilding><Build className=\"Ranger\"/></PathOfBuilding>")
        assertEquals("", Poe2Tree.url(build, tree))
        assertTrue(Poe2Tree.summarize(build, tree) == null)
    }

    @Test
    fun treeRoundTripThroughEncodeRestoresNodes() {
        val build = Pob.parseXml(specXml)
        val once = Poe2Tree.url(build, tree)
        val state = tree.decode(once)!!
        val again = tree.treeUrl(state.nodes, state.classIndex, state.ascendancyIndex)
        assertEquals(once, again)
    }
}

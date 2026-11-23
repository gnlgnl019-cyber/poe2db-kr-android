package com.sunuhwi.poe2dbkr

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** poe2db 패시브 트리의 직업·전직 한 줄 */
data class TreeClass(
    val nameEn: String,
    val nameKo: String,
    val ascendancies: List<Pair<String, String>>,
)

/** 요약에 올리는 패시브 노드 하나 */
data class TreeNode(
    val id: Int,
    val nameEn: String,
    val nameKo: String,
    val stats: String,
    val kind: Char,
    val ascendancy: String,
    /** 트리에서 그려질 자리 (poe2db 자료의 x·y) */
    val x: Float = 0f,
    val y: Float = 0f,
    /** 이 노드가 부여하는 스킬 이름 (전직 주요 노드) */
    val grantedName: String = "",
    /** 부여 스킬 설명 */
    val grantedText: String = "",
    /** 부여 스킬의 세부 수치 (poe2db 스킬 창 기준) */
    val grantedStats: String = "",
) {
    val displayName: String get() = nameKo.ifBlank { nameEn }
    val isKeystone: Boolean get() = kind == 'k'
    val isNotable: Boolean get() = kind == 'n'
    val isMastery: Boolean get() = kind == 'm'
    val isAscendancy: Boolean get() = ascendancy.isNotBlank()
    val isClassStart: Boolean get() = kind == 'c'
}

/**
 * poe2db 패시브 트리 자료 (assets/poe2_tree_kr.json, scripts/fetch_poe2_tree.py가 만든다).
 *
 * 트리 화면 주소는 poe2db의 트리 뷰어가 읽는 형식 그대로 만든다.
 * 주소 마지막 조각은 [판, 직업 번호, 전직 번호, 노드 수, (노드 번호, 표시 정보)…]를
 * 큰 수 먼저 쓰는 바이트열로 놓고 base64url로 바꾼 것이다. 이 주소로 들어가면
 * poe2db가 그 노드들을 찍힌 상태(황금색)로 그린다.
 */
class TreeDict(
    val classes: List<TreeClass> = emptyList(),
    val nodes: Map<Int, TreeNode> = emptyMap(),
    /** 연결선 [가, 나, 가, 나, …] — 노드 번호 쌍 */
    val edges: List<Int> = emptyList(),
    /** 직업 번호 → 직업 시작 노드 */
    val classStarts: Map<Int, Int> = emptyMap(),
    /** 직업 번호 × 100 + 전직 번호 → 전직 시작 노드 */
    val ascStarts: Map<Int, Int> = emptyMap(),
    /** 트리 전체 범위 [minX, minY, maxX, maxY] */
    val bounds: FloatArray = FloatArray(0),
) {

    /** 화면에 그릴 때 쓰는 배열 묶음 (한 번만 만든다) */
    val geometry: TreeGeometry by lazy { TreeGeometry.of(this) }

    /** 직업·전직의 시작 노드 번호. 시작 지점을 화면 가운데로 잡을 때 쓴다. */
    fun startNode(classIndex: Int, ascendancyIndex: Int): Int {
        if (ascendancyIndex > 0) {
            ascStarts[classIndex * 100 + ascendancyIndex]?.let { return it }
        }
        return classStarts[classIndex] ?: -1
    }

    fun isEmpty(): Boolean = classes.isEmpty()

    /** PoB의 직업 이름(없으면 번호)으로 poe2db 직업 번호를 찾는다. */
    fun classIndex(className: String, classId: Int = -1): Int {
        val key = className.trim().lowercase()
        if (key.isNotEmpty()) {
            classes.forEachIndexed { index, cls ->
                if (cls.nameEn.lowercase() == key || cls.nameKo == className.trim()) return index
            }
        }
        if (classId in classes.indices) return classId
        return -1
    }

    /** 직업 안에서의 전직 번호(1부터). PoB의 ascendClassId·전직 이름을 차례로 본다. */
    fun ascendancyIndex(classIndex: Int, ascendName: String, ascendClassId: Int): Int {
        val cls = classes.getOrNull(classIndex) ?: return 0
        val key = ascendName.trim().lowercase()
        if (key.isNotEmpty()) {
            cls.ascendancies.forEachIndexed { index, (en, ko) ->
                if (en.lowercase() == key || ko == ascendName.trim()) return index + 1
            }
        }
        if (ascendClassId in 1..cls.ascendancies.size) return ascendClassId
        return 0
    }

    fun node(id: Int): TreeNode? = nodes[id]

    /** 찍은 노드들을 poe2db 트리 주소로 만든다. 직업을 모르면 빈 문자열. */
    fun treeUrl(nodesInOrder: List<Int>, classIndex: Int, ascendancyIndex: Int): String {
        if (classIndex < 0 || nodesInOrder.isEmpty()) return ""
        val kept = nodesInOrder.filter { id ->
            val node = nodes[id]
            node == null || (!node.isClassStart && node.kind != 'a')
        }
        val buf = ByteArrayBuilder(8 + kept.size * 4)
        buf.int32(VERSION)
        buf.int8(classIndex)
        buf.int8(ascendancyIndex and 3)
        buf.int16(kept.size)
        kept.forEach { id ->
            buf.int16(id)
            buf.int16(0)
        }
        return BASE + base64Url(buf.toByteArray())
    }

    /** 같은 노드 목록으로 만든 주소인지 확인할 때 쓴다 (테스트·디버그용). */
    fun decode(url: String): TreeState? {
        val tail = url.substringAfterLast('/')
        if (tail.isEmpty()) return null
        val bytes = decodeBase64Url(tail) ?: return null
        if (bytes.size < 8) return null
        val reader = ByteReader(bytes)
        val version = reader.int32()
        if (version <= 0) return null
        val classIndex = reader.int8()
        val flags = reader.int8()
        val count = reader.int16()
        val ids = ArrayList<Int>(count)
        repeat(count) {
            ids.add(reader.int16())
            reader.int16()
        }
        return TreeState(version, classIndex, flags and 3, ids)
    }

    companion object {
        val EMPTY = TreeDict()

        private const val BASE = "https://poe2db.tw/kr/passive-skill-tree/"
        private const val VERSION = 7

        fun parse(raw: String): TreeDict {
            val root = JSONObject(raw)
            val classes = ArrayList<TreeClass>()
            val classArray = root.optJSONArray("classes")
            for (i in 0 until (classArray?.length() ?: 0)) {
                val row = classArray?.optJSONArray(i) ?: continue
                val ascArr = row.optJSONArray(2)
                val asc = ArrayList<Pair<String, String>>()
                for (j in 0 until (ascArr?.length() ?: 0)) {
                    val pair = ascArr?.optJSONArray(j) ?: continue
                    asc.add(pair.optString(0) to pair.optString(1))
                }
                classes.add(TreeClass(row.optString(0), row.optString(1), asc))
            }
            val nodes = HashMap<Int, TreeNode>()
            val nodeArray = root.optJSONArray("nodes")
            for (i in 0 until (nodeArray?.length() ?: 0)) {
                val row = nodeArray?.optJSONArray(i) ?: continue
                val id = row.optInt(0, 0)
                if (id <= 0) continue
                nodes[id] = TreeNode(
                    id = id,
                    nameEn = row.optString(1),
                    nameKo = row.optString(2),
                    stats = row.optString(3),
                    kind = row.optString(4).firstOrNull() ?: 's',
                    ascendancy = row.optString(5),
                    x = row.optDouble(6, 0.0).toFloat(),
                    y = row.optDouble(7, 0.0).toFloat(),
                    grantedName = row.optString(8),
                    grantedText = row.optString(9),
                    grantedStats = row.optString(10),
                )
            }
            val edges = ArrayList<Int>()
            root.optJSONArray("edges")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val pair = arr.optJSONArray(i) ?: continue
                    edges.add(pair.optInt(0, 0))
                    edges.add(pair.optInt(1, 0))
                }
            }
            val classStarts = HashMap<Int, Int>()
            root.optJSONArray("classStarts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val pair = arr.optJSONArray(i) ?: continue
                    classStarts[pair.optInt(0, -1)] = pair.optInt(1, 0)
                }
            }
            val ascStarts = HashMap<Int, Int>()
            root.optJSONArray("ascStarts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONArray(i) ?: continue
                    ascStarts[row.optInt(0, -1) * 100 + row.optInt(1, 0)] = row.optInt(2, 0)
                }
            }
            val boundsRow = root.optJSONArray("bounds")
            val bounds = if ((boundsRow?.length() ?: 0) >= 4) {
                floatArrayOf(
                    boundsRow!!.optDouble(0, 0.0).toFloat(),
                    boundsRow.optDouble(1, 0.0).toFloat(),
                    boundsRow.optDouble(2, 0.0).toFloat(),
                    boundsRow.optDouble(3, 0.0).toFloat(),
                )
            } else {
                FloatArray(0)
            }
            return TreeDict(classes, nodes, edges, classStarts, ascStarts, bounds)
        }

        /** 고유 이름·노드 이름을 바이트열로 (큰 수 먼저). */
        private class ByteArrayBuilder(size: Int) {
            private val out = ArrayList<Byte>(size)

            fun int8(value: Int) = apply { out.add((value and 0xFF).toByte()) }

            fun int16(value: Int) = apply {
                out.add(((value shr 8) and 0xFF).toByte())
                out.add((value and 0xFF).toByte())
            }

            fun int32(value: Int) = apply {
                out.add(((value shr 24) and 0xFF).toByte())
                out.add(((value shr 16) and 0xFF).toByte())
                out.add(((value shr 8) and 0xFF).toByte())
                out.add((value and 0xFF).toByte())
            }

            fun toByteArray(): ByteArray = out.toByteArray()
        }

        private class ByteReader(val data: ByteArray) {
            private var pos = 0

            fun int8(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else 0

            fun int16(): Int {
                val high = int8()
                return (high shl 8) or int8()
            }

            fun int32(): Int {
                var value = 0
                repeat(4) { value = (value shl 8) or int8() }
                return value
            }
        }

        /** 표준 base64(패딩 포함)를 주소에 쓸 수 있는 글자로 바꾼다. */
        internal fun base64Url(bytes: ByteArray): String {
            val standard = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val sb = StringBuilder((bytes.size + 2) / 3 * 4)
            var i = 0
            while (i + 2 < bytes.size) {
                val chunk = ((bytes[i].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                    (bytes[i + 2].toInt() and 0xFF)
                sb.append(standard[(chunk shr 18) and 63])
                sb.append(standard[(chunk shr 12) and 63])
                sb.append(standard[(chunk shr 6) and 63])
                sb.append(standard[chunk and 63])
                i += 3
            }
            when (bytes.size - i) {
                1 -> {
                    val chunk = (bytes[i].toInt() and 0xFF) shl 16
                    sb.append(standard[(chunk shr 18) and 63])
                    sb.append(standard[(chunk shr 12) and 63])
                    sb.append("==")
                }
                2 -> {
                    val chunk = ((bytes[i].toInt() and 0xFF) shl 16) or
                        ((bytes[i + 1].toInt() and 0xFF) shl 8)
                    sb.append(standard[(chunk shr 18) and 63])
                    sb.append(standard[(chunk shr 12) and 63])
                    sb.append(standard[(chunk shr 6) and 63])
                    sb.append('=')
                }
            }
            return sb.toString().replace('+', '-').replace('/', '_')
        }

        internal fun decodeBase64Url(text: String): ByteArray? {
            val standard = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val clean = text.replace('-', '+').replace('_', '/').trimEnd('=')
            if (clean.isEmpty() || clean.any { it !in standard }) return null
            val out = ArrayList<Byte>(clean.length * 3 / 4)
            var buffer = 0
            var bits = 0
            for (ch in clean) {
                buffer = (buffer shl 6) or standard.indexOf(ch)
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    out.add(((buffer shr bits) and 0xFF).toByte())
                }
            }
            return out.toByteArray()
        }
    }
}

/** 트리 주소에 담긴 값 (판·직업·전직·노드) */
data class TreeState(val version: Int, val classIndex: Int, val ascendancyIndex: Int, val nodes: List<Int>)

/** 패시브 탭에 보여 줄 요약 */
data class TreeSummary(
    val classKo: String,
    val ascendKo: String,
    val allocated: Int,
    val ascendancyNodes: List<TreeNode>,
    val keystones: List<TreeNode>,
    /** 목록에 올린 주요 패시브 (많으면 일부만) */
    val notables: List<TreeNode>,
    val masteries: List<TreeNode>,
    /** 실제 주요 패시브 수 */
    val notableCount: Int,
)

/** assets의 트리 자료를 한 번만 읽어 둔다. */
object Poe2Tree {

    private const val ASSET = "poe2_tree_kr.json"
    private val cached = AtomicReference<TreeDict?>(null)

    fun of(context: Context): TreeDict {
        cached.get()?.let { return it }
        val loaded = runCatching {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            TreeDict.parse(raw)
        }.getOrElse { TreeDict.EMPTY }
        cached.compareAndSet(null, loaded)
        return cached.get() ?: loaded
    }

    fun ofRaw(raw: String): TreeDict = TreeDict.parse(raw)

    /**
     * 빌드에 담긴 노드로 트리 화면 주소를 만든다.
     * 노드 정보가 없으면 빈 문자열(호출한 쪽이 기본 트리를 연다).
     */
    fun url(build: PobBuild, tree: TreeDict): String {
        if (tree.isEmpty() || build.treeNodeIds.isEmpty()) return ""
        val classIndex = tree.classIndex(build.className, build.treeClassId)
        if (classIndex < 0) return ""
        val ascend = tree.ascendancyIndex(classIndex, build.ascendClassName, build.treeAscendClassId)
        return tree.treeUrl(build.treeNodeIds, classIndex, ascend)
    }

    /** 패시브 탭에 띄울 한글 요약. 찍은 노드를 종류별로 나눈다. */
    fun summarize(build: PobBuild, tree: TreeDict, notableLimit: Int = 14): TreeSummary? {
        if (build.treeNodeIds.isEmpty()) return null
        val classIndex = tree.classIndex(build.className, build.treeClassId)
        val cls = tree.classes.getOrNull(classIndex)
        val ascendIndex = if (classIndex >= 0) {
            tree.ascendancyIndex(classIndex, build.ascendClassName, build.treeAscendClassId)
        } else 0
        val ascendKo = cls?.ascendancies?.getOrNull(ascendIndex - 1)?.second.orEmpty()
        val ascendancyNodes = ArrayList<TreeNode>()
        val keystones = ArrayList<TreeNode>()
        val notables = ArrayList<TreeNode>()
        val masteries = ArrayList<TreeNode>()
        build.treeNodeIds.forEach { id ->
            val node = tree.node(id) ?: return@forEach
            when {
                node.isKeystone -> keystones.add(node)
                node.isMastery -> masteries.add(node)
                node.isAscendancy -> ascendancyNodes.add(node)
                node.isNotable -> notables.add(node)
            }
        }
        val classKo = cls?.nameKo.orEmpty().ifBlank { build.className }
        val distinctNotables = notables.distinctBy { it.id }
        return TreeSummary(
            classKo = classKo,
            ascendKo = ascendKo.ifBlank { build.ascendClassName },
            allocated = build.treeNodeIds.size,
            ascendancyNodes = ascendancyNodes.distinctBy { it.id },
            keystones = keystones.distinctBy { it.id },
            notables = if (notableLimit > 0) distinctNotables.take(notableLimit) else distinctNotables,
            masteries = masteries.distinctBy { it.id },
            notableCount = distinctNotables.size,
        )
    }
}

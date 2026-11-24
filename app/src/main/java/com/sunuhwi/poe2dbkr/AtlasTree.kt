package com.sunuhwi.poe2dbkr

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** 아틀라스 트리 노드 하나 */
data class AtlasNode(
    val id: Int,
    /** 한글 이름 (일반 노드는 비어 있기도 하다) */
    val name: String,
    val stats: String,
    val kind: Char,
    val x: Float,
    val y: Float,
    /** 아이콘 스프라이트 안의 자리 [x, y, w, h] */
    val icon: IntArray?,
) {
    val isKeystone: Boolean get() = kind == 'k'
    val isNotable: Boolean get() = kind == 'n'
    val isMastery: Boolean get() = kind == 'm'
}

/**
 * poe2db 아틀라스 패시브 트리 (assets/poe2_atlas_kr.json, scripts/fetch_poe2_atlas.py가 만든다).
 *
 * 패시브 트리와 같은 방식으로 그린다. 직업·전직이 없고 총 포인트만 다르다.
 */
class AtlasDict(
    val nodes: Map<Int, AtlasNode> = emptyMap(),
    /** 연결선 [가, 나, 가, 나, …] */
    val edges: List<Int> = emptyList(),
    val bounds: FloatArray = FloatArray(0),
    /** 아틀라스에서 쓸 수 있는 총 포인트 */
    val totalPoints: Int = 0,
    val iconAsset: String = ATLAS_ICONS,
) {

    val geometry: TreeGeometry by lazy { TreeGeometry.ofAtlas(this) }

    fun isEmpty(): Boolean = nodes.isEmpty()

    fun node(id: Int): AtlasNode? = nodes[id]

    companion object {
        const val ATLAS_ICONS = "atlas_icons.webp"
        val EMPTY = AtlasDict()

        fun parse(raw: String): AtlasDict {
            val root = JSONObject(raw)
            val nodes = HashMap<Int, AtlasNode>()
            val array = root.optJSONArray("nodes")
            for (i in 0 until (array?.length() ?: 0)) {
                val row = array?.optJSONArray(i) ?: continue
                val id = row.optInt(0, 0)
                if (id == 0) continue
                val icon = row.optJSONArray(6)?.let { rect ->
                    if (rect.length() < 4) null
                    else intArrayOf(rect.optInt(0, 0), rect.optInt(1, 0), rect.optInt(2, 0), rect.optInt(3, 0))
                }
                nodes[id] = AtlasNode(
                    id = id,
                    name = row.optString(1),
                    stats = row.optString(2),
                    kind = row.optString(3).firstOrNull() ?: 's',
                    x = row.optDouble(4, 0.0).toFloat(),
                    y = row.optDouble(5, 0.0).toFloat(),
                    icon = icon,
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
            val total = root.optJSONObject("points")?.optInt("total", 0) ?: 0
            return AtlasDict(nodes, edges, bounds, total, root.optString("icons", ATLAS_ICONS))
        }
    }
}

/** assets의 아틀라스 트리를 한 번만 읽어 둔다 */
object TreeAtlas {

    private const val ASSET = "poe2_atlas_kr.json"
    private val cached = AtomicReference<AtlasDict?>(null)

    fun of(context: Context): AtlasDict {
        cached.get()?.let { return it }
        val loaded = runCatching {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            AtlasDict.parse(raw)
        }.getOrElse { AtlasDict.EMPTY }
        cached.compareAndSet(null, loaded)
        return cached.get() ?: loaded
    }

    fun ofRaw(raw: String): AtlasDict = AtlasDict.parse(raw)
}

package com.sunuhwi.poe2dbkr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * 노드 아이콘을 한 장에 모아 둔 그림(스프라이트).
 *
 * 트리에는 노드가 5천 개가 넘는다. 아이콘을 낱개 파일로 열면 트리를 밀 때마다
 * 파일을 찾느라 끊기므로, poe2db가 주는 노드별 아이콘을 한 장의 webp로
 * 묶어 두고 노드를 그릴 때 그 부분만 잘라 쓴다.
 */
class SpriteSheet private constructor(
    val bitmap: Bitmap,
    private val drawPaint: Paint,
) {

    private val src = Rect()
    private val dst = RectF()

    /** 스프라이트의 한 칸을 주어진 원 안에 그린다 */
    fun draw(
        canvas: Canvas,
        rect: IntArray,
        cx: Float,
        cy: Float,
        radius: Float,
        alpha: Int = 255,
    ) {
        if (rect.size < 4) return
        src.set(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3])
        dst.set(cx - radius, cy - radius, cx + radius, cy + radius)
        drawPaint.alpha = alpha.coerceIn(0, 255)
        canvas.drawBitmap(bitmap, src, dst, drawPaint)
    }

    fun recycle() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    companion object {
        private val cache = HashMap<String, SpriteSheet>()

        /** assets 안의 스프라이트를 한 번만 연다 */
        fun of(context: Context, asset: String): SpriteSheet? {
            cache[asset]?.let { return it }
            val loaded = runCatching {
                context.assets.open(asset).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }?.let { bitmap ->
                    SpriteSheet(bitmap, Paint(Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true })
                }
            }.getOrNull()
            if (loaded != null) cache[asset] = loaded
            return loaded
        }

        /** JVM 테스트에서 그림 없이 쓰는 빈 스프라이트 */
        fun empty(): SpriteSheet = SpriteSheet(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), Paint())
    }
}

/** 직업 일러스트 한 칸 — 트리 한가운데의 메달 */
data class ClassArt(val name: String, val rect: IntArray)

/**
 * 직업 일러스트 모음 (assets/class_art.webp + class_art.json).
 * 트리 중앙 메달에 그 직업의 그림을 넣는다.
 */
class ClassArts(
    val file: String = "",
    val arts: Map<String, IntArray> = emptyMap(),
) {
    fun of(className: String): IntArray? {
        if (className.isBlank()) return null
        arts[className]?.let { return it }
        val key = className.trim().lowercase()
        return arts.entries.firstOrNull { it.key.lowercase() == key }?.value
    }

    companion object {
        val EMPTY = ClassArts()
        private val cached = AtomicReference<ClassArts?>(null)

        fun parse(raw: String): ClassArts {
            val root = JSONObject(raw)
            val file = root.optString("file")
            val rows = root.optJSONObject("classes") ?: return ClassArts(file)
            val out = HashMap<String, IntArray>()
            rows.keys().forEach { name ->
                val row = rows.optJSONArray(name) ?: return@forEach
                if (row.length() < 4) return@forEach
                out[name] = intArrayOf(
                    row.optInt(0, 0), row.optInt(1, 0), row.optInt(2, 0), row.optInt(3, 0),
                )
            }
            return ClassArts(file, out)
        }

        fun of(context: Context): ClassArts {
            cached.get()?.let { return it }
            val loaded = runCatching {
                val raw = context.assets.open("class_art.json").bufferedReader().use { it.readText() }
                parse(raw)
            }.getOrElse { EMPTY }
            cached.compareAndSet(null, loaded)
            return cached.get() ?: loaded
        }
    }
}

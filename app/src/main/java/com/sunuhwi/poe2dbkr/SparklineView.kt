package com.sunuhwi.poe2dbkr

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * 7일 가격 추이 미니 그래프. poe.ninja의 sparkLine 배열(중간 결측치는 null)을 그대로 받는다.
 */
class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var values: List<Double?> = emptyList()
    private var positive: Boolean = true

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    fun setData(data: List<Double?>, up: Boolean) {
        values = data
        positive = up
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pts = values
        if (pts.size < 2) {
            fill.color = 0x22FFFFFF
            canvas.drawRect(0f, h / 2f - 0.6f, w, h / 2f + 0.6f, fill)
            return
        }

        var minV = Double.MAX_VALUE
        var maxV = -Double.MAX_VALUE
        for (v in pts) {
            if (v == null) continue
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }
        if (minV == Double.MAX_VALUE) minV = 0.0
        if (maxV - minV < 1e-9) {
            maxV = minV + 1.0
            minV -= 1.0
        }

        val padTop = h * 0.12f
        val padBottom = h * 0.16f
        fun xAt(i: Int): Float = w * i / (pts.size - 1).toFloat()
        fun yAt(v: Double): Float {
            val f = ((v - minV) / (maxV - minV)).toFloat()
            return h - padBottom - f * (h - padTop - padBottom)
        }

        val color = if (positive) 0xFF6FD08C.toInt() else 0xFFE2685C.toInt()

        // 영역 채우기
        path.reset()
        var started = false
        var lastX = 0f
        var firstX = 0f
        var lastY = h - padBottom
        for (i in pts.indices) {
            val v = pts[i] ?: continue
            val x = xAt(i)
            val y = yAt(v)
            if (!started) {
                path.moveTo(x, y)
                firstX = x
                started = true
            } else {
                path.lineTo(x, y)
            }
            lastX = x
            lastY = y
        }
        if (!started) return

        val linePath = Path(path)
        path.lineTo(lastX, h - padBottom)
        path.lineTo(firstX, h - padBottom)
        path.close()
        fill.shader = LinearGradient(
            0f, padTop, 0f, h,
            intArrayOf(alpha(color, 90), alpha(color, 8)),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, fill)
        fill.shader = null

        line.color = color
        canvas.drawPath(linePath, line)

        dot.color = color
        canvas.drawCircle(lastX, lastY, 3.2f * resources.displayMetrics.density, dot)

        // 기준선
        fill.color = 0x14FFFFFF
        canvas.drawRect(0f, h - padBottom, w, h - padBottom + 1f, fill)
        Unit
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            (72 * resources.displayMetrics.density).toInt()
        }
        setMeasuredDimension(max(w, 1), max(h, 1))
    }

    private fun alpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or ((a.coerceIn(0, 255)) shl 24)
}

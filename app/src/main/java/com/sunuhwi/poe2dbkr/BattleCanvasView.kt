package com.sunuhwi.poe2dbkr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 전장과 스틱맨을 그리는 캔버스.
 * BattleWorld의 상태만 읽어서 그리며, 프레임마다 시뮬레이션을 한 걸음 진행한다.
 */
class BattleCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var world: BattleWorld? = null
        private set

    var speed = 1f
        private set

    var onFinished: ((BattleResult) -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val chrome = Choreographer.getInstance()
    private var running = false
    private var paused = false
    private var finished = false
    private var frameScheduled = false
    private var finishedAt = 0L
    private var lastFrameNanos = 0L
    private var lastFrameAt = 0L

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val textLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val rect = RectF()
    private val path = Path()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameScheduled = false
            if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
            var dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000.0
            lastFrameNanos = frameTimeNanos
            dt = dt.coerceIn(0.0, 0.05)
            lastFrameAt = System.currentTimeMillis()
            val w = world
            if (w != null && !paused) {
                w.update(dt * speed)
                if (w.state != BattleWorld.State.RUNNING && !finished) {
                    finished = true
                    finishedAt = System.currentTimeMillis()
                    onFinished?.invoke(w.result())
                }
            }
            invalidate()
            val done = finished && System.currentTimeMillis() - finishedAt > 2600
            if (running && !paused && !done) schedule() else if (done) running = false
        }
    }

    /**
     * 프레임 콜백이 한 번이라도 끊기면(탭 전환, 화면 꺼짐, 뷰 재부착 등) 루프가 영영 멈춘 채로
     * 남을 수 있어서, 마지막 프레임 뒤로 오래 조용하면 스스로 다시 걸어 준다.
     */
    private val watchdog = Runnable {
        if (running && !paused && !finished) {
            val idle = System.currentTimeMillis() - lastFrameAt
            if (idle > 400L) {
                frameScheduled = false
                lastFrameNanos = 0L
                invalidate()
                schedule()
            }
        }
    }

    private fun schedule() {
        if (frameScheduled) return
        frameScheduled = true
        chrome.postFrameCallback(frameCallback)
        removeCallbacks(watchdog)
        postDelayed(watchdog, 650L)
    }

    fun attach(world: BattleWorld) {
        this.world = world
        finished = false
        paused = false
        lastFrameNanos = 0L
        lastFrameAt = System.currentTimeMillis()
        invalidate()
    }

    fun clear() {
        stop()
        world = null
        finished = false
        paused = false
        invalidate()
    }

    fun play() {
        running = true
        paused = false
        finished = false
        lastFrameNanos = 0L
        lastFrameAt = System.currentTimeMillis()
        invalidate()
        schedule()
    }

    fun pause() {
        paused = true
        removeCallbacks(watchdog)
        invalidate()
    }

    fun resume() {
        if (world == null) return
        paused = false
        lastFrameNanos = 0L
        running = true
        invalidate()
        schedule()
    }

    /** 탭에 다시 들어왔을 때처럼 화면만 새로 그리면 되는 경우 */
    fun refresh() {
        lastFrameNanos = 0L
        invalidate()
        if (running && !paused && !finished) schedule()
    }

    val isPaused: Boolean get() = paused

    fun setSpeed(value: Float) {
        speed = value
    }

    fun stop() {
        running = false
        chrome.removeFrameCallback(frameCallback)
        removeCallbacks(watchdog)
        frameScheduled = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (world != null && !paused) {
            running = true
            lastFrameNanos = 0L
            lastFrameAt = System.currentTimeMillis()
            schedule()
        }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            invalidate()
            if (running && !paused && !finished) {
                lastFrameNanos = 0L
                lastFrameAt = System.currentTimeMillis()
                schedule()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = world
        canvas.drawColor(0xFF0A0B10.toInt())
        drawBackdrop(canvas)
        if (w == null) {
            drawIdle(canvas)
            return
        }

        canvas.save()
        if (w.shake > 0.1f) {
            val s = w.shake
            canvas.translate(
                (sin((w.elapsed * 91f).toFloat()) * s * 0.6f) * dp,
                (cos((w.elapsed * 77f).toFloat()) * s * 0.5f) * dp,
            )
        }
        val scale = min(width / BattleWorld.W, height / BattleWorld.H)
        val ox = (width - BattleWorld.W * scale) / 2f
        val oy = (height - BattleWorld.H * scale) / 2f
        canvas.translate(ox, oy)
        canvas.scale(scale, scale)

        drawFloor(canvas, w)
        drawTelegraphs(canvas, w)
        drawGroundFx(canvas, w)
        drawCursedGrounds(canvas, w)
        drawWhirlwinds(canvas, w)
        drawEntities(canvas, w)
        drawTwisters(canvas, w)
        drawProjectiles(canvas, w)
        drawAirFx(canvas, w)
        drawParticles(canvas, w)
        drawPopups(canvas, w)
        canvas.restore()

        drawVignette(canvas, w)
        drawHud(canvas, w)
        drawBanner(canvas, w)
        if (paused) drawPaused(canvas)
        // 루프가 어떤 이유로 끊겼어도 다음 프레임은 여기서 다시 건다.
        if (running && !paused && !finished && !frameScheduled) schedule()
    }

    // -------------------------------------------------------------- 배경

    private fun drawBackdrop(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        fill.shader = RadialGradient(
            width * 0.45f, height * 0.42f, max(width, height) * 0.85f,
            intArrayOf(0xFF171B24.toInt(), 0xFF0C0E14.toInt(), 0xFF06070B.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null

        val t = System.currentTimeMillis() / 1000.0
        val mote = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 46) {
            val seedX = ((i * 137) % 999) / 999f
            val seedY = ((i * 311) % 977) / 977f
            val drift = sin((t * 0.25 + i * 0.7).toFloat()) * 14f * dp
            val x = seedX * width + drift
            val y = seedY * height + cos((t * 0.18 + i).toFloat()) * 10f * dp
            val a = (18 + (i % 5) * 8)
            mote.color = Color.argb(a, 220, 210, 180)
            mote.style = Paint.Style.FILL
            canvas.drawCircle(x, y, (0.8f + (i % 3) * 0.5f) * dp, mote)
        }
    }

    private fun drawIdle(canvas: Canvas) {
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        text.textSize = 13f * dp
        text.color = 0xFF79808C.toInt()
        canvas.drawText("빌드를 불러오면 전투를 시작할 수 있습니다", width / 2f, height / 2f, text)
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun drawFloor(canvas: Canvas, w: BattleWorld) {
        val cx = BattleWorld.W / 2f
        val cy = BattleWorld.H / 2f + 10f
        rect.set(cx - 300f, cy - 352f, cx + 300f, cy + 352f)
        path.reset()
        path.addOval(rect, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)

        fill.shader = RadialGradient(
            cx, cy, 380f,
            intArrayOf(0xFF171B22.toInt(), 0xFF101319.toInt(), 0xFF0A0C12.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, fill)
        fill.shader = null

        stroke.color = 0x12C9D4E6
        stroke.strokeWidth = 1.1f
        var gx = cx - 300f
        while (gx <= cx + 300f) {
            canvas.drawLine(gx, cy - 352f, gx, cy + 352f, stroke)
            gx += 48f
        }
        var gy = cy - 352f
        while (gy <= cy + 352f) {
            canvas.drawLine(cx - 300f, gy, cx + 300f, gy, stroke)
            gy += 48f
        }
        // 중앙 발광
        fill.shader = RadialGradient(
            cx - 90f, cy, 240f,
            intArrayOf(0x22D4AF37, 0x00000000), null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, fill)
        fill.shader = null
        canvas.restore()

        stroke.color = 0x33D4AF37
        stroke.strokeWidth = 2.4f
        canvas.drawOval(rect, stroke)
        stroke.color = 0x1AFFFFFF
        stroke.strokeWidth = 1.2f
        rect.inset(14f, 14f)
        canvas.drawOval(rect, stroke)
    }

    // -------------------------------------------------------------- 보스 예고 장판

    private fun drawTelegraphs(c: Canvas, w: BattleWorld) {
        if (w.telegraphs.isEmpty()) return
        val pulse = 0.6f + 0.4f * sin((w.elapsed * 14f).toFloat())
        for (t in w.telegraphs) {
            if (!t.active) continue
            val p = t.progress
            val danger = blend(t.element.color, 0xFFFF3B3B.toInt(), 0.55f)
            val hot = alpha(danger, (70 + 90 * p + 30 * pulse).toInt().coerceIn(0, 255))
            val edge = alpha(danger, 220)
            when (t.shape) {
                TelegraphShape.CIRCLE -> {
                    fill.color = alpha(danger, 34)
                    c.drawCircle(t.x, t.y, t.radius, fill)
                    fill.color = hot
                    c.drawCircle(t.x, t.y, t.radius * p, fill)
                    stroke.color = edge
                    stroke.strokeWidth = 2.2f
                    c.drawCircle(t.x, t.y, t.radius, stroke)
                    stroke.color = 0xFFFFFFFF.toInt()
                    stroke.strokeWidth = 1.4f
                    c.drawCircle(t.x, t.y, t.radius * p, stroke)
                }
                TelegraphShape.SAFE_CIRCLE -> {
                    // 바깥은 전부 위험, 안쪽 원만 안전
                    fill.color = alpha(danger, 30)
                    c.drawRect(0f, 0f, BattleWorld.W, BattleWorld.H, fill)
                    fill.color = alpha(danger, (40 + 55 * p).toInt().coerceIn(0, 255))
                    c.drawRect(0f, 0f, BattleWorld.W, BattleWorld.H, fill)
                    fill.color = alpha(0xFF0E1512.toInt(), 235)
                    c.drawCircle(t.x, t.y, t.radius, fill)
                    stroke.color = alpha(0xFF6CFFA8.toInt(), 235)
                    stroke.strokeWidth = 2.6f
                    c.drawCircle(t.x, t.y, t.radius, stroke)
                    stroke.strokeWidth = 1.2f + pulse
                    c.drawCircle(t.x, t.y, t.radius * (0.55f + 0.4f * pulse), stroke)
                }
                TelegraphShape.DONUT -> {
                    val band = t.length - t.radius
                    val mid = (t.radius + t.length) / 2f
                    stroke.color = alpha(danger, 40)
                    stroke.strokeWidth = band
                    c.drawCircle(t.x, t.y, mid, stroke)
                    stroke.color = alpha(danger, (60 + 80 * p).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = band * p
                    c.drawCircle(t.x, t.y, mid, stroke)
                    stroke.color = edge
                    stroke.strokeWidth = 2f
                    c.drawCircle(t.x, t.y, t.radius, stroke)
                    c.drawCircle(t.x, t.y, t.length, stroke)
                }
                TelegraphShape.LINE -> {
                    c.save()
                    c.translate(t.x, t.y)
                    c.rotate(Math.toDegrees(t.angle.toDouble()).toFloat())
                    rect.set(0f, -t.radius, t.length, t.radius)
                    fill.color = alpha(danger, 40)
                    c.drawRect(rect, fill)
                    rect.set(0f, -t.radius, t.length * p, t.radius)
                    fill.color = hot
                    c.drawRect(rect, fill)
                    stroke.color = edge
                    stroke.strokeWidth = 2f
                    c.drawRect(0f, -t.radius, t.length, t.radius, stroke)
                    c.restore()
                }
                TelegraphShape.CROSS -> {
                    c.save()
                    c.translate(t.x, t.y)
                    c.rotate(Math.toDegrees(t.angle.toDouble()).toFloat())
                    fill.color = alpha(danger, 40)
                    c.drawRect(-t.length, -t.radius, t.length, t.radius, fill)
                    c.drawRect(-t.radius, -t.length, t.radius, t.length, fill)
                    fill.color = hot
                    c.drawRect(-t.length * p, -t.radius, t.length * p, t.radius, fill)
                    c.drawRect(-t.radius, -t.length * p, t.radius, t.length * p, fill)
                    c.restore()
                }
            }
            // 남은 시간 표시
            text.textAlign = Paint.Align.CENTER
            text.textSize = 15f
            text.color = 0xFFFFFFFF.toInt()
            if (t.shape != TelegraphShape.SAFE_CIRCLE) {
                c.drawText(t.name, t.x, t.y - t.radius - 6f, text)
            } else {
                c.drawText(t.name, t.x, t.y - t.radius - 8f, text)
            }
        }
    }

    // -------------------------------------------------------------- 엔티티

    private fun drawEntities(canvas: Canvas, w: BattleWorld) {
        val list = ArrayList<Any>(w.enemies.size + w.phantoms.size + w.bells.size + w.minions.size + 1)
        for (e in w.enemies) if (e.alive || e.deadFade < 1f) list.add(e)
        for (m in w.minions) if (m.alive || m.deadFade < 1f) list.add(m)
        for (b in w.bells) list.add(b)
        for (p in w.phantoms) list.add(p)
        list.add(w.player)
        list.sortBy { o ->
            when (o) {
                is Enemy -> o.y
                is PlayerState -> o.y
                is Bell -> o.y
                is Phantom -> o.y
                is Minion -> o.y
                else -> 0f
            }
        }
        for (o in list) {
            when (o) {
                is Enemy -> drawEnemy(canvas, w, o)
                is Minion -> drawMinion(canvas, w, o)
                is PlayerState -> drawPlayer(canvas, w)
                is Bell -> drawBell(canvas, w, o)
                is Phantom -> drawPhantom(canvas, w, o)
            }
        }
    }

    private fun drawPlayer(c: Canvas, w: BattleWorld) {
        val p = w.player
        val st = w.stats
        val element = st.fx.element
        val breath = 0.5f + 0.5f * sin(w.elapsed * 3.4f).toFloat()

        // 바닥 오라
        fill.shader = RadialGradient(
            p.x, p.y + 2f, 68f,
            intArrayOf(alpha(element.color, 70 + (breath * 30).toInt()), 0x00000000),
            null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(p.x, p.y + 2f, 68f, fill)
        fill.shader = null
        stroke.color = alpha(element.color, 90)
        stroke.strokeWidth = 1.6f
        val ringR = 34f + breath * 8f
        c.drawOval(p.x - ringR, p.y - ringR * 0.42f, p.x + ringR, p.y + ringR * 0.42f, stroke)

        drawShadow(c, p.x, p.y, 1.55f)
        // 점멸 도착 직후의 섬광
        if (p.blinkGlow > 0f) {
            val g = (p.blinkGlow / 0.26f).coerceIn(0f, 1f)
            stroke.color = alpha(0xFFFFE9A8.toInt(), (190 * g).toInt())
            stroke.strokeWidth = 2.6f
            c.drawCircle(p.x, p.y - 26f, 26f + 22f * (1f - g), stroke)
            stroke.color = alpha(0xFF8FE0FF.toInt(), (140 * g).toInt())
            stroke.strokeWidth = 1.4f
            c.drawCircle(p.x, p.y - 26f, 34f + 26f * (1f - g), stroke)
        }
        val bodyColor = blend(0xFFF4F0E4.toInt(), 0xFFFF5555.toInt(), p.hurtFlash * 0.85f)
        if (p.whirlTimer > 0f) {
            // 선회 공격 — 몸을 크게 돌리며 주변을 벤다. 회전 궤적을 같이 남긴다.
            val spin = p.whirlAngle
            c.save()
            c.rotate(Math.toDegrees(spin.toDouble()).toFloat(), p.x, p.y - 28f)
            drawStickman(
                c = c, x = p.x, y = p.y, s = 1.62f,
                body = bodyColor, accent = element.color,
                facing = spin, walk = p.walkPhase, moving = p.moving,
                attack = 1f, weapon = st.weapon, form = st.form,
                glow = 0.55f + breath * 0.25f,
            )
            c.restore()
            val ring = 46f + sin(spin * 2f) * 4f
            stroke.color = alpha(0xFFEAF4FF.toInt(), 170)
            stroke.strokeWidth = 2.4f
            c.drawOval(p.x - ring, p.y - ring * 0.45f, p.x + ring, p.y + ring * 0.45f, stroke)
            stroke.color = alpha(0xFFBFD8FF.toInt(), 110)
            stroke.strokeWidth = 1.2f
            c.drawOval(p.x - ring * 0.7f, p.y - ring * 0.32f, p.x + ring * 0.7f, p.y + ring * 0.32f, stroke)
            stroke.color = alpha(0xFFDCE8FF.toInt(), 150)
            stroke.strokeWidth = 1.5f
            for (i in 0 until 3) {
                val a = spin + i * 2.09f
                c.drawLine(
                    p.x + cos(a) * 26f, p.y - 30f + sin(a) * 12f,
                    p.x + cos(a) * 52f, p.y - 24f + sin(a) * 24f, stroke,
                )
            }
        } else {
            drawStickman(
                c = c, x = p.x, y = p.y, s = 1.62f,
                body = bodyColor, accent = element.color,
                facing = p.facing, walk = p.walkPhase, moving = p.moving,
                attack = p.attackAnim, weapon = st.weapon, form = st.form,
                glow = 0.35f + breath * 0.25f,
            )
        }
        if (w.state == BattleWorld.State.DEFEAT) {
            stroke.color = 0x99FF4444.toInt()
            stroke.strokeWidth = 1.6f
            c.drawLine(p.x - 21f, p.y - 58f, p.x - 9f, p.y - 46f, stroke)
            c.drawLine(p.x - 9f, p.y - 58f, p.x - 21f, p.y - 46f, stroke)
        }
    }

    /**
     * 공허의 형상이 빗어 낸 상.
     * 플레이어와 같은 스틱맨 실루엣을 보랏빛 반투명으로 그리고, 회전 각도에 맞춰 몸을 돌린다.
     */
    private fun drawPhantom(c: Canvas, w: BattleWorld, p: Phantom) {
        val fade = p.fade
        if (fade <= 0.03f) return
        val purple = 0xFFB07AFF.toInt()
        val glow = (70 + 50 * sin(w.elapsed * 16.0).toFloat()) * fade
        fill.shader = RadialGradient(
            p.x, p.y - 10f, 52f,
            intArrayOf(alpha(purple, glow.toInt().coerceIn(0, 255)), 0x00000000),
            null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(p.x, p.y - 10f, 52f, fill)
        fill.shader = null
        stroke.color = alpha(purple, (120 * fade).toInt())
        stroke.strokeWidth = 1.6f
        c.drawOval(p.x - 24f, p.y - 10f, p.x + 24f, p.y + 10f, stroke)

        c.save()
        c.rotate(Math.toDegrees((p.spin * 0.55f).toDouble()).toFloat(), p.x, p.y - 28f)
        drawStickman(
            c = c, x = p.x, y = p.y, s = 1.55f,
            body = withAlpha(0xFFD9C4FF.toInt(), (170 * fade).toInt()),
            accent = withAlpha(purple, (200 * fade).toInt()),
            facing = p.facing, walk = p.age * 9f, moving = true,
            attack = 1f, weapon = p.weapon, form = p.form,
            glow = 0.45f * fade,
        )
        c.restore()

        // 위로 끊어지듯 흩어지는 보랏빛 잔재
        fill.color = alpha(purple, (110 * fade).toInt())
        for (i in 0 until 4) {
            val a = p.spin + i * 1.57f
            val r = 12f + (p.age * 40f + i * 5f) % 26f
            c.drawCircle(p.x + cos(a) * r, p.y - 20f - (p.age * 46f + i * 7f) % 30f, 2.2f - i * 0.3f, fill)
        }
    }

    /** 폭풍의 종·공허의 종 — 땅에 박힌 채 흔들리는 종 */
    private fun drawBell(c: Canvas, w: BattleWorld, b: Bell) {
        val fade = b.fade
        if (fade <= 0.03f) return
        val gold = if (b.ghost) 0xFFD8C3A0.toInt() else 0xFFFFD98A.toInt()
        val sway = sin(w.elapsed * 5.2f).toFloat() * (2f + b.swing * 8f)
        fill.color = 0x55000000
        c.drawOval(b.x - 22f, b.y - 7f, b.x + 22f, b.y + 7f, fill)
        stroke.color = alpha(gold, (110 * fade).toInt())
        stroke.strokeWidth = 1.6f
        c.drawOval(b.x - 26f, b.y - 10f, b.x + 26f, b.y + 10f, stroke)

        val topX = b.x + sway
        val topY = b.y - 52f
        path.reset()
        path.moveTo(b.x - 17f, b.y - 4f)
        path.cubicTo(b.x - 17f, b.y - 38f, topX - 7f, topY + 6f, topX - 6f, topY)
        path.lineTo(topX + 6f, topY)
        path.cubicTo(topX + 7f, topY + 6f, b.x + 17f, b.y - 38f, b.x + 17f, b.y - 4f)
        path.close()
        fill.shader = LinearGradient(
            b.x, topY, b.x + 18f, b.y,
            intArrayOf(alpha(gold, (220 * fade).toInt()), alpha(0xFF8A6A2E.toInt(), (190 * fade).toInt())),
            null, Shader.TileMode.CLAMP,
        )
        c.drawPath(path, fill)
        fill.shader = null
        stroke.color = alpha(0xFFFFF3D0.toInt(), (180 * fade).toInt())
        stroke.strokeWidth = 1.4f
        c.drawPath(path, stroke)
        // 꼭지와 종가락
        fill.color = alpha(gold, (220 * fade).toInt())
        c.drawCircle(topX, topY - 5f, 4f, fill)
        stroke.color = alpha(0xFFFFF3D0.toInt(), (150 * fade).toInt())
        stroke.strokeWidth = 1.2f
        c.drawLine(topX, topY - 9f, topX - sway * 0.5f, topY - 18f, stroke)

        // 울린 직후 펴지는 충격파 고리
        if (b.swing > 0.02f) {
            val r = 30f + (1f - b.swing) * 92f
            stroke.color = alpha(0xFFFFE9A8.toInt(), (170 * b.swing * fade).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 2.4f
            c.drawOval(b.x - r, b.y - r * 0.42f, b.x + r, b.y + r * 0.42f, stroke)
        }
    }

    /** 소용돌이 베기가 남긴 와류 — 바닥에서 도는 회오리 고리 */
    private fun drawWhirlwinds(c: Canvas, w: BattleWorld) {
        for (v in w.whirlwinds) {
            val fade = v.fade
            if (fade <= 0.03f) continue
            fill.color = alpha(0xFF9FC4FF.toInt(), (22 * fade).toInt().coerceIn(0, 255))
            c.drawOval(v.x - v.radius, v.y - v.radius * 0.5f, v.x + v.radius, v.y + v.radius * 0.5f, fill)
            for (k in 0 until 3) {
                val r = v.radius * (0.52f + k * 0.22f)
                val spin = (w.elapsed * (2.1f + k * 0.7f)).toFloat() + k * 2.0f
                stroke.color = alpha(
                    if (k == 0) 0xFFE6F0FF.toInt() else 0xFFBFD8FF.toInt(),
                    ((120 - k * 26) * fade).toInt().coerceIn(0, 255),
                )
                stroke.strokeWidth = 2.4f - k * 0.5f
                rect.set(v.x - r, v.y - r * 0.5f, v.x + r, v.y + r * 0.5f)
                c.drawArc(rect, Math.toDegrees(spin.toDouble()).toFloat(), 210f, false, stroke)
            }
            // 단계만큼 위로 솟는 먼지 기둥
            fill.color = alpha(0xFFD6F0FF.toInt(), (90 * fade).toInt().coerceIn(0, 255))
            c.drawCircle(v.x, v.y - 14f - v.stage * 6f, 3f + v.stage, fill)
        }
    }

    /** 돌개바람 — 위로 좁아지는 회오리 깔때기 */
    private fun drawTwisters(c: Canvas, w: BattleWorld) {
        for (t in w.twisters) {
            val fade = t.fade
            if (fade <= 0.03f) continue
            val tall = if (t.amplified) 132f else 104f
            val tint = if (t.amplified) 0xFFE8F6FF.toInt() else 0xFFE2D8BE.toInt()
            fill.color = alpha(tint, (26 * fade).toInt().coerceIn(0, 255))
            c.drawOval(t.x - t.radius, t.y - t.radius * 0.46f, t.x + t.radius, t.y + t.radius * 0.46f, fill)
            for (i in 0 until 6) {
                val f = i / 5f
                val r = t.radius * (0.95f - f * 0.62f)
                val y = t.y - tall * f
                val spin = (w.elapsed * (7f + i * 0.6f)).toFloat() + i * 1.1f
                stroke.color = alpha(tint, ((150 - i * 16) * fade).toInt().coerceIn(0, 255))
                stroke.strokeWidth = 2.6f - i * 0.25f
                rect.set(t.x - r, y - r * 0.34f, t.x + r, y + r * 0.34f)
                c.drawArc(rect, Math.toDegrees(spin.toDouble()).toFloat(), 240f, false, stroke)
            }
            // 휘말린 티끌
            fill.color = alpha(0xFFF4F0E4.toInt(), (90 * fade).toInt().coerceIn(0, 255))
            for (i in 0 until 3) {
                val a = w.elapsed.toFloat() * 6f + i * 2.1f
                val y = t.y - 20f - i * 26f
                c.drawCircle(t.x + cos(a) * (t.radius * 0.5f), y, 1.8f, fill)
            }
        }
    }

    private fun drawEnemy(c: Canvas, w: BattleWorld, e: Enemy) {
        val alphaF = 1f - e.deadFade
        if (alphaF <= 0.02f) return
        val baseColor = when (e.kind) {
            EnemyKind.NORMAL -> 0xFFB9AFA6.toInt()
            EnemyKind.MAGIC -> 0xFF93A9E8.toInt()
            EnemyKind.RARE -> 0xFFFFC86A.toInt()
            EnemyKind.BOSS -> 0xFFFF8A66.toInt()
        }
        val body = if (e.hitFlash > 0.01f) blend(baseColor, 0xFFFFFFFF.toInt(), e.hitFlash * 0.9f) else baseColor
        val scale = e.scale

        if (e.alive) drawShadow(c, e.x, e.y, scale * 0.9f)

        if (e.boss != null) {
            val pulse = 0.5f + 0.5f * sin(w.elapsed * 2.2f).toFloat()
            fill.shader = RadialGradient(
                e.x, e.y - 40f * scale, 170f,
                intArrayOf(
                    alpha(if (e.enraged) 0xFFFF3B3B.toInt() else e.boss!!.element.color, (60 + pulse * 40).toInt()),
                    0x00000000,
                ),
                null, Shader.TileMode.CLAMP,
            )
            c.drawCircle(e.x, e.y - 40f * scale, 170f, fill)
            fill.shader = null
        }

        val facing = if (e.x > w.player.x) 3.1415927f else 0f
        if (e.boss != null) {
            drawBossFigure(c, w, e, alphaF)
        } else {
            val look = MonsterDesign.look(e.kind, e.element, e.enraged)
            drawMonsterFigure(c, w, e, look, alphaF, facing)
        }

        if (!e.alive) return

        // 상태 표시
        val barW = 40f * scale
        val top = e.y - 60f * scale
        if (e.kind != EnemyKind.BOSS) {
            fill.color = 0xAA000000.toInt()
            rect.set(e.x - barW / 2f - 1f, top - 1f, e.x + barW / 2f + 1f, top + 4.4f)
            c.drawRect(rect, fill)
            fill.color = when (e.kind) {
                EnemyKind.RARE -> 0xFFFFC24D.toInt()
                EnemyKind.MAGIC -> 0xFF7FA0FF.toInt()
                else -> 0xFFD97070.toInt()
            }
            rect.set(e.x - barW / 2f, top, e.x - barW / 2f + barW * (e.hp / e.maxHp).toFloat().coerceIn(0f, 1f), top + 3.4f)
            c.drawRect(rect, fill)
        }
        if (e.dot > 0.0) {
            fill.color = alpha(0xFFC182FF.toInt(), 110)
            c.drawCircle(e.x, top - 8f * scale, 3.2f, fill)
        }
    }

    /**
     * 몬스터 한 마리.
     *
     * 플레이어는 밝은 뼈대, 소환수는 뼈·유령 빛으로 그리므로, 몬스터는 검은 외곽선을 두른
     * 어두운 덩어리로 그린다. 종류마다 실루엣(굽은 등·후드·갑주)과 눈빛이 다르다.
     */
    private fun drawMonsterFigure(
        c: Canvas,
        w: BattleWorld,
        e: Enemy,
        look: MonsterLook,
        alphaF: Float,
        facing: Float,
    ) {
        val s = e.scale
        val x = e.x
        val y = e.y
        val h = look.height * s
        val halfW = look.width * 0.5f * s
        val breathe = sin(w.elapsed * 2.2f + e.slotAngle).toFloat()
        val bob = breathe * 0.9f * s
        val topY = y - h + bob
        val shY = topY + h * 0.30f
        val hipY = y - h * 0.30f
        val headR = h * 0.135f
        val headY = topY + h * 0.09f
        val dir = if (facing > 1.5f) -1f else 1f
        val flash = e.hitFlash.coerceIn(0f, 1f)
        val bodyColor = withAlpha(blend(look.body, 0xFFFFFFFF.toInt(), flash * 0.75f), (255 * alphaF).toInt().coerceIn(0, 255))
        val outlineColor = withAlpha(look.outline, (240 * alphaF).toInt().coerceIn(0, 255))
        val rimColor = withAlpha(look.rim, (210 * alphaF).toInt().coerceIn(0, 255))
        val accent = withAlpha(e.element.color, (210 * alphaF).toInt().coerceIn(0, 255))

        // 발광 오라 — 희귀·술사는 뒤에서 은은하게 번진다.
        if (look.aura != 0 && look.auraRadius > 0f && e.alive) {
            val pulse = 0.5f + 0.5f * sin(w.elapsed * 3.1f + e.slotAngle).toFloat()
            val strength = ((if (e.enraged) 52 else 30) + pulse * 34).toFloat() * alphaF
            glowAt(c, x, y - h * 0.45f, look.auraRadius * s, look.aura, strength.toInt().coerceIn(0, 255))
        }

        // 박쥐 날개 — 몸 뒤에서 천천히 펄럭인다.
        if (look.wings) {
            val flap = sin(w.elapsed * 4.1f + e.slotAngle).toFloat()
            for (side in listOf(-1f, 1f)) {
                path.reset()
                path.moveTo(x + side * halfW * 0.30f, shY)
                path.cubicTo(
                    x + side * halfW * (1.75f + flap * 0.25f), shY - h * 0.52f,
                    x + side * halfW * (2.45f + flap * 0.30f), shY - h * 0.02f,
                    x + side * halfW * 0.55f, hipY - h * 0.02f,
                )
                path.close()
                fill.color = withAlpha(0xFF17121C.toInt(), (165 * alphaF).toInt().coerceIn(0, 255))
                c.drawPath(path, fill)
                stroke.color = withAlpha(look.rim, (95 * alphaF).toInt().coerceIn(0, 255))
                stroke.strokeWidth = 0.9f
                c.drawPath(path, stroke)
            }
        }

        // 다리 — 굽은 뒷다리
        val stride = sin(e.walkPhase).toFloat() * h * 0.05f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = bodyColor
        stroke.strokeWidth = h * 0.11f
        c.drawLine(x - halfW * 0.40f, hipY, x - halfW * 0.58f - stride, y - 1f, stroke)
        c.drawLine(x + halfW * 0.40f, hipY, x + halfW * 0.58f + stride, y - 1f, stroke)

        // 몸통 — 앞으로 굽은 덩어리
        path.reset()
        path.moveTo(x - halfW * 0.48f, hipY + h * 0.02f)
        path.cubicTo(x - halfW * 1.12f, shY + h * 0.14f, x - halfW * 1.02f, topY + h * 0.14f, x - halfW * 0.28f, topY + h * 0.10f)
        path.cubicTo(x + halfW * 0.12f, topY - h * 0.02f, x + halfW * 0.72f, topY + h * 0.06f, x + halfW * 0.86f, shY)
        path.cubicTo(x + halfW * 1.08f, shY + h * 0.18f, x + halfW * 0.74f, hipY + h * 0.02f, x + halfW * 0.48f, hipY + h * 0.02f)
        path.close()
        fill.color = bodyColor
        c.drawPath(path, fill)
        stroke.color = outlineColor
        stroke.strokeWidth = 1.9f * s
        c.drawPath(path, stroke)

        // 테두리 하이라이트 (왼쪽 어깨)
        path.reset()
        path.moveTo(x - halfW * 0.70f, shY + h * 0.02f)
        path.cubicTo(x - halfW * 0.85f, topY + h * 0.20f, x - halfW * 0.55f, topY + h * 0.12f, x - halfW * 0.30f, topY + h * 0.12f)
        stroke.color = rimColor
        stroke.strokeWidth = 1.2f * s
        c.drawPath(path, stroke)

        // 등 가시
        if (look.spikes > 0) {
            stroke.color = withAlpha(0xFFDDD0BC.toInt(), (200 * alphaF).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.7f * s
            for (i in 0 until look.spikes) {
                val t = (i + 1f) / (look.spikes + 1f)
                val sx = x - dir * halfW * (0.80f + t * 0.12f)
                val sy = topY + h * (0.16f + t * 0.30f)
                c.drawLine(sx, sy, sx - dir * halfW * 0.38f, sy - h * 0.10f, stroke)
            }
            // 가시 끝을 속성빛으로
            stroke.color = accent
            stroke.strokeWidth = 0.8f
            for (i in 0 until look.spikes) {
                val t = (i + 1f) / (look.spikes + 1f)
                val sx = x - dir * halfW * (0.98f + t * 0.12f)
                val sy = topY + h * (0.10f + t * 0.30f)
                c.drawCircle(sx, sy, 1.1f * s, fill.apply { color = accent })
            }
        }

        // 머리
        val headX = x + dir * halfW * 0.30f
        fill.color = bodyColor
        c.drawCircle(headX, headY, headR, fill)
        stroke.color = outlineColor
        stroke.strokeWidth = 1.9f * s
        c.drawCircle(headX, headY, headR, stroke)

        // 뿔 — 악마의 굽은 뿔
        if (look.horns > 0) {
            stroke.color = withAlpha(0xFFE2D5C0.toInt(), (240 * alphaF).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 2.1f * s
            for (i in 0 until look.horns.coerceAtMost(2)) {
                val hx = headX + (i - 0.5f) * headR * 1.15f
                val tipX = hx - dir * headR * (0.65f + i * 0.25f)
                path.reset()
                path.moveTo(hx, headY - headR * 0.55f)
                path.quadTo(hx - dir * headR * 0.15f, headY - headR * 1.6f, tipX, headY - headR * 2.05f)
                c.drawPath(path, stroke)
            }
        }

        // 후드 술사 — 머리를 덮는 뾰족 모자
        if (look.build == MonsterBuild.HOODED_CASTER) {
            path.reset()
            path.moveTo(headX - headR * 1.35f, headY + headR * 0.75f)
            path.quadTo(headX, headY - headR * 2.6f, headX + headR * 1.35f, headY + headR * 0.75f)
            path.close()
            fill.color = withAlpha(0xFF221C36.toInt(), (240 * alphaF).toInt().coerceIn(0, 255))
            c.drawPath(path, fill)
            stroke.color = withAlpha(look.rim, (150 * alphaF).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.1f * s
            c.drawPath(path, stroke)
        }

        // 갑주 — 어깨 판·가슴 판·왕관
        if (look.armored) {
            val plate = withAlpha(0xFF3B3128.toInt(), (235 * alphaF).toInt().coerceIn(0, 255))
            fill.color = plate
            // 어깨 판
            for (side in listOf(-1f, 1f)) {
                rect.set(
                    x + side * halfW * 0.95f - halfW * 0.32f,
                    shY - h * 0.10f,
                    x + side * halfW * 0.95f + halfW * 0.32f,
                    shY + h * 0.10f,
                )
                c.drawOval(rect, fill)
                stroke.color = withAlpha(look.rim, (170 * alphaF).toInt().coerceIn(0, 255))
                stroke.strokeWidth = 1.2f * s
                c.drawOval(rect, stroke)
                // 어깨 가시
                c.drawLine(
                    x + side * halfW * 0.95f, shY - h * 0.06f,
                    x + side * halfW * 1.18f, shY - h * 0.24f, stroke,
                )
            }
            // 가슴 판
            path.reset()
            path.moveTo(x - halfW * 0.42f, shY + h * 0.06f)
            path.lineTo(x + halfW * 0.42f, shY + h * 0.04f)
            path.lineTo(x + halfW * 0.30f, shY + h * 0.30f)
            path.lineTo(x - halfW * 0.30f, shY + h * 0.32f)
            path.close()
            fill.color = withAlpha(0xFF5A4A2E.toInt(), (235 * alphaF).toInt().coerceIn(0, 255))
            c.drawPath(path, fill)
            stroke.color = withAlpha(0xFFFFD36A.toInt(), (200 * alphaF).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.0f * s
            c.drawPath(path, stroke)
            // 왕관
            stroke.strokeWidth = 2.2f * s
            for (i in -1..1) {
                c.drawLine(
                    headX + i * headR * 0.75f, headY - headR * 0.75f,
                    headX + i * headR * 0.95f, headY - headR * 1.65f, stroke,
                )
            }
        }

        // 팔과 발톱 — 공격할 때 크게 휘두른다
        val swing = if (e.attackAnim > 0f) sin(e.attackAnim * 3.14159f).toFloat() else 0f
        val windupLift = if (e.windup > 0f) (e.windup / 0.95f).coerceIn(0f, 1f) else 0f
        val handX = x + dir * (halfW * 1.05f + swing * halfW * 0.85f)
        val handY = shY + h * 0.26f - swing * h * 0.30f - windupLift * h * 0.18f
        stroke.color = bodyColor
        stroke.strokeWidth = h * 0.10f
        c.drawLine(x + dir * halfW * 0.75f, shY + h * 0.05f, handX, handY, stroke)
        stroke.color = outlineColor
        stroke.strokeWidth = 1.4f * s
        c.drawLine(x + dir * halfW * 0.75f, shY + h * 0.05f, handX, handY, stroke)
        // 발톱 세 개
        stroke.color = withAlpha(0xFFF0E4CE.toInt(), (235 * alphaF).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 1.6f * s
        for (i in -1..1) {
            c.drawLine(
                handX, handY,
                handX + dir * h * 0.10f + i * 2.2f * s,
                handY + h * 0.11f - i * 2.2f * s,
                stroke,
            )
        }
        // 반대팔(작게)
        stroke.color = bodyColor
        stroke.strokeWidth = h * 0.085f
        c.drawLine(
            x - dir * halfW * 0.80f, shY + h * 0.06f,
            x - dir * halfW * 1.02f, shY + h * 0.30f - swing * h * 0.08f, stroke,
        )

        // 무기 — 술사는 지팡이, 근접은 발톱만
        if (look.build == MonsterBuild.HOODED_CASTER) {
            val staffTop = headY - h * 0.55f
            stroke.color = withAlpha(0xFF8A7250.toInt(), (240 * alphaF).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.6f * s
            c.drawLine(handX, handY - h * 0.30f, handX + dir * 4f * s, staffTop, stroke)
            glowAt(c, handX + dir * 4f * s, staffTop, 9f * s, e.element.color, (150 * alphaF).toInt().coerceIn(0, 255))
        }

        // 눈 — 붉거나 보랏빛으로 빛난다
        if (e.alive) {
            val eyeY = headY - headR * 0.10f
            val eye1X = headX + dir * headR * 0.30f
            val eye2X = headX - dir * headR * 0.30f
            glowAt(c, eye1X, eyeY, 7.5f * s, look.eye, (200 * alphaF).toInt().coerceIn(0, 255))
            glowAt(c, eye2X, eyeY, 7.5f * s, look.eye, (200 * alphaF).toInt().coerceIn(0, 255))
            fill.color = withAlpha(0xFFFFF3E8.toInt(), (235 * alphaF).toInt().coerceIn(0, 255))
            c.drawCircle(eye1X, eyeY, 1.15f * s, fill)
            c.drawCircle(eye2X, eyeY, 1.15f * s, fill)
        }

        // 보스급 예고 — 곧 내려칠 때 발밑에 붉은 고리
        if (e.windup > 0f) {
            stroke.color = alpha(0xFFFF4B4B.toInt(), (150 * windupLift).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.6f
            c.drawOval(x - 26f * s, y - 9f * s, x + 26f * s, y + 9f * s, stroke)
        }
    }

    /**
     * 소환수·반려수 한 마리.
     *
     * 뼈 소환수는 크림색 뼈대와 해골, 유령은 반투명 청록, 반려수는 네 발 짐승,
     * 두꺼비는 초록 덩어리로 그려 플레이어·몬스터와 구분한다.
     */
    private fun drawMinion(c: Canvas, w: BattleWorld, m: Minion) {
        val fade = if (m.alive) 1f else 1f - m.deadFade
        if (fade <= 0.03f) return
        val s = m.type.scale
        val x = m.x
        val y = m.y
        val hop = if (m.isToad) abs(sin(m.anim * 8f)) * 5f * s else 0f
        val bob = if (m.type.companion) sin(m.anim * 6f).toFloat() * 1.2f * s else 0f
        val bodyY = y - hop + bob

        if (m.alive) drawShadow(c, x, y, s * 0.7f)
        when {
            m.isToad -> drawToad(c, x, bodyY, s, fade, m)
            m.type.companion -> drawBeast(c, x, bodyY, s, fade, m)
            m.type.role == MinionRole.CASTER || m.type.role == MinionRole.RANGED ->
                drawSkeleton(c, x, bodyY, s, fade, m, ranged = true)
            m.type.untargetable -> drawSpirit(c, x, bodyY, s, fade, m)
            else -> drawSkeleton(c, x, bodyY, s, fade, m, ranged = false)
        }

        // 소환수 체력 — 머리 위에 얇게
        if (m.alive && m.hp < m.maxHp) {
            val bw = 18f * s
            fill.color = 0xAA000000.toInt()
            rect.set(x - bw / 2f, bodyY - 44f * s, x + bw / 2f, bodyY - 41f * s)
            c.drawRect(rect, fill)
            fill.color = 0xFF7FD9A0.toInt()
            rect.set(x - bw / 2f, bodyY - 44f * s, x - bw / 2f + bw * (m.hp / m.maxHp).toFloat().coerceIn(0f, 1f), bodyY - 41f * s)
            c.drawRect(rect, fill)
        }
    }

    /** 해골 소환수 — 크림색 뼈대, 어두운 눈구멍, 초록/보랏빛 속불 */
    private fun drawSkeleton(c: Canvas, x: Float, y: Float, s: Float, fade: Float, m: Minion, ranged: Boolean) {
        val bone = withAlpha(0xFFEDE4CE.toInt(), (245 * fade).toInt().coerceIn(0, 255))
        val shade = withAlpha(0xFF9A8F76.toInt(), (220 * fade).toInt().coerceIn(0, 255))
        val glow = withAlpha(0xFF7FE0C8.toInt(), (200 * fade).toInt().coerceIn(0, 255))
        val swing = if (m.attackAnim > 0f) sin(m.attackAnim * 3.14f).toFloat() else 0f
        val facing = if (m.facing > 1.5f) -1f else 1f
        val headY = y - 34f * s
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = bone
        stroke.strokeWidth = 2.4f * s
        // 다리
        val stride = sin(m.walkPhase).toFloat() * 3f * s
        c.drawLine(x - 2f * s, y - 14f * s, x - 4f * s - stride, y - 1f, stroke)
        c.drawLine(x + 2f * s, y - 14f * s, x + 4f * s + stride, y - 1f, stroke)
        // 척추·갈비
        c.drawLine(x, y - 14f * s, x, y - 26f * s, stroke)
        stroke.strokeWidth = 1.4f * s
        for (i in 0 until 3) {
            val ry = y - (17f + i * 3.2f) * s
            c.drawLine(x - 5f * s, ry, x + 5f * s, ry, stroke)
        }
        // 팔
        stroke.strokeWidth = 2.0f * s
        c.drawLine(x, y - 25f * s, x + facing * (9f + swing * 8f) * s, y - (14f - swing * 8f) * s, stroke)
        // 해골
        fill.color = bone
        c.drawCircle(x, headY, 5.6f * s, fill)
        stroke.color = shade
        stroke.strokeWidth = 1f * s
        c.drawCircle(x, headY, 5.6f * s, stroke)
        // 눈구멍
        fill.color = withAlpha(0xFF1A1712.toInt(), (235 * fade).toInt().coerceIn(0, 255))
        c.drawCircle(x - 2f * s, headY - 0.6f * s, 1.5f * s, fill)
        c.drawCircle(x + 2f * s, headY - 0.6f * s, 1.5f * s, fill)
        glowAt(c, x, headY - 0.6f * s, 5f * s, m.type.element.color, (120 * fade).toInt().coerceIn(0, 255))
        // 이빨
        stroke.color = shade
        stroke.strokeWidth = 0.8f * s
        c.drawLine(x - 2.2f * s, headY + 3.2f * s, x + 2.2f * s, headY + 3.2f * s, stroke)
        if (ranged) {
            // 활
            stroke.color = withAlpha(0xFFB99A6A.toInt(), (240 * fade).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.4f * s
            rect.set(x + facing * 5f * s - 4f * s, y - 30f * s, x + facing * 5f * s + 4f * s, y - 8f * s)
            c.drawArc(rect, if (facing > 0) 270f else 90f, 180f, false, stroke)
        } else {
            // 낡은 검
            stroke.color = withAlpha(0xFFBFB49A.toInt(), (235 * fade).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.8f * s
            c.drawLine(
                x + facing * 8f * s, y - 14f * s,
                x + facing * (14f + swing * 6f) * s, y - (26f + swing * 4f) * s, stroke,
            )
        }
        // 작은 불꽃 (속성빛)
        if (m.attackAnim > 0.5f) {
            glowAt(c, x + facing * 12f * s, y - 20f * s, 7f * s, m.type.element.color, (140 * fade).toInt().coerceIn(0, 255))
        }
    }

    /** 격노의 유령·망령 — 반투명 청록 불꽃 두개골 */
    private fun drawSpirit(c: Canvas, x: Float, y: Float, s: Float, fade: Float, m: Minion) {
        val color = withAlpha(0xFF7FD9E8.toInt(), (150 * fade).toInt().coerceIn(0, 255))
        fill.color = color
        c.drawCircle(x, y - 28f * s, 5.2f * s, fill)
        // 흘러내리는 불꽃 꼬리
        path.reset()
        path.moveTo(x - 4.5f * s, y - 25f * s)
        path.quadTo(x - 2f * s, y - 8f * s, x, y - 2f * s)
        path.quadTo(x + 2f * s, y - 8f * s, x + 4.5f * s, y - 25f * s)
        path.close()
        fill.color = withAlpha(0xFF4FA8C9.toInt(), (110 * fade).toInt().coerceIn(0, 255))
        c.drawPath(path, fill)
        glowAt(c, x, y - 28f * s, 12f * s, m.type.element.color, (150 * fade).toInt().coerceIn(0, 255))
        fill.color = withAlpha(0xFF10222A.toInt(), (220 * fade).toInt().coerceIn(0, 255))
        c.drawCircle(x - 1.8f * s, y - 28.5f * s, 1.3f * s, fill)
        c.drawCircle(x + 1.8f * s, y - 28.5f * s, 1.3f * s, fill)
    }

    /** 반려수 — 네 발 짐승, 금빛 목걸이 */
    private fun drawBeast(c: Canvas, x: Float, y: Float, s: Float, fade: Float, m: Minion) {
        val fur = withAlpha(0xFF8A6544.toInt(), (245 * fade).toInt().coerceIn(0, 255))
        val dark = withAlpha(0xFF4A3524.toInt(), (240 * fade).toInt().coerceIn(0, 255))
        val facing = if (m.facing > 1.5f) -1f else 1f
        val stride = sin(m.walkPhase).toFloat() * 3f * s
        val bodyY = y - 16f * s
        // 다리
        stroke.color = dark
        stroke.strokeWidth = 2.6f * s
        stroke.strokeCap = Paint.Cap.ROUND
        c.drawLine(x - 6f * s, bodyY + 5f * s, x - 7f * s - stride, y, stroke)
        c.drawLine(x + 6f * s, bodyY + 5f * s, x + 7f * s + stride, y, stroke)
        c.drawLine(x - 3f * s, bodyY + 6f * s, x - 3.5f * s + stride, y, stroke)
        c.drawLine(x + 3f * s, bodyY + 6f * s, x + 3.5f * s - stride, y, stroke)
        // 몸통
        rect.set(x - 10f * s, bodyY - 6f * s, x + 10f * s, bodyY + 6f * s)
        fill.color = fur
        c.drawOval(rect, fill)
        stroke.color = dark
        stroke.strokeWidth = 1.2f * s
        c.drawOval(rect, stroke)
        // 머리
        val hx = x + facing * 11f * s
        val hy = bodyY - 5f * s
        fill.color = fur
        c.drawCircle(hx, hy, 5.2f * s, fill)
        stroke.color = dark
        stroke.strokeWidth = 1f * s
        c.drawCircle(hx, hy, 5.2f * s, stroke)
        // 귀
        path.reset()
        path.moveTo(hx - 2.5f * s, hy - 4f * s)
        path.lineTo(hx - 1f * s, hy - 9f * s)
        path.lineTo(hx + 1.5f * s, hy - 4.5f * s)
        path.close()
        fill.color = fur
        c.drawPath(path, fill)
        // 눈
        fill.color = 0xFF2A1C12.toInt()
        c.drawCircle(hx + facing * 1.6f * s, hy - 1f * s, 1.1f * s, fill)
        // 금빛 목걸이
        stroke.color = withAlpha(0xFFFFD36A.toInt(), (230 * fade).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 1.4f * s
        c.drawLine(x + facing * 5f * s, bodyY - 3f * s, x + facing * 5f * s, bodyY + 3f * s, stroke)
        // 꼬리
        stroke.color = dark
        stroke.strokeWidth = 2.2f * s
        c.drawLine(
            x - facing * 9f * s, bodyY - 2f * s,
            x - facing * 13f * s, bodyY - 9f * s - sin(m.anim * 5f).toFloat() * 2f * s, stroke,
        )
    }

    /** 마녀두꺼비 — 초록 덩어리, 보랏빛 눈 */
    private fun drawToad(c: Canvas, x: Float, y: Float, s: Float, fade: Float, m: Minion) {
        val skin = withAlpha(0xFF6E8A3E.toInt(), (245 * fade).toInt().coerceIn(0, 255))
        val dark = withAlpha(0xFF3E4E22.toInt(), (240 * fade).toInt().coerceIn(0, 255))
        rect.set(x - 10f * s, y - 12f * s, x + 10f * s, y - 2f * s)
        fill.color = skin
        c.drawOval(rect, fill)
        stroke.color = dark
        stroke.strokeWidth = 1.2f * s
        c.drawOval(rect, stroke)
        // 뒷다리
        stroke.color = dark
        stroke.strokeWidth = 2.2f * s
        c.drawLine(x - 7f * s, y - 4f * s, x - 11f * s, y, stroke)
        c.drawLine(x + 7f * s, y - 4f * s, x + 11f * s, y, stroke)
        // 눈 두 개
        glowAt(c, x - 3f * s, y - 10f * s, 5f * s, 0xFFB07AFF.toInt(), (180 * fade).toInt().coerceIn(0, 255))
        fill.color = 0xFFE8D6FF.toInt()
        c.drawCircle(x - 3f * s, y - 10f * s, 1.4f * s, fill)
        c.drawCircle(x + 3f * s, y - 10f * s, 1.4f * s, fill)
    }

    /** 저주받은 지대 — 보랏빛 늪과 두꺼비가 깨어나는 무늬 */
    private fun drawCursedGrounds(c: Canvas, w: BattleWorld) {
        for (g in w.cursedGrounds) {
            val pulse = 0.5f + 0.5f * sin(w.elapsed * 2.4f + g.x * 0.05f).toFloat()
            fill.color = alpha(0xFF6B2E8A.toInt(), (44 + pulse * 26).toInt())
            rect.set(g.x - g.radius, g.y - g.radius * 0.52f, g.x + g.radius, g.y + g.radius * 0.52f)
            c.drawOval(rect, fill)
            stroke.color = alpha(0xFFB07AFF.toInt(), (120 + pulse * 60).toInt())
            stroke.strokeWidth = 1.6f
            rect.set(g.x - g.radius * 0.92f, g.y - g.radius * 0.48f, g.x + g.radius * 0.92f, g.y + g.radius * 0.48f)
            c.drawOval(rect, stroke)
            // 늪 위를 떠도는 저주 문양
            fill.color = alpha(0xFFD9B8FF.toInt(), (100 + pulse * 60).toInt())
            for (i in 0 until 4) {
                val a = w.elapsed.toFloat() * 0.6f + i * 1.57f
                val r = g.radius * 0.6f
                c.drawCircle(g.x + cos(a) * r, g.y + sin(a) * r * 0.45f, 1.8f, fill)
            }
        }
    }

    private fun drawShadow(c: Canvas, x: Float, y: Float, scale: Float) {
        fill.color = 0x44000000
        c.drawOval(x - 13f * scale, y - 3.6f * scale, x + 13f * scale, y + 3.6f * scale, fill)
    }

    /**
     * 스틱맨 한 명. y는 발 위치.
     */
    private fun drawStickman(
        c: Canvas,
        x: Float,
        y: Float,
        s: Float,
        body: Int,
        accent: Int,
        facing: Float,
        walk: Float,
        moving: Boolean,
        attack: Float,
        weapon: WeaponVisualType,
        form: AttackForm = AttackForm.MELEE,
        glow: Float = 0f,
        boss: Boolean = false,
        bossColor: Int = accent,
        alphaMul: Float = 1f,
        windup: Float = 0f,
    ) {
        val legLen = 17f * s
        val torso = 19f * s
        val headR = 6.4f * s
        val hipY = y - legLen
        val shY = hipY - torso
        val headY = shY - headR * 1.15f
        val bob = if (moving) sin(walk * 2f) * 0.9f * s else sin(walk * 0.6f) * 0.35f * s
        val up = -bob

        if (glow > 0f) {
            stroke.color = alpha(accent, (60 * glow + 30).toInt())
            stroke.strokeWidth = 7f * s
            c.drawCircle(x, shY - 4f * s + up, 22f * s, stroke)
        }

        stroke.strokeWidth = 3.2f * s
        stroke.color = body

        // 다리
        val swing = if (moving) sin(walk) else sin(walk * 0.7f) * 0.2f
        val l1x = x - 4.2f * s + swing * 7.5f * s
        val l2x = x + 4.2f * s - swing * 7.5f * s
        val l1y = y - max(0f, swing) * 3.2f * s
        val l2y = y - max(0f, -swing) * 3.2f * s
        c.drawLine(x, hipY + up, l1x, l1y, stroke)
        c.drawLine(x, hipY + up, l2x, l2y, stroke)

        // 몸통
        c.drawLine(x, hipY + up, x, shY + up, stroke)

        // 팔 + 무기
        val dir = cos(facing)
        val fwd = if (abs(dir) < 0.01f) 1f else if (dir >= 0f) 1f else -1f

        when (weapon) {
            WeaponVisualType.BOW -> {
                // 활: 앞팔은 활을 내밀고, 뒷팔은 시위를 뒤로 당겼다 놓는다.
                val handX = x + fwd * 12f * s
                val handY = shY + 2f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                val drawX = x + fwd * (7f - 12f * attack) * s
                c.drawLine(x, shY + 1f * s + up, drawX, shY + 3f * s + up, stroke)
                drawBow(c, handX, handY, s, facing, attack, body)
            }
            WeaponVisualType.CROSSBOW -> {
                // 쇠뇌: 두 팔로 앞세워 조준하고, 쏘는 순간 몸이 뒤로 밀린다.
                val recoil = attack * 2.6f * s
                val handX = x + fwd * (12f * s - recoil)
                val handY = shY + 4f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x + fwd * (5f * s - recoil), handY - 1.5f * s, stroke)
                drawCrossbow(c, handX, handY, s, facing, attack, accent)
            }
            WeaponVisualType.STAFF -> {
                // 지팡이: 무기를 머리 위로 치켜들고 시전한다.
                val lift = 6f + attack * 9f
                val handX = x + fwd * 7f * s
                val handY = shY - lift * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x + fwd * 2f * s, shY + 6f * s + up, stroke)
                drawStaff(c, handX, handY, s, facing, attack, accent, body)
            }
            WeaponVisualType.WAND -> {
                val lift = 5f + attack * 7f
                val handX = x + fwd * 9f * s
                val handY = shY - lift * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - fwd * 2f * s, shY + 4f * s + up, stroke)
                drawShortRod(c, handX, handY, s, facing, attack, accent, 17f)
            }
            WeaponVisualType.SCEPTRE -> {
                val lift = 6f + attack * 8f
                val handX = x + fwd * 8f * s
                val handY = shY - lift * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - fwd * 3f * s, shY + 5f * s + up, stroke)
                drawShortRod(c, handX, handY, s, facing, attack, accent, 22f)
            }
            WeaponVisualType.QUARTERSTAFF -> {
                val handX = x + fwd * 8f * s
                val handY = shY + 6f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - fwd * 4f * s, handY - 3f * s, stroke)
                drawPolearm(c, handX, handY, s, facing, attack, accent, 46f, false)
            }
            WeaponVisualType.SPEAR -> {
                val swing = facing - 0.35f + (1f - attack) * 0.9f
                val handX = x + cos(swing) * 9f * s
                val handY = shY + 5f * s + sin(swing) * 7f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - cos(swing) * 3f * s, shY + 8f * s + up, stroke)
                drawPolearm(c, handX, handY, s, swing, attack, accent, 40f, true)
            }
            WeaponVisualType.SWORD -> {
                val swing = facing - 1.15f + (1f - attack) * 2.15f
                val handX = x + cos(swing) * 10f * s
                val handY = shY + 5f * s + sin(swing) * 9f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - cos(swing) * 4f * s, shY + 8f * s + up, stroke)
                drawBlade(c, handX, handY, s, swing, attack, accent, body, 32f, false)
            }
            WeaponVisualType.DAGGER -> {
                val swing = facing - 0.9f + (1f - attack) * 1.8f
                val handX = x + cos(swing) * 9f * s
                val handY = shY + 5f * s + sin(swing) * 8f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - cos(swing) * 3f * s, shY + 8f * s + up, stroke)
                drawBlade(c, handX, handY, s, swing, attack, accent, body, 17f, false)
            }
            WeaponVisualType.AXE -> {
                val swing = facing - 1.2f + (1f - attack) * 2.3f
                val handX = x + cos(swing) * 10f * s
                val handY = shY + 5f * s + sin(swing) * 9f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - cos(swing) * 4f * s, shY + 8f * s + up, stroke)
                drawAxe(c, handX, handY, s, swing, attack, accent)
            }
            WeaponVisualType.MACE -> {
                val swing = facing - 1.25f + (1f - attack) * 2.4f
                val handX = x + cos(swing) * 10f * s
                val handY = shY + 5f * s + sin(swing) * 9f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - cos(swing) * 4f * s, shY + 8f * s + up, stroke)
                drawMace(c, handX, handY, s, swing, attack, accent)
            }
            WeaponVisualType.CLAW -> {
                val swing = facing - 1.0f + (1f - attack) * 1.9f
                val handX = x + cos(swing) * 9f * s
                val handY = shY + 5f * s + sin(swing) * 8f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - cos(swing) * 3f * s, shY + 8f * s + up, stroke)
                drawClaw(c, handX, handY, s, swing, attack, accent)
            }
            WeaponVisualType.FLAIL -> {
                val swing = facing - 1.3f + (1f - attack) * 2.5f
                val handX = x + cos(swing) * 10f * s
                val handY = shY + 5f * s + sin(swing) * 9f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - cos(swing) * 4f * s, shY + 8f * s + up, stroke)
                drawFlail(c, handX, handY, s, swing, attack, accent)
            }
            WeaponVisualType.SHIELD -> {
                val handX = x - fwd * 9f * s
                val handY = shY + 6f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x + fwd * 6f * s, handY + 2f * s, stroke)
                drawShield(c, handX, handY, s, facing, accent)
            }
            WeaponVisualType.NONE -> {
                val handX = x + fwd * 8f * s
                val handY = shY + 5f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - fwd * 3f * s, handY + 4f * s, stroke)
                fill.color = body
                c.drawCircle(handX, handY, 2.6f * s, fill)
            }
        }

        if (windup > 0f) {
            stroke.color = 0xFFFF4B3D.toInt()
            stroke.strokeWidth = 2f * s
            c.drawCircle(x, headY + up, headR * 2.4f + sin(windup * 20f) * 2f, stroke)
        }

        // 머리
        fill.color = body
        c.drawCircle(x, headY + up, headR, fill)
        fill.color = alpha(0xFF20242C.toInt(), 150)
        val eyeX = x + fwd * headR * 0.45f
        c.drawCircle(eyeX, headY + up - headR * 0.1f, headR * 0.16f, fill)

        if (boss) {
            stroke.strokeWidth = 2.3f * s
            stroke.color = alpha(bossColor, 235)
            c.drawLine(x - headR * 0.7f, headY + up - headR * 0.7f, x - headR * 2.1f, headY + up - headR * 2.4f, stroke)
            c.drawLine(x + headR * 0.7f, headY + up - headR * 0.7f, x + headR * 2.1f, headY + up - headR * 2.4f, stroke)
            c.drawLine(x + headR * 0.7f, headY + up - headR * 0.7f, x + headR * 2.4f, headY + up - headR * 1.5f, stroke)
            c.drawLine(x - headR * 0.7f, headY + up - headR * 0.7f, x - headR * 2.4f, headY + up - headR * 1.5f, stroke)
        }
    }

    // -------------------------------------------------------------- 우버 보스 외형

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        c.drawLine(x1, y1, x2, y2, stroke)
    }

    private fun disc(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.shader = null
        fill.color = color
        c.drawCircle(x, y, r, fill)
    }

    private fun glowAt(c: Canvas, x: Float, y: Float, r: Float, color: Int, inner: Int) {
        fill.shader = RadialGradient(
            x, y, r,
            intArrayOf(alpha(color, inner), alpha(color, inner / 3), 0x00000000),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(x, y, r, fill)
        fill.shader = null
    }

    /** 보스 5종을 각자의 인게임 인상으로 그린다 (큰 스틱맨이 아니라 전용 실루엣). */
    private fun drawBossFigure(c: Canvas, w: BattleWorld, e: Enemy, alphaMul: Float) {
        val boss = e.boss ?: return
        val s = e.scale
        val facing = if (e.x > w.player.x) 3.1415927f else 0f
        val fwd = if (cos(facing) >= 0f) 1f else -1f
        val legLen = 22f * s
        val torso = 27f * s
        val headR = 8.2f * s
        val hipY = e.y - legLen
        val shY = hipY - torso
        val headY = shY - headR * 1.2f
        val bob = sin(e.walkPhase * 2f) * 1.1f * s
        val up = -bob
        val p2 = w.bossPhase >= 2
        val accent = boss.element.color
        val skin = blend(0xFF2A2F3A.toInt(), accent, 0.25f)
        val skinC = withAlpha(skin, (255 * alphaMul).toInt())
        val accentC = withAlpha(accent, (255 * alphaMul).toInt())
        val hitWhite = if (e.hitFlash > 0.01f) e.hitFlash * 0.8f else 0f

        fun body(color: Int) = if (hitWhite > 0f) blend(color, 0xFFFFFFFF.toInt(), hitWhite) else color
        val glowBoost = if (p2) 1.7f else 1f

        // 공통: 바닥 그림자
        drawShadow(c, e.x, e.y, s * 1.15f)

        // 다리
        val swing = sin(e.walkPhase) * 6f * s
        line(c, e.x, hipY + up, e.x - 5f * s + swing, e.y, withAlpha(body(skin), (255 * alphaMul).toInt()), 4.4f * s)
        line(c, e.x, hipY + up, e.x + 5f * s - swing, e.y, withAlpha(body(skin), (255 * alphaMul).toInt()), 4.4f * s)
        // 몸통
        line(c, e.x, hipY + up, e.x, shY + up, withAlpha(body(skin), (255 * alphaMul).toInt()), 5.6f * s)
        // 머리
        disc(c, e.x, headY + up, headR, withAlpha(body(skin), (255 * alphaMul).toInt()))

        when (boss.id) {
            "arbiter" -> {
                val fire = 0xFFFF6A2B.toInt()
                // 불꽃 후광 (머리 뒤)
                glowAt(c, e.x, headY + up, 62f * s, fire, (70 * glowBoost).toInt().coerceAtMost(255))
                // 긴 망토
                path.reset()
                path.moveTo(e.x - 12f * s, shY + up)
                path.quadTo(e.x - 26f * s, e.y - 30f * s, e.x - 15f * s + sin(e.walkPhase) * 3f * s, e.y - 2f * s)
                path.lineTo(e.x + 15f * s + sin(e.walkPhase) * 3f * s, e.y - 2f * s)
                path.quadTo(e.x + 26f * s, e.y - 30f * s, e.x + 12f * s, shY + up)
                path.close()
                fill.shader = null
                fill.color = withAlpha(0xFF6C1B12.toInt(), (220 * alphaMul).toInt())
                c.drawPath(path, fill)
                stroke.color = alpha(fire, (140 * glowBoost).toInt().coerceAtMost(255))
                stroke.strokeWidth = 2f * s
                c.drawPath(path, stroke)
                // 불꽃 왕관
                repeat(5) { i ->
                    val bx = e.x + (i - 2) * 4.6f * s
                    val h = if (i == 2) 22f * s else 14f * s + (i % 2) * 3f * s
                    path.reset()
                    path.moveTo(bx - 2.6f * s, headY + up - headR * 0.7f)
                    path.quadTo(bx + (i - 2) * 1.6f * s, headY + up - headR - h * 0.6f, bx, headY + up - headR - h)
                    path.quadTo(bx - 1.4f * s, headY + up - headR - h * 0.5f, bx + 2.6f * s, headY + up - headR * 0.7f)
                    path.close()
                    fill.color = alpha(fire, (200 + (i % 2) * 40))
                    c.drawPath(path, fill)
                }
                // 팔 + 거대한 불타는 대검
                val swing = facing - 1.05f + (1f - e.attackAnim) * 2.0f
                val handX = e.x + cos(swing) * 16f * s
                val handY = shY + 8f * s + sin(swing) * 13f * s + up
                line(c, e.x, shY + 2f * s + up, handX, handY, withAlpha(body(skin), (255 * alphaMul).toInt()), 3.4f * s)
                line(c, e.x, shY + 2f * s + up, e.x - fwd * 10f * s, shY + 14f * s + up, withAlpha(body(skin), (255 * alphaMul).toInt()), 3.4f * s)
                c.save()
                c.translate(handX, handY)
                c.rotate(Math.toDegrees((swing + 0.5f).toDouble()).toFloat())
                line(c, -6f * s, 0f, 6f * s, 0f, 0xFF3A2A22.toInt(), 3f * s)
                path.reset()
                path.moveTo(6f * s, -4.6f * s)
                path.lineTo(60f * s, -2.2f * s)
                path.lineTo(66f * s, 0f)
                path.lineTo(60f * s, 2.2f * s)
                path.lineTo(6f * s, 4.6f * s)
                path.close()
                fill.color = alpha(0xFFFFB06A.toInt(), (235 * alphaMul).toInt())
                c.drawPath(path, fill)
                stroke.color = alpha(fire, (220 * glowBoost).toInt().coerceAtMost(255))
                stroke.strokeWidth = 1.6f * s
                c.drawPath(path, stroke)
                c.restore()
                if (p2) glowAt(c, e.x, e.y - 40f * s, 120f * s, fire, 46)
            }
            "xesht" -> {
                val purple = 0xFFA855F7.toInt()
                glowAt(c, e.x, headY + up, 66f * s, purple, (75 * glowBoost).toInt().coerceAtMost(255))
                // 여러 개의 균열 팔
                val arms = listOf(-1.9f, -1.15f, -0.5f, 0.5f, 1.15f, 1.9f)
                arms.forEachIndexed { i, a ->
                    val len = (30f + (i % 3) * 9f) * s
                    val ex = e.x + cos(a) * len
                    val ey = shY + 6f * s + sin(a) * len * 0.75f + up
                    val mx = e.x + cos(a) * len * 0.55f
                    val my = shY + 6f * s + sin(a) * len * 0.45f + up
                    line(c, e.x, shY + 6f * s + up, mx, my, withAlpha(0xFF4C1D72.toInt(), (230 * alphaMul).toInt()), 3.2f * s)
                    line(c, mx, my, ex, ey, withAlpha(0xFF6D28A8.toInt(), (220 * alphaMul).toInt()), 2.6f * s)
                    // 갈고리 손
                    line(c, ex, ey, ex + cos(a - 0.6f) * 7f * s, ey + sin(a - 0.6f) * 7f * s, withAlpha(purple, (230 * alphaMul).toInt()), 1.8f * s)
                    line(c, ex, ey, ex + cos(a + 0.6f) * 7f * s, ey + sin(a + 0.6f) * 7f * s, withAlpha(purple, (230 * alphaMul).toInt()), 1.8f * s)
                }
                // 균열 눈 (머리 위 마름모)
                path.reset()
                path.moveTo(e.x, headY + up - headR - 16f * s)
                path.lineTo(e.x + 7f * s, headY + up - headR - 9f * s)
                path.lineTo(e.x, headY + up - headR - 2f * s)
                path.lineTo(e.x - 7f * s, headY + up - headR - 9f * s)
                path.close()
                fill.color = alpha(purple, (240 * alphaMul).toInt())
                c.drawPath(path, fill)
                disc(c, e.x, headY + up - headR - 9f * s, 2.2f * s, 0xFFFFFFFF.toInt())
                // 뿔
                line(c, e.x - 5.5f * s, headY + up - headR * 0.7f, e.x - 12f * s, headY + up - headR * 2.6f, withAlpha(0xFF3B0F5E.toInt(), (240 * alphaMul).toInt()), 2.6f * s)
                line(c, e.x + 5.5f * s, headY + up - headR * 0.7f, e.x + 12f * s, headY + up - headR * 2.6f, withAlpha(0xFF3B0F5E.toInt(), (240 * alphaMul).toInt()), 2.6f * s)
                if (p2) glowAt(c, e.x, e.y - 40f * s, 130f * s, purple, 44)
            }
            "king" -> {
                val moss = 0xFF4E7A5C.toInt()
                val violet = 0xFF8B7BD8.toInt()
                glowAt(c, e.x, headY + up, 58f * s, violet, (55 * glowBoost).toInt().coerceAtMost(255))
                // 의식 망토
                path.reset()
                path.moveTo(e.x - 13f * s, shY + up)
                path.quadTo(e.x - 30f * s, e.y - 40f * s, e.x - 20f * s, e.y - 1f * s)
                path.lineTo(e.x + 20f * s, e.y - 1f * s)
                path.quadTo(e.x + 30f * s, e.y - 40f * s, e.x + 13f * s, shY + up)
                path.close()
                fill.shader = null
                fill.color = withAlpha(moss, (215 * alphaMul).toInt())
                c.drawPath(path, fill)
                stroke.color = alpha(violet, (160 * alphaMul).toInt())
                stroke.strokeWidth = 1.8f * s
                c.drawPath(path, stroke)
                // 사슴 뿔 왕관
                val antler = withAlpha(0xFFE8DFC8.toInt(), (240 * alphaMul).toInt())
                fun branch(x: Float, y: Float, dx: Float, dy: Float, len: Float) {
                    line(c, x, y, x + dx * len, y + dy * len, antler, 2.2f * s)
                    line(c, x + dx * len * 0.55f, y + dy * len * 0.55f, x + dx * len * 0.55f + dx * 6f * s, y + dy * len * 0.55f - 8f * s, antler, 1.7f * s)
                }
                branch(e.x - 4.5f * s, headY + up - headR * 0.8f, -0.5f, -1f, 20f * s)
                branch(e.x + 4.5f * s, headY + up - headR * 0.8f, 0.5f, -1f, 20f * s)
                // 의식 해골 지팡이
                val handX = e.x + fwd * 15f * s
                val handY = shY + 10f * s + up
                line(c, e.x, shY + 3f * s + up, handX, handY, withAlpha(body(skin), (255 * alphaMul).toInt()), 3.2f * s)
                line(c, handX, handY + 22f * s, handX, handY - 30f * s, 0xFF6B5B45.toInt(), 2.8f * s)
                disc(c, handX, handY - 33f * s, 5.4f * s, withAlpha(0xFFE8E2D0.toInt(), (235 * alphaMul).toInt())
                )
                disc(c, handX - 2f * s, handY - 34.5f * s, 1.5f * s, 0xFF1A1A1A.toInt())
                disc(c, handX + 2f * s, handY - 34.5f * s, 1.5f * s, 0xFF1A1A1A.toInt())
                glowAt(c, handX, handY - 33f * s, 16f * s, 0xFF7DFFB0.toInt(), 120)
                // 안개 오라
                repeat(3) { i ->
                    stroke.color = alpha(violet, (40 - i * 8).coerceAtLeast(8))
                    stroke.strokeWidth = 1.4f * s
                    c.drawCircle(e.x, e.y - 8f * s, (34f + i * 13f) * s, stroke)
                }
                if (p2) glowAt(c, e.x, e.y - 40f * s, 120f * s, violet, 46)
            }
            "olroth" -> {
                val ice = 0xFF7FE0FF.toInt()
                glowAt(c, e.x, headY + up, 62f * s, ice, (60 * glowBoost).toInt().coerceAtMost(255))
                // 룬 원형 후광
                stroke.color = alpha(ice, (150 * alphaMul).toInt())
                stroke.strokeWidth = 2f * s
                c.drawCircle(e.x, shY + up - 4f * s, 42f * s, stroke)
                repeat(8) { i ->
                    val a = i * 0.785f + e.walkPhase * 0.1f
                    line(
                        c,
                        e.x + cos(a) * 38f * s, shY + up - 4f * s + sin(a) * 38f * s,
                        e.x + cos(a) * 44f * s, shY + up - 4f * s + sin(a) * 44f * s,
                        alpha(ice, (180 * alphaMul).toInt()), 1.6f * s,
                    )
                }
                // 룬 갑옷 윤곽
                path.reset()
                path.moveTo(e.x - 11f * s, shY + up)
                path.lineTo(e.x, shY + up + 13f * s)
                path.lineTo(e.x + 11f * s, shY + up)
                path.lineTo(e.x, shY + up - 3f * s)
                path.close()
                fill.shader = null
                fill.color = withAlpha(0xFF1B3B4A.toInt(), (235 * alphaMul).toInt())
                c.drawPath(path, fill)
                stroke.color = alpha(ice, (200 * alphaMul).toInt())
                stroke.strokeWidth = 1.6f * s
                c.drawPath(path, stroke)
                // 대형 룬 방패
                val shieldX = e.x - fwd * 20f * s
                val shieldY = shY + 8f * s + up
                path.reset()
                path.moveTo(shieldX - 11f * s, shieldY - 14f * s)
                path.lineTo(shieldX + 11f * s, shieldY - 14f * s)
                path.quadTo(shieldX + 12f * s, shieldY + 6f * s, shieldX, shieldY + 18f * s)
                path.quadTo(shieldX - 12f * s, shieldY + 6f * s, shieldX - 11f * s, shieldY - 14f * s)
                path.close()
                fill.color = withAlpha(0xFF12303C.toInt(), (240 * alphaMul).toInt())
                c.drawPath(path, fill)
                stroke.color = alpha(ice, (215 * alphaMul).toInt())
                stroke.strokeWidth = 2f * s
                c.drawPath(path, stroke)
                disc(c, shieldX, shieldY, 4.2f * s, alpha(ice, (200 * alphaMul).toInt()))
                // 서리 대검 (반대손)
                val swing = facing - 1.1f + (1f - e.attackAnim) * 2.1f
                val handX = e.x + cos(swing) * 14f * s
                val handY = shY + 8f * s + sin(swing) * 12f * s + up
                line(c, e.x, shY + 2f * s + up, handX, handY, withAlpha(body(skin), (255 * alphaMul).toInt()), 3.2f * s)
                c.save()
                c.translate(handX, handY)
                c.rotate(Math.toDegrees((swing + 0.55f).toDouble()).toFloat())
                path.reset()
                path.moveTo(4f * s, -3.4f * s)
                path.lineTo(44f * s, -1.6f * s)
                path.lineTo(50f * s, 0f)
                path.lineTo(44f * s, 1.6f * s)
                path.lineTo(4f * s, 3.4f * s)
                path.close()
                fill.color = alpha(0xFFD6F6FF.toInt(), (240 * alphaMul).toInt())
                c.drawPath(path, fill)
                c.restore()
                if (p2) glowAt(c, e.x, e.y - 40f * s, 125f * s, ice, 46)
                if (e.shield > 0) {
                    stroke.color = alpha(ice, (120 + 80 * sin(w.elapsed.toFloat() * 6f)).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 3f * s
                    c.drawCircle(e.x, e.y - 40f * s, 52f * s, stroke)
                }
            }
            else -> {
                // 자로크 외 기본
                val gold = 0xFFFFCF6A.toInt()
                val teal = 0xFF5FE0D0.toInt()
                glowAt(c, e.x, headY + up, 64f * s, gold, (60 * glowBoost).toInt().coerceAtMost(255))
                // 등 뒤 시간 고리 + 모래시계
                stroke.color = alpha(teal, (140 * alphaMul).toInt())
                stroke.strokeWidth = 2f * s
                c.drawCircle(e.x, shY + up - 6f * s, 40f * s, stroke)
                c.drawCircle(e.x, shY + up - 6f * s, 30f * s, stroke)
                val hgX = e.x
                val hgY = shY + up - 6f * s
                stroke.color = alpha(gold, (230 * alphaMul).toInt())
                stroke.strokeWidth = 2.4f * s
                c.drawCircle(hgX, hgY, 13f * s, stroke)
                path.reset()
                path.moveTo(hgX - 7f * s, hgY - 8f * s)
                path.lineTo(hgX + 7f * s, hgY - 8f * s)
                path.lineTo(hgX, hgY)
                path.close()
                fill.color = alpha(gold, (200 * alphaMul).toInt())
                c.drawPath(path, fill)
                path.reset()
                path.moveTo(hgX - 7f * s, hgY + 8f * s)
                path.lineTo(hgX + 7f * s, hgY + 8f * s)
                path.lineTo(hgX, hgY)
                path.close()
                fill.color = alpha(0xFFFFF0B0.toInt(), (220 * alphaMul).toInt())
                c.drawPath(path, fill)
                // 황금 어깨 장식
                line(c, e.x - 12f * s, shY + up + 2f * s, e.x - 3f * s, shY + up - 2f * s, alpha(gold, (235 * alphaMul).toInt()), 3f * s)
                line(c, e.x + 12f * s, shY + up + 2f * s, e.x + 3f * s, shY + up - 2f * s, alpha(gold, (235 * alphaMul).toInt()), 3f * s)
                // 황금 지팡이
                val handX = e.x + fwd * 15f * s
                val handY = shY + 10f * s + up
                line(c, e.x, shY + 3f * s + up, handX, handY, withAlpha(body(skin), (255 * alphaMul).toInt()), 3.2f * s)
                line(c, handX, handY + 24f * s, handX, handY - 34f * s, alpha(gold, (235 * alphaMul).toInt()), 2.8f * s)
                disc(c, handX, handY - 37f * s, 4.6f * s, alpha(0xFFFFF3C4.toInt(), (245 * alphaMul).toInt()))
                glowAt(c, handX, handY - 37f * s, 20f * s, gold, 120)
                if (p2) {
                    repeat(4) { i ->
                        val a = i * 1.5708f + w.elapsed.toFloat()
                        stroke.color = alpha(teal, (150 * alphaMul).toInt())
                        stroke.strokeWidth = 1.6f * s
                        c.drawCircle(e.x + cos(a) * 36f * s, shY + up - 6f * s + sin(a) * 36f * s, 3.2f * s, stroke)
                    }
                    glowAt(c, e.x, e.y - 40f * s, 130f * s, gold, 44)
                }
            }
        }
    }

    private fun drawBow(c: Canvas, x: Float, y: Float, s: Float, facing: Float, attack: Float, body: Int) {
        c.save()
        c.translate(x, y)
        c.rotate(Math.toDegrees(facing.toDouble()).toFloat())
        path.reset()
        path.moveTo(-3f * s, -13f * s)
        path.quadTo(9f * s, 0f, -3f * s, 13f * s)
        stroke.color = 0xFFD9C08A.toInt()
        stroke.strokeWidth = 2.2f * s
        c.drawPath(path, stroke)

        val pull = 2f * s + attack * 11f * s
        stroke.color = 0xCCEFE8D8.toInt()
        stroke.strokeWidth = 1.1f * s
        c.drawLine(-3f * s, -13f * s, -3f * s - pull, 0f, stroke)
        c.drawLine(-3f * s - pull, 0f, -3f * s, 13f * s, stroke)

        if (attack > 0.35f) {
            stroke.color = alpha(0xFFBFEFFF.toInt(), 220)
            stroke.strokeWidth = 1.6f * s
            c.drawLine(-3f * s - pull, 0f, 18f * s, 0f, stroke)
        }
        c.restore()
    }

    /** 쇠뇌: 가로로 긴 몸체 + 앞쪽 T자 활대 + 볼트 */
    private fun drawCrossbow(c: Canvas, x: Float, y: Float, s: Float, facing: Float, attack: Float, accent: Int) {
        c.save()
        c.translate(x, y)
        c.rotate(Math.toDegrees(facing.toDouble()).toFloat())
        val recoil = -attack * 3f * s

        // 개머리판 + 몸체
        stroke.color = 0xFF6B5236.toInt()
        stroke.strokeWidth = 3.4f * s
        c.drawLine(-13f * s + recoil, 1.2f * s, 12f * s + recoil, 0f, stroke)
        stroke.color = 0xFF8A6A46.toInt()
        stroke.strokeWidth = 2f * s
        c.drawLine(-9f * s + recoil, -2.4f * s, 7f * s + recoil, -2.6f * s, stroke)

        // T자 활대 (앞쪽에서 위아래로 뻗은 두 팔)
        stroke.color = 0xFFB79A6C.toInt()
        stroke.strokeWidth = 2.6f * s
        path.reset()
        path.moveTo(10f * s + recoil, -12f * s)
        path.quadTo(17f * s + recoil, -6f * s, 14f * s + recoil, 0f)
        path.quadTo(17f * s + recoil, 6f * s, 10f * s + recoil, 12f * s)
        c.drawPath(path, stroke)

        // 시위
        stroke.color = 0xDDE8E1D2.toInt()
        stroke.strokeWidth = 0.9f * s
        val draw = 1.5f * s + attack * 3f * s
        c.drawLine(10f * s + recoil, -12f * s, 12f * s + recoil - draw, 0f, stroke)
        c.drawLine(12f * s + recoil - draw, 0f, 10f * s + recoil, 12f * s, stroke)

        // 볼트
        if (attack > 0.25f) {
            stroke.color = alpha(0xFFDFF6FF.toInt(), 235)
            stroke.strokeWidth = 1.7f * s
            c.drawLine(6f * s + recoil, 0f, 26f * s + recoil, 0f, stroke)
            fill.color = alpha(accent, 230)
            c.drawCircle(26f * s + recoil, 0f, 2.4f * s, fill)
        }
        c.restore()
    }

    private fun drawStaff(c: Canvas, x: Float, y: Float, s: Float, facing: Float, attack: Float, accent: Int, body: Int) {
        val tipX = x + cos(facing) * 9f * s
        val tipY = y - 27f * s
        stroke.color = 0xFF8A6A46.toInt()
        stroke.strokeWidth = 2.4f * s
        c.drawLine(x - cos(facing) * 6f * s, y + 19f * s, tipX, tipY, stroke)

        val pulse = 4.5f * s + attack * 3f * s
        fill.shader = RadialGradient(
            tipX, tipY, 14f * s + attack * 8f * s,
            intArrayOf(alpha(accent, 230), alpha(accent, 60), 0x00000000),
            null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(tipX, tipY, 14f * s + attack * 8f * s, fill)
        fill.shader = null
        fill.color = alpha(0xFFFFFFFF.toInt(), 210)
        c.drawCircle(tipX, tipY, pulse * 0.42f, fill)
        // 지팡이 끝 장식
        stroke.color = 0xFFC9A76A.toInt()
        stroke.strokeWidth = 1.6f * s
        c.drawCircle(tipX, tipY, 4.6f * s, stroke)
    }

    /** 완드·홀처럼 짧은 막대 + 끝 보석 */
    private fun drawShortRod(
        c: Canvas, x: Float, y: Float, s: Float, facing: Float, attack: Float,
        accent: Int, length: Float,
    ) {
        val tipX = x + cos(facing) * length * s * 0.5f
        val tipY = y - length * s + attack * 2f * s
        stroke.color = 0xFF8A6A46.toInt()
        stroke.strokeWidth = 2.3f * s
        c.drawLine(x - cos(facing) * 4f * s, y + 7f * s, tipX, tipY, stroke)
        fill.shader = RadialGradient(
            tipX, tipY, 11f * s,
            intArrayOf(alpha(accent, 240), alpha(accent, 70), 0x00000000), null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(tipX, tipY, 11f * s, fill)
        fill.shader = null
        fill.color = alpha(0xFFFFFFFF.toInt(), 200)
        c.drawCircle(tipX, tipY, 2.4f * s, fill)
    }

    /** 육척봉·창: 긴 자루 (+ 창끝) */
    private fun drawPolearm(
        c: Canvas, x: Float, y: Float, s: Float, angle: Float, attack: Float,
        accent: Int, length: Float, spear: Boolean,
    ) {
        val tipX = x + cos(angle) * length * s
        val tipY = y + sin(angle) * length * s
        stroke.color = 0xFF8A6A46.toInt()
        stroke.strokeWidth = 2.4f * s
        c.drawLine(x - cos(angle) * 10f * s, y - sin(angle) * 10f * s, tipX, tipY, stroke)
        stroke.color = 0xFFC9A76A.toInt()
        stroke.strokeWidth = 1.4f * s
        c.drawLine(x + cos(angle) * length * 0.55f * s, y + sin(angle) * length * 0.55f * s, tipX, tipY, stroke)
        if (spear) {
            path.reset()
            path.moveTo(tipX + cos(angle) * 9f * s, tipY + sin(angle) * 9f * s)
            path.lineTo(tipX + cos(angle + 1.7f) * 3.4f * s, tipY + sin(angle + 1.7f) * 3.4f * s)
            path.lineTo(tipX + cos(angle - 1.7f) * 3.4f * s, tipY + sin(angle - 1.7f) * 3.4f * s)
            path.close()
            fill.color = 0xFFC9D2DE.toInt()
            c.drawPath(path, fill)
        } else {
            fill.color = alpha(accent, 200)
            c.drawCircle(tipX, tipY, 2.6f * s, fill)
        }
    }

    private fun drawBlade(
        c: Canvas, x: Float, y: Float, s: Float, swing: Float, attack: Float,
        accent: Int, body: Int, length: Float, wide: Boolean,
    ) {
        val len = length * s
        val tipX = x + cos(swing) * len
        val tipY = y + sin(swing) * len
        stroke.color = 0xFFC9D2DE.toInt()
        stroke.strokeWidth = if (wide) 4.2f * s else 2.6f * s
        c.drawLine(x, y, tipX, tipY, stroke)
        stroke.color = alpha(accent, 180)
        stroke.strokeWidth = 1.2f * s
        c.drawLine(
            x + cos(swing + 1.57f) * 3.4f * s, y + sin(swing + 1.57f) * 3.4f * s,
            x + cos(swing) * len * 0.62f, y + sin(swing) * len * 0.62f,
            stroke,
        )
        // 손잡이
        stroke.color = 0xFF6B5236.toInt()
        stroke.strokeWidth = 2.4f * s
        c.drawLine(x - cos(swing) * 4f * s, y - sin(swing) * 4f * s, x + cos(swing) * 4f * s, y + sin(swing) * 4f * s, stroke)
        if (attack > 0.4f) {
            stroke.color = alpha(0xFFFFFFFF.toInt(), 150)
            stroke.strokeWidth = 1.4f * s
            c.drawLine(x + cos(swing) * len * 0.8f, y + sin(swing) * len * 0.8f, tipX, tipY, stroke)
        }
    }

    private fun drawAxe(c: Canvas, x: Float, y: Float, s: Float, swing: Float, attack: Float, accent: Int) {
        val len = 27f * s
        val tipX = x + cos(swing) * len
        val tipY = y + sin(swing) * len
        stroke.color = 0xFF6B5236.toInt()
        stroke.strokeWidth = 2.4f * s
        c.drawLine(x - cos(swing) * 5f * s, y - sin(swing) * 5f * s, tipX, tipY, stroke)

        path.reset()
        val ax = tipX + cos(swing + 1.57f) * 2f * s
        val ay = tipY + sin(swing + 1.57f) * 2f * s
        path.moveTo(ax, ay)
        path.lineTo(ax + cos(swing) * 8f * s + cos(swing + 1.57f) * 7f * s,
            ay + sin(swing) * 8f * s + sin(swing + 1.57f) * 7f * s)
        path.lineTo(ax + cos(swing) * 3f * s + cos(swing + 1.57f) * 11f * s,
            ay + sin(swing) * 3f * s + sin(swing + 1.57f) * 11f * s)
        path.lineTo(ax, ay)
        path.close()
        fill.color = 0xFFC9D2DE.toInt()
        c.drawPath(path, fill)
        stroke.color = alpha(accent, 160)
        stroke.strokeWidth = 1f * s
        c.drawPath(path, stroke)
        if (attack > 0.4f) {
            stroke.color = alpha(0xFFFFFFFF.toInt(), 130)
            stroke.strokeWidth = 1.2f * s
            c.drawLine(tipX, tipY, tipX + cos(swing + 1.57f) * 9f * s, tipY + sin(swing + 1.57f) * 9f * s, stroke)
        }
    }

    private fun drawMace(c: Canvas, x: Float, y: Float, s: Float, swing: Float, attack: Float, accent: Int) {
        val len = 26f * s
        val tipX = x + cos(swing) * len
        val tipY = y + sin(swing) * len
        stroke.color = 0xFF6B5236.toInt()
        stroke.strokeWidth = 2.6f * s
        c.drawLine(x - cos(swing) * 5f * s, y - sin(swing) * 5f * s, tipX, tipY, stroke)
        fill.color = 0xFFB8C0CC.toInt()
        c.drawCircle(tipX, tipY, 6.4f * s, fill)
        stroke.color = alpha(accent, 190)
        stroke.strokeWidth = 1.2f * s
        c.drawCircle(tipX, tipY, 7.4f * s + attack * 2f * s, stroke)
        for (i in 0 until 4) {
            val ang = swing + i * 1.57f
            stroke.color = 0xFFC9D2DE.toInt()
            stroke.strokeWidth = 1.6f * s
            c.drawLine(tipX, tipY, tipX + cos(ang) * 8.4f * s, tipY + sin(ang) * 8.4f * s, stroke)
        }
    }

    private fun drawClaw(c: Canvas, x: Float, y: Float, s: Float, swing: Float, attack: Float, accent: Int) {
        stroke.color = 0xFFC9D2DE.toInt()
        stroke.strokeWidth = 1.8f * s
        for (i in -1..1) {
            val a = swing + i * 0.34f
            val len = (15f + attack * 4f) * s
            path.reset()
            path.moveTo(x, y)
            path.quadTo(
                x + cos(a) * len * 0.6f + cos(a + 1.57f) * 2f * s,
                y + sin(a) * len * 0.6f + sin(a + 1.57f) * 2f * s,
                x + cos(a) * len, y + sin(a) * len,
            )
            c.drawPath(path, stroke)
        }
        fill.color = alpha(accent, 120)
        c.drawCircle(x, y, 3.4f * s, fill)
    }

    private fun drawFlail(c: Canvas, x: Float, y: Float, s: Float, swing: Float, attack: Float, accent: Int) {
        val len = 18f * s
        val headX = x + cos(swing) * len
        val headY = y + sin(swing) * len
        stroke.color = 0xFF6B5236.toInt()
        stroke.strokeWidth = 2.4f * s
        c.drawLine(x - cos(swing) * 4f * s, y - sin(swing) * 4f * s, x + cos(swing) * 9f * s, y + sin(swing) * 9f * s, stroke)
        stroke.color = 0xFF9AA3B0.toInt()
        stroke.strokeWidth = 1f * s
        c.drawLine(x + cos(swing) * 9f * s, y + sin(swing) * 9f * s, headX, headY, stroke)
        fill.color = 0xFFB8C0CC.toInt()
        c.drawCircle(headX, headY, 4.6f * s, fill)
        stroke.color = alpha(accent, 200)
        stroke.strokeWidth = 1f * s
        c.drawCircle(headX, headY, 6f * s + attack * 2f * s, stroke)
    }

    private fun drawShield(c: Canvas, x: Float, y: Float, s: Float, facing: Float, accent: Int) {
        c.save()
        c.translate(x, y)
        c.rotate(Math.toDegrees(facing.toDouble()).toFloat())
        path.reset()
        path.moveTo(0f, -12f * s)
        path.lineTo(7f * s, -8f * s)
        path.lineTo(7f * s, 4f * s)
        path.quadTo(0f, 13f * s, -7f * s, 4f * s)
        path.lineTo(-7f * s, -8f * s)
        path.close()
        fill.color = 0xFF3C4552.toInt()
        c.drawPath(path, fill)
        stroke.color = alpha(accent, 210)
        stroke.strokeWidth = 1.6f * s
        c.drawPath(path, stroke)
        c.restore()
    }

    // -------------------------------------------------------------- 효과

    private fun drawGroundFx(c: Canvas, w: BattleWorld) {
        for (fx in w.effects) {
            val t = 1f - (fx.life / max(0.0001f, fx.maxLife)).coerceIn(0f, 1f)
            when (fx.kind) {
                FxKind.RING, FxKind.SHOCKWAVE -> {
                    val r = fx.radius * (0.25f + t * 0.95f)
                    stroke.color = alpha(fx.color, ((1f - t) * 200).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 2.2f * fx.width * (1f - t * 0.5f) + 0.4f
                    c.drawOval(fx.x - r, fx.y - r * 0.42f, fx.x + r, fx.y + r * 0.42f, stroke)
                    if (fx.kind == FxKind.SHOCKWAVE) {
                        stroke.strokeWidth = 1f
                        stroke.color = alpha(fx.color, ((1f - t) * 120).toInt().coerceIn(0, 255))
                        for (i in 0 until 6) {
                            val a = i * 1.047f + fx.seed % 10 * 0.1f
                            c.drawLine(
                                fx.x + cos(a) * r * 0.4f, fx.y + sin(a) * r * 0.4f * 0.42f,
                                fx.x + cos(a) * r * 1.15f, fx.y + sin(a) * r * 1.15f * 0.42f, stroke,
                            )
                        }
                    }
                }
                FxKind.AURA -> {
                    val r = fx.radius * (0.6f + t * 0.8f)
                    stroke.color = alpha(fx.color, ((1f - t) * 150).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 2.4f
                    c.drawOval(fx.x - r, fx.y - r * 0.5f, fx.x + r, fx.y + r * 0.5f, stroke)
                    fill.shader = RadialGradient(
                        fx.x, fx.y - 10f, r,
                        intArrayOf(alpha(fx.color, ((1f - t) * 70).toInt().coerceIn(0, 255)), 0x00000000),
                        null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(fx.x, fx.y - 10f, r, fill)
                    fill.shader = null
                }
                FxKind.IMPACT -> {
                    // 내려찍기 착탄점: 바닥이 갈라지고 파편이 튄다
                    val r = fx.radius * (0.5f + t * 0.7f)
                    fill.color = alpha(fx.color, ((1f - t) * 90).toInt().coerceIn(0, 255))
                    c.drawOval(fx.x - r, fx.y - r * 0.42f, fx.x + r, fx.y + r * 0.42f, fill)
                    stroke.color = alpha(0xFFFFF3D6.toInt(), ((1f - t) * 220).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 2.6f * fx.width * (1f - t * 0.4f)
                    c.drawOval(fx.x - r, fx.y - r * 0.42f, fx.x + r, fx.y + r * 0.42f, stroke)
                    stroke.strokeWidth = 1.6f
                    for (i in 0 until 7) {
                        val a = i * 0.897f + (fx.seed % 11) * 0.07f
                        c.drawLine(
                            fx.x + cos(a) * r * 0.2f, fx.y + sin(a) * r * 0.2f * 0.42f,
                            fx.x + cos(a) * r * (0.9f + t * 0.4f), fx.y + sin(a) * r * (0.9f + t * 0.4f) * 0.42f,
                            stroke,
                        )
                    }
                }
                FxKind.GROUND_CRACK -> {
                    // 보스 강타 예고
                    val r = fx.radius
                    fill.color = alpha(0xFFFF3B2E.toInt(), (18 + t * 30).toInt())
                    c.drawOval(fx.x - r, fx.y - r * 0.42f, fx.x + r, fx.y + r * 0.42f, fill)
                    stroke.color = alpha(0xFFFF5A3D.toInt(), (120 + t * 110).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 2.4f
                    c.drawOval(fx.x - r, fx.y - r * 0.42f, fx.x + r, fx.y + r * 0.42f, stroke)
                    stroke.color = alpha(0xFFFF8A5D.toInt(), 150)
                    stroke.strokeWidth = 1.3f
                    for (i in 0 until 8) {
                        val a = i * 0.785f
                        c.drawLine(
                            fx.x + cos(a) * r * 0.15f, fx.y + sin(a) * r * 0.15f * 0.42f,
                            fx.x + cos(a) * r * (0.55f + t * 0.45f), fx.y + sin(a) * r * (0.55f + t * 0.45f) * 0.42f,
                            stroke,
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    private fun drawAirFx(c: Canvas, w: BattleWorld) {
        for (fx in w.effects) {
            val t = 1f - (fx.life / max(0.0001f, fx.maxLife)).coerceIn(0f, 1f)
            when (fx.kind) {
                FxKind.SLASH -> drawSlash(c, fx, t)
                FxKind.ICE_CONE -> drawIceCone(c, fx, t)
                FxKind.BOLT -> drawBolt(c, fx, t)
                FxKind.THRUST -> drawThrust(c, fx, t)
                FxKind.PILLAR -> drawPillar(c, fx, t)
                FxKind.MINION_HIT -> drawStarBurst(c, fx, t)
                FxKind.METEOR -> drawMeteor(c, fx, t)
                FxKind.AFTERIMAGE -> drawAfterImage(c, fx, t)
                FxKind.STREAK -> drawStreak(c, fx, t)
                FxKind.CYCLONE -> Unit
                else -> Unit
            }
        }
    }

    /** 점멸 타격 잔상: 지나온 자리에 사람 형체가 옅게 남는다. */
    private fun drawAfterImage(c: Canvas, fx: Fx, t: Float) {
        val fade = (1f - t).coerceIn(0f, 1f)
        if (fade <= 0.03f) return
        val k = 0.35f + fx.width * 0.45f
        val a = (120 * fade).toInt().coerceIn(0, 255)
        val body = withAlpha(fx.color, a)
        val accent = withAlpha(0xFFFFFFFF.toInt(), (70 * fade).toInt())
        val s = 1.62f
        val legLen = 17f * s
        val torso = 19f * s
        val headR = 6.4f * s
        val hipY = fx.y - legLen
        val shY = hipY - torso
        val headY = shY - headR * 1.15f
        stroke.color = body
        stroke.strokeWidth = 3.2f * s * k
        c.drawLine(fx.x, hipY, fx.x - 4.2f * s, fx.y, stroke)
        c.drawLine(fx.x, hipY, fx.x + 4.2f * s, fx.y, stroke)
        c.drawLine(fx.x, hipY, fx.x, shY, stroke)
        c.drawCircle(fx.x, headY, headR * k, stroke)
        // 뻗은 팔과 무기 자리
        val fwd = if (cos(fx.angle) >= 0f) 1f else -1f
        c.drawLine(fx.x, shY + 2f * s, fx.x + fwd * 13f * s, shY - 3f * s, stroke)
        stroke.color = accent
        stroke.strokeWidth = 1.6f * s * k
        c.drawLine(fx.x + fwd * 13f * s, shY - 3f * s, fx.x + fwd * 24f * s, shY - 12f * s, stroke)
    }

    /** 순간이동 궤적을 잇는 번쩍이는 섬광 선 */
    private fun drawStreak(c: Canvas, fx: Fx, t: Float) {
        val fade = (1f - t).coerceIn(0f, 1f)
        if (fade <= 0.03f) return
        stroke.color = alpha(fx.color, (170 * fade).toInt())
        stroke.strokeWidth = 4.2f * fx.width
        c.drawLine(fx.x, fx.y, fx.x2, fx.y2, stroke)
        stroke.color = alpha(0xFFFFFFFF.toInt(), (210 * fade).toInt())
        stroke.strokeWidth = 1.4f * fx.width
        c.drawLine(fx.x, fx.y, fx.x2, fx.y2, stroke)
    }

    private fun drawSlash(c: Canvas, fx: Fx, t: Float) {
        val spread = 1.05f
        val startA = fx.angle - spread + t * 0.5f
        val endA = fx.angle + spread * 0.55f + t * 0.9f
        val r = fx.radius * (0.75f + t * 0.45f)
        for (i in 0 until 3) {
            stroke.color = alpha(fx.color, ((1f - t) * (160 - i * 40)).toInt().coerceIn(0, 255))
            stroke.strokeWidth = (4.6f - i * 1.2f) * fx.width
            rect.set(fx.x - r, fx.y - r, fx.x + r, fx.y + r)
            c.drawArc(rect, Math.toDegrees(startA.toDouble()).toFloat(), Math.toDegrees((endA - startA).toDouble()).toFloat(), false, stroke)
        }
    }

    private fun drawIceCone(c: Canvas, fx: Fx, t: Float) {
        val dir = fx.angle
        for (i in 0 until 7) {
            val spread = (i - 3) * 0.24f
            val a = dir + spread
            val len = fx.radius * (0.55f + ((i * 37 + fx.seed) % 60) / 100f) * (0.5f + t * 0.9f)
            val w = 3.4f
            val tipX = fx.x + cos(a) * len
            val tipY = fx.y + sin(a) * len * 0.78f
            path.reset()
            path.moveTo(fx.x, fx.y)
            path.lineTo(tipX + cos(a + 1.4f) * w, tipY + sin(a + 1.4f) * w)
            path.lineTo(tipX, tipY)
            path.lineTo(tipX + cos(a - 1.4f) * w, tipY + sin(a - 1.4f) * w)
            path.close()
            fill.color = alpha(fx.color, ((1f - t) * 210).toInt().coerceIn(0, 255))
            c.drawPath(path, fill)
            stroke.color = alpha(0xFFFFFFFF.toInt(), ((1f - t) * 160).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 0.9f
            c.drawLine(fx.x, fx.y, tipX, tipY, stroke)
        }
        fill.color = alpha(0xFFFFFFFF.toInt(), ((1f - t) * 120).toInt().coerceIn(0, 255))
        c.drawCircle(fx.x, fx.y, 4f * (1f - t) + 1f, fill)
    }

    private fun drawBolt(c: Canvas, fx: Fx, t: Float) {
        val segments = 7
        val dx = fx.x2 - fx.x
        val dy = fx.y2 - fx.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val nx = -dy / len
        val ny = dx / len
        path.reset()
        path.moveTo(fx.x, fx.y)
        for (i in 1 until segments) {
            val p = i / segments.toFloat()
            val jitter = (((fx.seed shr (i * 3)) and 0xFF) / 255f - 0.5f) * 34f * (1f - abs(p - 0.5f))
            path.lineTo(fx.x + dx * p + nx * jitter, fx.y + dy * p + ny * jitter)
        }
        path.lineTo(fx.x2, fx.y2)

        val alphaFade = (1f - t).coerceIn(0f, 1f)
        stroke.color = alpha(fx.color, (60 * alphaFade).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 14f * fx.width * alphaFade.coerceAtLeast(0.2f)
        c.drawPath(path, stroke)
        stroke.color = alpha(fx.color, (170 * alphaFade).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 5.6f * fx.width
        c.drawPath(path, stroke)
        stroke.color = alpha(0xFFFFFFFF.toInt(), (230 * alphaFade).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 2f * fx.width
        c.drawPath(path, stroke)
    }

    /** 창·단검 찌르기: 손에서 표적까지 곧게 뻗는 잔상 */
    private fun drawThrust(c: Canvas, fx: Fx, t: Float) {
        val a = (1f - t).coerceIn(0f, 1f)
        val endX = fx.x + (fx.x2 - fx.x) * (0.4f + 0.6f * (1f - t))
        val endY = fx.y + (fx.y2 - fx.y) * (0.4f + 0.6f * (1f - t))
        stroke.color = alpha(fx.color, (90 * a).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 9f * fx.width * a.coerceAtLeast(0.15f)
        c.drawLine(fx.x, fx.y, endX, endY, stroke)
        stroke.color = alpha(fx.color, (220 * a).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 2.6f * fx.width
        c.drawLine(fx.x, fx.y, endX, endY, stroke)
        // 창끝
        path.reset()
        val ang = fx.angle
        path.moveTo(endX + cos(ang) * 16f, endY + sin(ang) * 16f)
        path.lineTo(endX + cos(ang + 2.5f) * 6f, endY + sin(ang + 2.5f) * 6f)
        path.lineTo(endX + cos(ang - 2.5f) * 6f, endY + sin(ang - 2.5f) * 6f)
        path.close()
        fill.color = alpha(0xFFFFFFFF.toInt(), (200 * a).toInt().coerceIn(0, 255))
        c.drawPath(path, fill)
    }

    private fun drawPillar(c: Canvas, fx: Fx, t: Float) {
        val a = (1f - t).coerceIn(0f, 1f)
        val w = fx.radius * (0.6f + (1f - a) * 0.5f)
        fill.shader = LinearGradient(
            fx.x, fx.y - 820f, fx.x, fx.y,
            intArrayOf(0x00FFFFFF, alpha(fx.color, (200 * a).toInt()), alpha(0xFFFFFFFF.toInt(), (240 * a).toInt())),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(fx.x - w / 2f, fx.y - 840f, fx.x + w / 2f, fx.y, fill)
        fill.shader = null
        fill.color = alpha(fx.color, (150 * a).toInt().coerceIn(0, 255))
        c.drawCircle(fx.x, fx.y, w, fill)
        fill.color = alpha(0xFFFFFFFF.toInt(), (220 * a).toInt().coerceIn(0, 255))
        c.drawCircle(fx.x, fx.y, w * 0.35f, fill)
    }

    /** 혜성: 하늘에서 떨어지는 얼음 덩어리 */
    private fun drawMeteor(c: Canvas, fx: Fx, t: Float) {
        val h = (1f - t).coerceIn(0f, 1f)
        val drift = ((fx.seed % 40) - 20) / 20f * 16f
        val px = fx.x + drift * h
        val py = fx.y - h * 470f
        val tail = 120f * (0.35f + h * 0.9f)

        stroke.color = alpha(fx.color, (150 * h + 40).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 7f * h + 1.5f
        c.drawLine(px + drift * 0.18f, py - tail, px, py, stroke)

        fill.shader = RadialGradient(
            px, py, 34f,
            intArrayOf(0xFFFFFFFF.toInt(), alpha(fx.color, 240), alpha(fx.color, 90), 0x00000000),
            floatArrayOf(0f, 0.3f, 0.65f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(px, py, 34f, fill)
        fill.shader = null
        fill.color = alpha(0xFFE8FCFF.toInt(), 240)
        c.drawCircle(px, py, 11f, fill)

        stroke.color = alpha(0xFFFFFFFF.toInt(), 200)
        stroke.strokeWidth = 1.4f
        for (i in 0 until 5) {
            val a = i * 1.256f + (fx.seed % 17) * 0.1f
            c.drawLine(px, py, px + cos(a) * 26f, py + sin(a) * 26f, stroke)
        }
    }

    private fun drawStarBurst(c: Canvas, fx: Fx, t: Float) {
        val a = (1f - t).coerceIn(0f, 1f)
        stroke.color = alpha(fx.color, (200 * a).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 2f
        for (i in 0 until 6) {
            val ang = i * 1.047f + fx.seed % 7 * 0.2f
            val r = fx.radius * (0.6f + t)
            c.drawLine(fx.x + cos(ang) * r * 0.3f, fx.y + sin(ang) * r * 0.3f, fx.x + cos(ang) * r, fx.y + sin(ang) * r, stroke)
        }
    }

    // -------------------------------------------------------------- 투사체

    private fun drawProjectiles(c: Canvas, w: BattleWorld) {
        for (p in w.projectiles) {
            when (p.kind) {
                ProjKind.ICE_ARROW -> {
                    drawTrail(c, p, 0xFF8FE6FF.toInt(), 13f)
                    stroke.color = 0xFFBFF4FF.toInt()
                    stroke.strokeWidth = 3f
                    c.drawLine(p.x - p.dirX * 16f, p.y - p.dirY * 16f, p.x, p.y, stroke)
                    fill.shader = RadialGradient(
                        p.x, p.y, 18f,
                        intArrayOf(0xFFE8FCFF.toInt(), 0x338FE6FF, 0x00000000), null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 18f, fill)
                    fill.shader = null
                }
                ProjKind.LIGHT_ARROW -> {
                    drawTrail(c, p, 0xFFFFE272.toInt(), 10f)
                    stroke.color = 0xFFFFF8CC.toInt()
                    stroke.strokeWidth = 2.4f
                    c.drawLine(p.x - p.dirX * 22f, p.y - p.dirY * 22f, p.x, p.y, stroke)
                    fill.shader = RadialGradient(
                        p.x, p.y, 19f,
                        intArrayOf(0xFFFFFFE6.toInt(), 0x44FFE272, 0x00000000), null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 19f, fill)
                    fill.shader = null
                }
                ProjKind.FIRE_ORB -> {
                    drawTrail(c, p, 0xFFFF7B3C.toInt(), 17f)
                    fill.shader = RadialGradient(
                        p.x, p.y, 23f,
                        intArrayOf(0xFFFFF2C4.toInt(), 0xFFFF9A3D.toInt(), 0x33FF5A1E.toInt(), 0x00000000),
                        floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 23f, fill)
                    fill.shader = null
                }
                ProjKind.CHAOS_ORB -> {
                    drawTrail(c, p, 0xFFC182FF.toInt(), 14f)
                    fill.shader = RadialGradient(
                        p.x, p.y, 22f,
                        intArrayOf(0xFFF0D9FF.toInt(), 0xFF9B5DE5.toInt(), 0x33C182FF, 0x00000000),
                        floatArrayOf(0f, 0.4f, 0.75f, 1f), Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 22f, fill)
                    fill.shader = null
                    stroke.color = 0x66E0C2FF
                    stroke.strokeWidth = 1.2f
                    c.drawCircle(p.x, p.y, 27f, stroke)
                }
                ProjKind.MINION_ORB -> {
                    drawTrail(c, p, 0xFFFFE9A8.toInt(), 12f)
                    fill.shader = RadialGradient(
                        p.x, p.y, 17f,
                        intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD98A.toInt(), 0x00000000),
                        null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 17f, fill)
                    fill.shader = null
                    fill.color = 0xCCFFFFFF.toInt()
                    c.drawCircle(p.x, p.y, 3.6f, fill)
                }
                ProjKind.BOLT -> {
                    drawTrail(c, p, 0xFFCFE8FF.toInt(), 9f)
                    val len = 26f
                    stroke.color = 0xFF5C4A33.toInt()
                    stroke.strokeWidth = 2.6f
                    c.drawLine(p.x - p.dirX * len, p.y - p.dirY * len, p.x - p.dirX * 8f, p.y - p.dirY * 8f, stroke)
                    stroke.color = 0xFFE2EAF2.toInt()
                    stroke.strokeWidth = 1.8f
                    c.drawLine(p.x - p.dirX * 8f, p.y - p.dirY * 8f, p.x + p.dirX * 4f, p.y + p.dirY * 4f, stroke)
                    fill.shader = RadialGradient(
                        p.x + p.dirX * 4f, p.y + p.dirY * 4f, 16f,
                        intArrayOf(alpha(p.element.color, 245), alpha(p.element.color, 80), 0x00000000),
                        null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x + p.dirX * 4f, p.y + p.dirY * 4f, 16f, fill)
                    fill.shader = null
                }
                ProjKind.ARROW -> {
                    // 깃털 달린 화살: 자루 + 촉 + 꼬리 깃
                    drawTrail(c, p, p.element.color, 8f)
                    val tailX = p.x - p.dirX * 26f
                    val tailY = p.y - p.dirY * 26f
                    stroke.color = 0xFFE8DCC0.toInt()
                    stroke.strokeWidth = 2f
                    c.drawLine(tailX, tailY, p.x, p.y, stroke)
                    // 촉
                    path.reset()
                    val ang = Math.toDegrees(kotlin.math.atan2(p.dirY, p.dirX).toDouble()).toFloat()
                    c.save()
                    c.rotate(ang, p.x, p.y)
                    path.moveTo(8f, 0f)
                    path.lineTo(-1.5f, -3.2f)
                    path.lineTo(-1.5f, 3.2f)
                    path.close()
                    fill.color = 0xFFDCE6F2.toInt()
                    c.drawPath(path, fill)
                    // 깃
                    stroke.color = alpha(p.element.color, 230)
                    stroke.strokeWidth = 1.6f
                    for (i in 0 until 3) {
                        val bx = tailX + p.dirX * i * 3.4f
                        val by = tailY + p.dirY * i * 3.4f
                        c.drawLine(bx, by, bx - p.dirX * 4f - p.dirY * 3.4f, by - p.dirY * 4f + p.dirX * 3.4f, stroke)
                        c.drawLine(bx, by, bx - p.dirX * 4f + p.dirY * 3.4f, by - p.dirY * 4f - p.dirX * 3.4f, stroke)
                    }
                    c.restore()
                }
                ProjKind.SPARK -> {
                    // 전기불꽃: 지그재그로 갈라지는 작은 번개 덩어리
                    drawTrail(c, p, 0xFFFFE272.toInt(), 9f)
                    fill.shader = RadialGradient(
                        p.x, p.y, 15f,
                        intArrayOf(0xFFFFFFE0.toInt(), alpha(0xFFFFE272.toInt(), 200), 0x00000000),
                        null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 15f, fill)
                    fill.shader = null
                    stroke.color = 0xFFFFFFFF.toInt()
                    stroke.strokeWidth = 1.3f
                    var px = p.x
                    var py = p.y
                    for (i in 0 until 3) {
                        val nx = p.x + cos((p.trail.size + i * 2.1f)) * 7f
                        val ny = p.y + sin((p.trail.size + i * 2.7f)) * 7f
                        c.drawLine(px, py, nx, ny, stroke)
                        px = nx
                        py = ny
                    }
                }
                ProjKind.FROST_ORB -> {
                    drawTrail(c, p, 0xFF8FE6FF.toInt(), 15f)
                    fill.shader = RadialGradient(
                        p.x, p.y, 21f,
                        intArrayOf(0xFFF2FDFF.toInt(), 0xFF8FE6FF.toInt(), 0x338FE6FF, 0x00000000),
                        floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 21f, fill)
                    fill.shader = null
                }
                ProjKind.GRENADE -> {
                    // 통통 튀는 유탄: 포물선 높이를 그대로 화면에 올린다
                    val hop = sin(min(1f, p.lift * 1.6f) * 3.1416f) * 22f
                    val gx = p.x
                    val gy = p.y - hop
                    fill.color = 0x33000000
                    c.drawOval(gx - 7f, p.y + 2f, gx + 7f, p.y + 7f, fill)
                    fill.shader = RadialGradient(
                        gx - 2f, gy - 3f, 11f,
                        intArrayOf(0xFF8A939E.toInt(), 0xFF3B424B.toInt(), 0xFF1C2026.toInt()),
                        null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(gx, gy, 9f, fill)
                    fill.shader = null
                    // 점화된 신관 — 퓨즈가 줄수록 빨라진다
                    stroke.color = 0xFFC9A066.toInt()
                    stroke.strokeWidth = 1.6f
                    c.drawLine(gx + 3f, gy - 8f, gx + 7f, gy - 14f, stroke)
                    val spark = 0.5f + 0.5f * sin((w.elapsed * (18.0 - p.fuse * 6.0)).toFloat())
                    fill.shader = RadialGradient(
                        gx + 7f, gy - 15f, 8f,
                        intArrayOf(alpha(0xFFFFE9A8.toInt(), 240), alpha(0xFFFF7B3C.toInt(), 120), 0x00000000),
                        null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(gx + 7f, gy - 15f, 8f, fill)
                    fill.shader = null
                    fill.color = alpha(0xFFFFFFFF.toInt(), (200 * spark).toInt().coerceIn(0, 255))
                    c.drawCircle(gx + 7f, gy - 15f, 2.4f, fill)
                }
                ProjKind.ICE_SHARD -> {
                    // 얼음 화살이 부수러뜨린 파편
                    c.save()
                    c.rotate(Math.toDegrees(kotlin.math.atan2(p.dirY, p.dirX).toDouble()).toFloat(), p.x, p.y)
                    path.reset()
                    path.moveTo(9f, 0f)
                    path.lineTo(-4f, -3.2f)
                    path.lineTo(-1f, 0f)
                    path.lineTo(-4f, 3.2f)
                    path.close()
                    fill.color = 0xFFE8FCFF.toInt()
                    c.drawPath(path, fill)
                    stroke.color = 0xCC8FE6FF.toInt()
                    stroke.strokeWidth = 1f
                    c.drawPath(path, stroke)
                    c.restore()
                }
                ProjKind.ENEMY_BOLT -> {
                    fill.shader = RadialGradient(
                        p.x, p.y, 15f,
                        intArrayOf(alpha(p.element.color, 240), alpha(p.element.color, 90), 0x00000000),
                        null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 15f, fill)
                    fill.shader = null
                    fill.color = 0xFFFFFFFF.toInt()
                    c.drawCircle(p.x, p.y, 3.2f, fill)
                }
            }
        }
    }

    private fun drawTrail(c: Canvas, p: Projectile, color: Int, radius: Float) {
        val trail = p.trail
        if (trail.size >= 4) {
            for (i in 2 until trail.size step 2) {
                val f = i.toFloat() / trail.size
                fill.color = alpha(color, ((f * 90).toInt()).coerceIn(0, 255))
                c.drawCircle(trail[i - 2], trail[i - 1], radius * f * 0.55f, fill)
            }
        }
        fill.shader = RadialGradient(
            p.x, p.y, radius * 2.4f,
            intArrayOf(alpha(color, 120), 0x00000000), null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(p.x, p.y, radius * 2.4f, fill)
        fill.shader = null
    }

    private fun drawParticles(c: Canvas, w: BattleWorld) {
        for (p in w.particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            fill.color = alpha(p.color, (a * 235).toInt().coerceIn(0, 255))
            if (p.streak) {
                stroke.color = fill.color
                stroke.strokeWidth = p.size * 0.62f
                c.drawLine(p.x - p.vx * 0.022f, p.y - p.vy * 0.022f, p.x, p.y, stroke)
            } else {
                c.drawCircle(p.x, p.y, p.size * (0.4f + a * 0.8f) * 0.8f, fill)
            }
        }
    }

    private fun drawPopups(c: Canvas, w: BattleWorld) {
        for (p in w.popups) {
            val a = (p.life / if (p.crit) 1.0f else 0.7f).coerceIn(0f, 1f)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 20f * p.size
            val strokeWidth = if (p.crit) 5.4f else 3.6f
            text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            text.style = Paint.Style.STROKE
            text.color = 0xB0000000.toInt()
            text.strokeWidth = strokeWidth
            c.drawText(p.text, p.x, p.y, text)
            text.style = Paint.Style.FILL
            text.color = alpha(p.color, (a * 255).toInt().coerceIn(0, 255))
            c.drawText(p.text, p.x, p.y, text)
        }
        text.style = Paint.Style.FILL
    }

    // -------------------------------------------------------------- 화면 고정 UI

    private fun drawVignette(c: Canvas, w: BattleWorld) {
        fill.shader = RadialGradient(
            width / 2f, height / 2f, max(width, height) * 0.78f,
            intArrayOf(0x00000000, 0x00000000, 0x99000000.toInt()),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null
        if (w.hurtFlash > 0.01f) {
            fill.color = alpha(0xFFFF2B2B.toInt(), (w.hurtFlash * 70).toInt().coerceIn(0, 255))
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        }
    }

    private fun drawHud(c: Canvas, w: BattleWorld) {
        val pad = 10f * dp
        val st = w.stats
        val element = st.fx.element

        // 스킬 배지
        val badgeText = st.skillName.ifBlank { st.fx.label }
        text.textAlign = Paint.Align.LEFT
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textSize = 13f * dp
        val nameW = text.measureText(badgeText)
        val chipText = element.label
        textLight.textAlign = Paint.Align.LEFT
        textLight.textSize = 10f * dp
        val chipW = textLight.measureText(chipText) + 12f * dp
        // 전투 중에는 메타젬별 발동 횟수, 시작 전에는 어떤 메타젬이 붙어 있는지 보여 준다.
        val liveTrigger = w.triggerHud()
        val triggerText = if (liveTrigger.isNotBlank()) liveTrigger else st.triggerText
        val hasTrigger = triggerText.isNotBlank()
        val badgeW = min(width - pad * 2f, nameW + chipW + 30f * dp)
        textLight.textSize = 9.5f * dp
        // 메타젬이 여럿이면 배지를 두 줄까지 늘려 이름과 발동 횟수를 최대한 보여 준다.
        val triggerLines = if (!hasTrigger) emptyList()
        else wrapText("⟳ $triggerText", max(80f * dp, badgeW - 20f * dp), 2)
        // 전직 효과는 메타젬 줄 아래에 금색으로 적는다.
        val ascend = w.ascendHud()
        val ascendLines = if (ascend.isBlank()) emptyList()
        else wrapText(ascend, max(80f * dp, badgeW - 20f * dp), 2)
        // 봇이 돌리는 콤보도 배지 안에 한 줄 붙인다.
        val combo = w.comboHud()
        val comboLines = if (combo.isBlank()) emptyList()
        else wrapText(combo, max(80f * dp, badgeW - 20f * dp), 1)
        // 소환수·두꺼비 상태도 한 줄
        val minion = w.minionHud()
        val minionLines = if (minion.isBlank()) emptyList()
        else wrapText("◈ $minion", max(80f * dp, badgeW - 20f * dp), 1)
        val badgeH = (27f + (triggerLines.size + ascendLines.size + comboLines.size + minionLines.size) * 11f) * dp

        rect.set(pad, pad, pad + badgeW, pad + badgeH)
        fill.color = 0xCC0E1117.toInt()
        c.drawRoundRect(rect, 8f * dp, 8f * dp, fill)
        stroke.color = alpha(element.color, 110)
        stroke.strokeWidth = 1.2f * dp
        c.drawRoundRect(rect, 8f * dp, 8f * dp, stroke)

        fill.color = element.color
        c.drawCircle(pad + 11f * dp, pad + 13.5f * dp, 3.4f * dp, fill)

        text.color = 0xFFF3EFE4.toInt()
        c.drawText(badgeText, pad + 20f * dp, pad + 18f * dp, text)

        val chipX = min(width - pad - chipW, pad + 20f * dp + nameW + 6f * dp)
        rect.set(chipX, pad + 6f * dp, chipX + chipW, pad + 21f * dp)
        fill.color = alpha(element.color, 40)
        c.drawRoundRect(rect, 4f * dp, 4f * dp, fill)
        textLight.color = alpha(element.color, 255)
        c.drawText(chipText, chipX + 6f * dp, pad + 16.6f * dp, textLight)

        if (triggerLines.isNotEmpty()) {
            textLight.color = 0xFFCFF3FF.toInt()
            textLight.textSize = 9.5f * dp
            triggerLines.forEachIndexed { index, line ->
                c.drawText(line, pad + 10f * dp, pad + (33f + index * 11f) * dp, textLight)
            }
            textLight.textSize = 10f * dp
        }
        if (ascendLines.isNotEmpty()) {
            textLight.color = 0xFFFFD98A.toInt()
            textLight.textSize = 9.5f * dp
            ascendLines.forEachIndexed { index, line ->
                val y = pad + (33f + (triggerLines.size + index) * 11f) * dp
                c.drawText(line, pad + 10f * dp, y, textLight)
            }
            textLight.textSize = 10f * dp
        }
        if (comboLines.isNotEmpty()) {
            textLight.color = 0xFFBFE4FF.toInt()
            textLight.textSize = 9.5f * dp
            comboLines.forEachIndexed { index, line ->
                val y = pad + (33f + (triggerLines.size + ascendLines.size + index) * 11f) * dp
                c.drawText(line, pad + 10f * dp, y, textLight)
            }
            textLight.textSize = 10f * dp
        }
        if (minionLines.isNotEmpty()) {
            textLight.color = 0xFFB9F0C8.toInt()
            textLight.textSize = 9.5f * dp
            minionLines.forEachIndexed { index, line ->
                val y = pad + (33f + (triggerLines.size + ascendLines.size + comboLines.size + index) * 11f) * dp
                c.drawText(line, pad + 10f * dp, y, textLight)
            }
            textLight.textSize = 10f * dp
        }

        // 남은 적 · 시간
        text.textAlign = Paint.Align.RIGHT
        text.textSize = 11f * dp
        text.color = 0xFFA9AFBA.toInt()
        val remain = if (w.mode == BattleMode.BOSS) "보스" else "적 ${w.aliveCount}/${w.totalEnemies}"
        c.drawText(remain, width - pad, pad + 12f * dp, text)
        text.color = 0xFFD4AF37.toInt()
        text.textSize = 12f * dp
        c.drawText(String.format(java.util.Locale.KOREA, "%.1fs", w.elapsed), width - pad, pad + 28f * dp, text)

        // 메타 에너지 · 연발 (혜성 x18 처럼)
        val meta = w.metaHud()
        if (meta.isNotBlank()) {
            text.textAlign = Paint.Align.RIGHT
            text.textSize = 11.5f * dp
            text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            text.color = if (w.maxBurst > 3) 0xFFFFD98A.toInt() else 0xFF9FD8FF.toInt()
            c.drawText("◈ $meta", width - pad, pad + 44f * dp, text)
            text.typeface = Typeface.DEFAULT
        }

        // 체력 / 보호막
        val barW = min(width * 0.46f, 220f * dp)
        val barX = pad
        val barY = pad + badgeH + 6f * dp
        drawPoolBar(c, barX, barY, barW, w.player.baseLife, w.player.life, 0xFFD9534F.toInt())
        drawPoolBar(c, barX, barY + 8f * dp, barW, w.player.baseEs, w.player.es, 0xFF4FA8D9.toInt())

        // 보스 체력 · 페이즈 · 패턴
        val boss = w.bossEnemy
        if (boss != null) {
            val barY2 = pad + badgeH + 28f * dp
            val w2 = width - pad * 2f
            text.textAlign = Paint.Align.LEFT
            text.textSize = 10.5f * dp
            text.color = 0xFFFFD9C9.toInt()
            val phaseLabel = if (w.bossPhase >= 2) "P2" else "P1"
            val phaseColorText = if (w.bossPhase >= 2) 0xFFFF8A5C.toInt() else 0xFFBFE9FF.toInt()
            c.drawText("${boss.boss?.name.orEmpty()}  ·  ${boss.boss?.pattern.orEmpty()}", pad, barY2 - 3f * dp, text)
            text.textAlign = Paint.Align.RIGHT
            text.color = phaseColorText
            text.textSize = 11.5f * dp
            val pattern = if (w.bossPatternName.isBlank()) "대기" else w.bossPatternName
            c.drawText("$phaseLabel  ·  $pattern", width - pad, barY2 - 3f * dp, text)
            text.textAlign = Paint.Align.LEFT
            text.textSize = 10.5f * dp
            text.color = 0xFFFFD9C9.toInt()
            rect.set(pad, barY2, pad + w2, barY2 + 8f * dp)
            fill.color = 0xCC241012.toInt()
            c.drawRoundRect(rect, 3f * dp, 3f * dp, fill)
            val frac = (boss.hp / boss.maxHp).toFloat().coerceIn(0f, 1f)
            rect.set(pad, barY2, pad + w2 * frac, barY2 + 8f * dp)
            fill.shader = LinearGradient(
                pad, barY2, pad + w2, barY2,
                intArrayOf(0xFFB3222A.toInt(), 0xFFFF7A3D.toInt()), null, Shader.TileMode.CLAMP,
            )
            c.drawRoundRect(rect, 3f * dp, 3f * dp, fill)
            fill.shader = null
            if (boss.shield > 0.0) {
                val sFrac = (boss.shield / boss.maxHp).toFloat().coerceIn(0f, 1f)
                rect.set(pad, barY2 + 9f * dp, pad + w2 * sFrac, barY2 + 13f * dp)
                fill.color = 0xFF7FE0FF.toInt()
                c.drawRoundRect(rect, 2f * dp, 2f * dp, fill)
            }
        }
    }

    private fun drawPoolBar(c: Canvas, x: Float, y: Float, w: Float, maxValue: Double, value: Double, color: Int) {
        if (maxValue <= 0.0) return
        rect.set(x, y, x + w, y + 5.4f * dp)
        fill.color = 0xB30B0D12.toInt()
        c.drawRoundRect(rect, 2.6f * dp, 2.6f * dp, fill)
        val frac = (value / maxValue).toFloat().coerceIn(0f, 1f)
        if (frac > 0f) {
            rect.set(x, y, x + w * frac, y + 5.4f * dp)
            fill.color = color
            c.drawRoundRect(rect, 2.6f * dp, 2.6f * dp, fill)
        }
        stroke.color = 0x33000000
        stroke.strokeWidth = 0.8f * dp
        c.drawRoundRect(RectF(x, y, x + w, y + 5.4f * dp), 2.6f * dp, 2.6f * dp, stroke)
    }

    private fun drawBanner(c: Canvas, w: BattleWorld) {
        if (w.banner <= 0f) return
        val a = (w.banner / 1.4f).coerceIn(0f, 1f)
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textSize = 19f * dp
        text.style = Paint.Style.STROKE
        text.strokeWidth = 5f * dp
        text.color = 0xB3000000.toInt()
        c.drawText(w.bannerText, width / 2f, height * 0.30f, text)
        text.style = Paint.Style.FILL
        text.color = alpha(0xFFFFE6A8.toInt(), (a * 255).toInt().coerceIn(0, 255))
        c.drawText(w.bannerText, width / 2f, height * 0.30f, text)
    }

    private fun drawPaused(c: Canvas) {
        fill.color = 0xA6000000.toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 15f * dp
        text.color = 0xFFE8E2D4.toInt()
        c.drawText("일시정지", width / 2f, height / 2f, text)
    }

    /** 폭에 맞춰 말줄임까지 붙여 maxLines 줄로 나눈다. */
    private fun wrapText(value: String, limit: Float, maxLines: Int): List<String> {
        val words = value.split(' ')
        val lines = ArrayList<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (textLight.measureText(candidate) <= limit) {
                current = StringBuilder(candidate)
                continue
            }
            if (current.isNotEmpty()) lines.add(current.toString())
            if (lines.size == maxLines) break
            current = StringBuilder(word)
        }
        if (lines.size < maxLines && current.isNotEmpty()) {
            var last = current.toString()
            while (last.isNotEmpty() && textLight.measureText(last) > limit) last = last.dropLast(1)
            lines.add(last)
        }
        // 남은 조각이 있으면 마지막 줄에 말줄임을 붙인다.
        if (lines.size == maxLines) {
            val last = lines.last()
            if (textLight.measureText(last) + textLight.measureText(" …") <= limit) {
                lines[lines.size - 1] = "$last …"
            }
        }
        return lines
    }

    // -------------------------------------------------------------- 색 유틸

    private fun alpha(color: Int, a: Int): Int =
        Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun withAlpha(color: Int, a: Int): Int = alpha(color, a)

    private fun blend(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * f).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * f).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f).toInt(),
        )
    }
}

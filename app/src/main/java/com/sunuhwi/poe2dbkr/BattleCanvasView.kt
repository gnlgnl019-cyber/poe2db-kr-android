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
import android.view.MotionEvent
import android.view.View
import java.util.Locale
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

    /** 사망 화면의 단추 — 전투 화면이 다시 시작과 닫기를 연결한다 */
    var onRetry: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    /** 사용자가 사망 화면을 닫았는지 */
    private var deathDismissed = false
    private val retryRect = RectF()
    private val closeRect = RectF()

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
                    val result = w.result()
                    if (result.victory) {
                        onFinished?.invoke(result)
                    } else {
                        // 사망 원인 카드가 올라온 뒤에 아래 결과 글을 띄운다.
                        postDelayed({ if (world === w) onFinished?.invoke(result) }, 1500L)
                    }
                }
            }
            invalidate()
            // 사망 화면은 원인이 다 올라올 때까지 조금 더 그린다.
            val tailMs = if (w?.state == BattleWorld.State.DEFEAT) 6200L else 2600L
            val done = finished && System.currentTimeMillis() - finishedAt > tailMs
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
        deathDismissed = false
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
        drawOils(canvas, w)
        drawCursedGrounds(canvas, w)
        drawWhirlwinds(canvas, w)
        drawFrostBombs(canvas, w)
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
        drawDeathScreen(canvas, w)
        if (paused) drawPaused(canvas)
        // 사망 화면이 다 올라오기 전에 프레임이 끊겼으면(탭 전환·화면 꺼짐) 여기서 다시 잇는다.
        if (w.state == BattleWorld.State.DEFEAT && !deathDismissed &&
            w.deathAge < DeathFx.TOTAL_TIME && !paused
        ) {
            running = true
            if (!frameScheduled) {
                lastFrameNanos = 0L
                schedule()
            }
        }
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
        if (w.state == BattleWorld.State.DEFEAT) {
            drawFallenPlayer(c, w)
            return
        }
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
        if (st.beast.isBeast) {
            drawBeastPlayer(c, w)
        } else if (p.whirlTimer > 0f) {
            // 선회 공격 — 몸을 크게 돌리며 주변을 벤다. 회전 궤적을 같이 남긴다.
            val spin = p.whirlAngle
            drawStickman(
                c = c, x = p.x, y = p.y, s = 1.62f,
                body = bodyColor, accent = element.color,
                facing = spin, walk = p.walkPhase, moving = p.moving,
                attack = 1f, weapon = st.weapon, form = st.form,
                glow = 0.55f + breath * 0.25f,
                motion = SkillMotion.WHIRLING_ASSAULT, time = w.elapsed.toFloat(), spin = spin * 0.35f,
            )
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
                motion = st.motion, time = w.elapsed.toFloat(),
            )
            // 집중 유지 중인 스킬(기름 포화 등)은 손 앞에 계속 기운이 모인다.
            if (p.channel > 0f) {
                val pulse = 0.5f + 0.5f * sin(w.elapsed * 14f).toFloat()
                val hx = p.x + cos(p.facing) * 22f
                val hy = p.y - 32f
                glowAt(c, hx, hy, 26f + pulse * 12f, element.color, (90 + pulse * 90).toInt().coerceIn(0, 255))
                stroke.color = alpha(element.color, 150)
                stroke.strokeWidth = 1.3f
                c.drawCircle(hx, hy, 14f + pulse * 8f, stroke)
            }
        }
    }

    /**
     * 쓰러진 모습 — 몸이 발을 축으로 옆으로 넘어가고, 빠져나온 영혼이 위로 흩어진다.
     * 바닥에는 붉은 자국이 번진다.
     */
    private fun drawFallenPlayer(c: Canvas, w: BattleWorld) {
        val p = w.player
        val st = w.stats
        val age = w.deathAge
        val fall = DeathFx.fall(age)
        val soul = DeathFx.soul(age)

        // 바닥에 번지는 자국 (몸보다 먼저 그린다)
        fill.color = alpha(0xFF4A0A0C.toInt(), (fall * 165).toInt().coerceIn(0, 255))
        c.drawOval(p.x - (30f + 16f * fall), p.y - 9f, p.x + (30f + 16f * fall), p.y + 9f, fill)

        val body = blend(0xFFF4F0E4.toInt(), 0xFF8A2A24.toInt(), 0.35f + fall * 0.5f)
        val accent = blend(st.fx.element.color, 0xFF6A1010.toInt(), 0.65f)
        c.save()
        c.rotate(-76f * fall, p.x, p.y)
        drawStickman(
            c = c, x = p.x, y = p.y, s = 1.62f,
            body = body, accent = accent,
            facing = -1.35f, walk = 0f, moving = false, attack = 0f,
            weapon = st.weapon, form = st.form, glow = 0.04f,
        )
        c.restore()

        // 빠져나온 영혼 — 몸 위에서 떠올라 흐려진다
        if (soul < 1f) {
            val sy = p.y - 30f - 74f * soul
            val a = ((1f - soul) * 190f).toInt().coerceIn(0, 255)
            glowAt(c, p.x + 8f * soul, sy, 26f + 10f * soul, 0xFFBFE0FF.toInt(), a / 2)
            fill.color = alpha(0xFFDCEBFF.toInt(), a)
            c.drawCircle(p.x + 8f * soul, sy, (3.4f + 3.6f * soul) * dp, fill)
            stroke.color = alpha(0xFF9CC8FF.toInt(), (a * 0.7f).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.2f * dp
            c.drawCircle(p.x + 8f * soul, sy, (7f + 8f * soul) * dp, stroke)
        }
    }

    /**
     * 부적으로 변신한 야수 — 곰·늑대인간·와이번.
     *
     * 머리·턱·갈기·날개·꼬리·앞뒷다리를 [BeastMotions]의 자세대로 나눠 그린다.
     * 변신하는 순간에는 형상 색 오라가 터지고, 그 뒤로는 형상마다 다른 실루엣이 선다.
     */
    private fun drawBeastPlayer(c: Canvas, w: BattleWorld) {
        val p = w.player
        val st = w.stats
        val form = st.beast
        val accent = form.element.color
        val breath = 0.5f + 0.5f * sin(w.elapsed * 3.1f).toFloat()
        val pose = BeastMotions.pose(form, p.attackAnim, p.walkPhase, p.moving, w.elapsed.toFloat(), p.channel)
        val dir = if (cos(p.facing) >= 0f) 1f else -1f
        val s = when (form) {
            BeastForm.BEAR -> 1.85f
            BeastForm.WEREWOLF -> 1.72f
            BeastForm.WYVERN -> 1.8f
            BeastForm.NONE -> 1f
        }
        // 변신 중에는 몸이 작게 시작해 부풀어 오른다.
        val grow = 0.55f + 0.45f * p.shiftAnim.coerceIn(0f, 1f)

        // 형상 오라
        fill.shader = RadialGradient(
            p.x, p.y + 2f, 84f * grow,
            intArrayOf(alpha(accent, (60 + breath * 40).toInt()), 0x00000000),
            null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(p.x, p.y + 2f, 84f * grow, fill)
        fill.shader = null
        stroke.color = alpha(accent, 110)
        stroke.strokeWidth = 1.8f
        val ringR = 40f + breath * 10f
        c.drawOval(p.x - ringR, p.y - ringR * 0.4f, p.x + ringR, p.y + ringR * 0.4f, stroke)
        drawShadow(c, p.x, p.y, 2.1f)

        c.save()
        c.translate(p.x, p.y)
        c.scale(grow, grow)
        c.translate(-p.x, -p.y)
        when (form) {
            BeastForm.BEAR -> drawBear(c, p.x, p.y, s, dir, pose, accent, w.elapsed.toFloat())
            BeastForm.WEREWOLF -> drawWerewolf(c, p.x, p.y, s, dir, pose, accent, w.elapsed.toFloat())
            BeastForm.WYVERN -> drawWyvern(c, p.x, p.y, s, dir, pose, accent, w.elapsed.toFloat())
            BeastForm.NONE -> Unit
        }
        c.restore()

        // 변신 직후 터지는 오라 고리
        if (p.shiftAnim < 1f) {
            val k = (1f - p.shiftAnim).coerceIn(0f, 1f)
            stroke.color = alpha(accent, (200 * k).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 2.6f
            c.drawCircle(p.x, p.y - 34f, 30f + 70f * (1f - k), stroke)
            stroke.color = alpha(0xFFFFFFFF.toInt(), (120 * k).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.4f
            c.drawCircle(p.x, p.y - 34f, 46f + 90f * (1f - k), stroke)
        }
    }

    /** 곰 — 네 발로 선 몸통, 큰 머리와 턱, 갈기, 앞뒷다리 발톱 */
    private fun drawBear(
        c: Canvas, x: Float, y: Float, s: Float, dir: Float, pose: BeastPose,
        accent: Int, time: Float,
    ) {
        val fur = 0xFF8A6A46.toInt()
        val furDark = 0xFF6B5236.toInt()
        val lift = pose.lift * s
        val hipX = x - dir * 16f * s
        val hipY = y - 34f * s - lift
        val chestX = x + dir * 20f * s
        val chestY = y - 40f * s - lift - sin(pose.pitch) * 8f * s
        val neckX = chestX + dir * 8f * s
        val neckY = chestY - 6f * s - cos(pose.pitch) * 4f * s
        val headX = neckX + dir * 10f * s
        val headY = neckY - 10f * s + pose.head * 12f * s
        val headR = 8.4f * s

        // 몸통
        stroke.color = fur
        stroke.strokeWidth = 15f * s
        c.drawLine(hipX, hipY, chestX, chestY, stroke)
        stroke.color = furDark
        stroke.strokeWidth = 6f * s
        c.drawLine(hipX + dir * 2f * s, hipY + 3f * s, chestX - dir * 2f * s, chestY + 3f * s, stroke)

        // 목과 갈기
        stroke.color = fur
        stroke.strokeWidth = 12f * s
        c.drawLine(chestX, chestY, neckX, neckY, stroke)
        stroke.color = 0xFF5A4326.toInt()
        stroke.strokeWidth = 2.4f * s
        for (i in 0 until 6) {
            val a = -1.1f + i * 0.42f + pose.head * 0.4f
            val len = (5f + pose.mane * 9f) * s
            c.drawLine(
                neckX + cos(a) * 4f * s * dir, neckY + sin(a) * 4f * s,
                neckX + cos(a) * len * dir, neckY + sin(a) * len, stroke,
            )
        }

        // 머리
        fill.color = fur
        c.drawCircle(headX, headY, headR, fill)
        stroke.color = furDark
        stroke.strokeWidth = 2.2f * s
        // 귀
        c.drawLine(headX - dir * 4f * s, headY - headR * 0.7f, headX - dir * 6.5f * s, headY - headR * 1.7f, stroke)
        c.drawLine(headX + dir * 4f * s, headY - headR * 0.7f, headX + dir * 6.5f * s, headY - headR * 1.7f, stroke)
        // 주둥이와 턱
        val snoutX = headX + dir * headR * 0.85f
        val snoutY = headY + headR * 0.25f
        stroke.color = fur
        stroke.strokeWidth = 5.4f * s
        c.drawLine(headX, headY + headR * 0.15f, snoutX + dir * 4f * s, snoutY, stroke)
        val jawOpen = pose.jaw * 0.75f
        stroke.color = 0xFF4A3520.toInt()
        stroke.strokeWidth = 2.6f * s
        c.drawLine(
            snoutX, snoutY,
            snoutX + dir * cos(jawOpen) * 7f * s, snoutY + sin(jawOpen) * 7f * s + 2f * s, stroke,
        )
        // 이빨
        if (pose.jaw > 0.35f) {
            stroke.color = 0xFFF2E8D0.toInt()
            stroke.strokeWidth = 1.3f * s
            c.drawLine(snoutX + dir * 4f * s, snoutY + 1f * s, snoutX + dir * 4f * s, snoutY + 4f * s + jawOpen * 3f * s, stroke)
        }
        // 눈
        fill.color = if (pose.jaw > 0.5f) accent else 0xFF201A12.toInt()
        c.drawCircle(headX + dir * 3f * s, headY - 2f * s, 1.4f * s, fill)

        // 다리 — 허벅지·종아리·발
        fun leg(rootX: Float, rootY: Float, reach: Float, back: Boolean) {
            val phase = if (back) -pose.stride else pose.stride
            val upper = if (back) 10f * s else (10f + reach * 6f) * s
            val kneeX = rootX + dir * (sin(phase) * 5f - reach * 7f) * s
            val kneeY = rootY + upper
            val pawX = kneeX + dir * (cos(phase) * 4f + reach * 5f) * s
            val pawY = y - 1.5f * s
            stroke.color = fur
            stroke.strokeWidth = 6.5f * s
            c.drawLine(rootX, rootY, kneeX, kneeY, stroke)
            stroke.strokeWidth = 5.2f * s
            c.drawLine(kneeX, kneeY, pawX, pawY, stroke)
            // 발톱
            stroke.color = 0xFFE8E0CC.toInt()
            stroke.strokeWidth = 1.4f * s
            for (i in -1..1) {
                c.drawLine(pawX, pawY, pawX + dir * 4.5f * s, pawY + i * 1.6f * s, stroke)
            }
        }
        leg(chestX - dir * 2f * s, chestY + 4f * s, pose.frontReach, back = false)
        leg(chestX + dir * 1f * s, chestY + 5f * s, pose.frontReach * 0.7f, back = false)
        leg(hipX, hipY + 3f * s, 0f, back = true)

        // 꼬리
        stroke.color = furDark
        stroke.strokeWidth = 4f * s
        path.reset()
        path.moveTo(hipX - dir * 3f * s, hipY - 2f * s)
        path.quadTo(
            hipX - dir * 12f * s, hipY - 4f * s + pose.tail * 4f * s,
            hipX - dir * 16f * s, hipY - 9f * s + pose.tail * 7f * s,
        )
        c.drawPath(path, stroke)
    }

    /** 늑대인간 — 굽은 허리, 긴 팔과 발톱, 늑대 머리, 갈기와 꼬리 */
    private fun drawWerewolf(
        c: Canvas, x: Float, y: Float, s: Float, dir: Float, pose: BeastPose,
        accent: Int, time: Float,
    ) {
        val fur = 0xFF6E6A78.toInt()
        val furDark = 0xFF4B4854.toInt()
        val lift = pose.lift * s
        val hipX = x - dir * 2f * s
        val hipY = y - 30f * s - lift
        val shX = hipX + dir * sin(pose.pitch + 0.45f) * 17f * s
        val shY = hipY - cos(pose.pitch + 0.45f) * 17f * s
        val headX = shX + dir * 7f * s
        val headY = shY - 8f * s + pose.head * 10f * s
        val headR = 6.6f * s

        // 다리 — 무릎이 뒤로 꺾인 짐승 다리
        fun leg(rootX: Float, rootY: Float, phase: Float) {
            val thighX = rootX + dir * sin(0.45f + phase * 0.5f) * 10f * s
            val thighY = rootY + cos(0.45f + phase * 0.5f) * 10f * s
            val shinX = thighX - dir * (0.35f + pose.crouch * 0.5f) * 10f * s
            val shinY = thighY + 10f * s
            val footX = thighX + dir * 4f * s
            val footY = y - 1f * s
            stroke.color = fur
            stroke.strokeWidth = 6f * s
            c.drawLine(rootX, rootY, thighX, thighY, stroke)
            c.drawLine(thighX, thighY, shinX, shinY, stroke)
            stroke.strokeWidth = 4.6f * s
            c.drawLine(shinX, shinY, footX, footY, stroke)
            stroke.color = 0xFFE8E0CC.toInt()
            stroke.strokeWidth = 1.3f * s
            for (i in -1..1) c.drawLine(footX, footY, footX + dir * 4f * s, footY + i * 1.4f * s, stroke)
        }
        leg(hipX, hipY, pose.stride)
        leg(hipX + dir * 3f * s, hipY + 1f * s, -pose.stride)

        // 몸통
        stroke.color = fur
        stroke.strokeWidth = 12f * s
        c.drawLine(hipX, hipY, shX, shY, stroke)
        stroke.color = furDark
        stroke.strokeWidth = 5f * s
        c.drawLine(hipX, hipY + 2f * s, shX - dir * 2f * s, shY + 2f * s, stroke)

        // 팔 — 길게 뻗어 할퀸다
        val reach = pose.frontReach
        val armAng = -0.6f + reach * 1.9f
        val elbX = shX + dir * sin(armAng) * 9f * s
        val elbY = shY + cos(armAng) * 9f * s
        val handX = elbX + dir * sin(armAng + 0.55f) * 10f * s
        val handY = elbY + cos(armAng + 0.55f) * 10f * s
        stroke.color = fur
        stroke.strokeWidth = 5.6f * s
        c.drawLine(shX, shY, elbX, elbY, stroke)
        c.drawLine(elbX, elbY, handX, handY, stroke)
        // 반대팔
        val bAng = 0.5f - reach * 0.7f
        val bElbX = shX + dir * sin(bAng) * 8f * s
        val bElbY = shY + cos(bAng) * 8f * s
        stroke.strokeWidth = 4.6f * s
        c.drawLine(shX, shY, bElbX, bElbY, stroke)
        c.drawLine(bElbX, bElbY, bElbX + dir * 3f * s, bElbY + 6f * s, stroke)
        // 발톱
        stroke.color = 0xFFE8E0CC.toInt()
        stroke.strokeWidth = 1.5f * s
        for (i in -1..1) {
            c.drawLine(handX, handY, handX + cos(armAng + i * 0.35f) * 10f * s, handY + sin(armAng + i * 0.35f) * 5f * s, stroke)
        }

        // 머리 — 늑대 주둥이, 귀, 갈기
        fill.color = fur
        c.drawCircle(headX, headY, headR, fill)
        stroke.color = fur
        stroke.strokeWidth = 4.4f * s
        c.drawLine(headX + dir * 3f * s, headY, headX + dir * 9f * s, headY + 2f * s, stroke)
        stroke.color = furDark
        stroke.strokeWidth = 1.8f * s
        // 귀
        c.drawLine(headX - dir * 3f * s, headY - headR * 0.8f, headX - dir * 5f * s, headY - headR * 1.8f, stroke)
        c.drawLine(headX + dir * 2f * s, headY - headR * 0.9f, headX + dir * 3.6f * s, headY - headR * 1.9f, stroke)
        // 턱
        val jawOpen = pose.jaw * 0.8f
        stroke.color = 0xFF332F38.toInt()
        stroke.strokeWidth = 2.4f * s
        c.drawLine(
            headX + dir * 8f * s, headY + 2f * s,
            headX + dir * (10f + cos(jawOpen) * 3f) * s, headY + 3f * s + sin(jawOpen) * 5f * s, stroke,
        )
        // 갈기
        stroke.color = 0xFF3E3A46.toInt()
        stroke.strokeWidth = 2.2f * s
        for (i in 0 until 5) {
            val a = -1.0f + i * 0.5f
            val len = (4f + pose.mane * 8f) * s
            c.drawLine(headX, headY + 2f * s, headX - dir * cos(a) * len, headY + sin(a) * len + 3f * s, stroke)
        }
        // 눈 — 변신과 할퀴기에 빛난다
        fill.color = blend(accent, 0xFFFFFFFF.toInt(), 0.25f + reach * 0.3f)
        c.drawCircle(headX + dir * 4f * s, headY - 1f * s, 1.5f * s, fill)

        // 꼬리
        stroke.color = furDark
        stroke.strokeWidth = 3.4f * s
        path.reset()
        path.moveTo(hipX - dir * 3f * s, hipY)
        path.quadTo(
            hipX - dir * 11f * s, hipY - 2f * s + pose.tail * 6f * s,
            hipX - dir * 14f * s, hipY - 10f * s + pose.tail * 10f * s,
        )
        c.drawPath(path, stroke)
    }

    /** 와이번 — 날개를 편 몸통, 긴 목과 턱, 꼬리, 뒷다리 발톱 */
    private fun drawWyvern(
        c: Canvas, x: Float, y: Float, s: Float, dir: Float, pose: BeastPose,
        accent: Int, time: Float,
    ) {
        val hide = 0xFF5E7A6E.toInt()
        val hideDark = 0xFF3E554C.toInt()
        val lift = pose.lift * s
        val bodyX = x
        val bodyY = y - 44f * s - lift
        // 목은 앞으로 뻗는다 (attack/channel이면 더 길게)
        val neckLen = (14f + pose.frontReach * 12f) * s
        val neckX = bodyX + dir * neckLen * 0.7f
        val neckY = bodyY - neckLen * 0.5f - sin(pose.head) * 6f * s
        val headX = neckX + dir * 10f * s
        val headY = neckY + 2f * s + pose.head * 8f * s
        val headR = 6.2f * s

        // 꼬리 — 길게 휘어진다
        stroke.color = hideDark
        stroke.strokeWidth = 5f * s
        path.reset()
        path.moveTo(bodyX - dir * 6f * s, bodyY + 3f * s)
        path.quadTo(
            bodyX - dir * 22f * s, bodyY + 6f * s + pose.tail * 8f * s,
            bodyX - dir * 34f * s, bodyY - 6f * s + pose.tail * 12f * s,
        )
        c.drawPath(path, stroke)
        // 꼬리 끝 가시
        path.reset()
        path.moveTo(bodyX - dir * 34f * s, bodyY - 6f * s + pose.tail * 12f * s)
        path.lineTo(bodyX - dir * 40f * s, bodyY - 12f * s + pose.tail * 14f * s)
        path.lineTo(bodyX - dir * 33f * s, bodyY - 13f * s + pose.tail * 13f * s)
        path.close()
        fill.color = 0xFF9AE8D0.toInt()
        c.drawPath(path, fill)

        // 몸통
        stroke.color = hide
        stroke.strokeWidth = 14f * s
        c.drawLine(bodyX - dir * 8f * s, bodyY + 6f * s, bodyX + dir * 8f * s, bodyY, stroke)
        stroke.color = hideDark
        stroke.strokeWidth = 5f * s
        c.drawLine(bodyX - dir * 6f * s, bodyY + 9f * s, bodyX + dir * 6f * s, bodyY + 4f * s, stroke)

        // 목
        stroke.color = hide
        stroke.strokeWidth = 8f * s
        path.reset()
        path.moveTo(bodyX + dir * 7f * s, bodyY - 2f * s)
        path.quadTo(bodyX + dir * neckLen * 0.4f, bodyY - neckLen * 0.5f, neckX, neckY)
        c.drawPath(path, stroke)

        // 머리 — 뾰족한 주둥이와 턱, 뿔
        fill.color = hide
        c.drawCircle(headX, headY, headR, fill)
        stroke.color = hide
        stroke.strokeWidth = 4.6f * s
        c.drawLine(headX + dir * 3f * s, headY, headX + dir * 11f * s, headY + 2f * s, stroke)
        stroke.color = hideDark
        stroke.strokeWidth = 2.2f * s
        val jawOpen = pose.jaw * 0.9f
        c.drawLine(
            headX + dir * 5f * s, headY + 3f * s,
            headX + dir * (12f + jawOpen * 4f) * s, headY + 4f * s + jawOpen * 7f * s, stroke,
        )
        // 뿔
        c.drawLine(headX - dir * 2f * s, headY - headR * 0.8f, headX - dir * 6f * s, headY - headR * 2.3f, stroke)
        c.drawLine(headX + dir * 1f * s, headY - headR * 0.9f, headX - dir * 1f * s, headY - headR * 2.4f, stroke)
        fill.color = blend(accent, 0xFFFFFFFF.toInt(), 0.3f)
        c.drawCircle(headX + dir * 4f * s, headY - 1.5f * s, 1.6f * s, fill)
        // 숨결이 새는 입
        if (pose.jaw > 0.3f) {
            glowAt(
                c, headX + dir * 14f * s, headY + 4f * s, 16f * s, accent,
                (120 + 80 * sin(time * 18f).toFloat()).toInt().coerceIn(0, 255),
            )
        }

        // 날개 — 어깨에서 두 마디로 펼쳐지고 얇은 막이 붙는다
        val spread = pose.wing
        val flap = pose.flap
        fun wing(side: Float) {
            val shX = bodyX + dir * 2f * s
            val shY = bodyY - 5f * s
            val elbowX = shX - dir * (6f + spread * 16f) * s
            val elbowY = shY - (12f + spread * 10f - flap * 6f) * s
            val tipX = elbowX - dir * (8f + spread * 14f) * s
            val tipY = elbowY - (6f + spread * 8f - flap * 8f) * s
            stroke.color = hideDark
            stroke.strokeWidth = 3.4f * s
            c.drawLine(shX, shY, elbowX, elbowY, stroke)
            c.drawLine(elbowX, elbowY, tipX, tipY, stroke)
            // 날개막
            path.reset()
            path.moveTo(shX, shY)
            path.lineTo(elbowX, elbowY)
            path.lineTo(tipX, tipY)
            path.quadTo(
                elbowX - dir * 4f * s, elbowY + 6f * s + flap * 4f * s,
                shX + dir * 2f * s, shY + 8f * s,
            )
            path.close()
            fill.color = alpha(blend(hide, accent, 0.35f), 120)
            c.drawPath(path, fill)
            stroke.color = alpha(accent, 120)
            stroke.strokeWidth = 1.1f * s
            c.drawPath(path, stroke)
            // 날개 뼈 사이 막살
            stroke.color = alpha(hideDark, 170)
            stroke.strokeWidth = 1f * s
            c.drawLine(elbowX, elbowY, shX - dir * 2f * s, shY + 7f * s, stroke)
            c.drawLine(tipX, tipY, shX - dir * 1f * s, shY + 7f * s, stroke)
        }
        wing(-1f)
        wing(1f)

        // 뒷다리 — 굵은 허벅지와 발톱
        fun leg(rootX: Float) {
            val phase = pose.stride
            val kneeX = rootX + dir * (3f + phase * 4f) * s
            val kneeY = y - 16f * s
            val footX = kneeX - dir * (2f - phase * 3f) * s
            stroke.color = hide
            stroke.strokeWidth = 6.4f * s
            c.drawLine(rootX, bodyY + 12f * s, kneeX, kneeY, stroke)
            stroke.strokeWidth = 4.6f * s
            c.drawLine(kneeX, kneeY, footX, y - 1f * s, stroke)
            stroke.color = 0xFFE8E0CC.toInt()
            stroke.strokeWidth = 1.4f * s
            for (i in -1..1) c.drawLine(footX, y - 1f * s, footX + dir * 5f * s, y - 1f * s + i * 1.6f * s, stroke)
        }
        leg(bodyX - dir * 4f * s)
        leg(bodyX + dir * 2f * s)
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

        drawStickman(
            c = c, x = p.x, y = p.y, s = 1.55f,
            body = withAlpha(0xFFD9C4FF.toInt(), (170 * fade).toInt()),
            accent = withAlpha(purple, (200 * fade).toInt()),
            facing = p.facing, walk = p.age * 9f, moving = true,
            attack = 1f, weapon = p.weapon, form = p.form,
            glow = 0.45f * fade,
            motion = p.motion, time = w.elapsed.toFloat(), spin = p.spin * 0.4f,
        )

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
            // 바닥에서 솟는 기류와 안쪽 심
            glowAt(c, t.x, t.y - tall * 0.25f, t.radius * 0.9f, tint, (40 * fade).toInt().coerceIn(0, 255))
            fill.color = alpha(0xFFFFFFFF.toInt(), (70 * fade).toInt().coerceIn(0, 255))
            c.drawCircle(t.x, t.y - tall * 0.55f, 3.4f + sin(w.elapsed * 9f).toFloat() * 1.2f, fill)
            // 휘날리는 파편 — 회오리 벽을 따라 돌아 올라간다
            fill.color = alpha(t.element.color, (150 * fade).toInt().coerceIn(0, 255))
            for (i in 0 until 5) {
                val f = ((w.elapsed.toFloat() * 0.55f + i * 0.2f) % 1f)
                val r = t.radius * (0.95f - f * 0.6f)
                val a = w.elapsed.toFloat() * 8f + i * 1.26f
                c.drawCircle(t.x + cos(a) * r, t.y - tall * f, 1.6f + f * 1.4f, fill)
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
            // 저주 인장 — 늪 가운데에 오각 문양이 천천히 돈다
            val sigil = g.radius * 0.55f
            stroke.color = alpha(0xFFC182FF.toInt(), (110 + pulse * 80).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.3f
            for (i in 0 until 5) {
                val a0 = w.elapsed.toFloat() * 0.35f + i * 1.256f
                val a1 = a0 + 2.09f
                c.drawLine(
                    g.x + cos(a0) * sigil, g.y + sin(a0) * sigil * 0.48f,
                    g.x + cos(a1) * sigil, g.y + sin(a1) * sigil * 0.48f, stroke,
                )
            }
            // 위로 피어오르는 보랏빛 연기
            fill.color = alpha(0xFF9B5DE5.toInt(), (36 + pulse * 24).toInt())
            for (i in 0 until 3) {
                val a = w.elapsed.toFloat() * 0.9f + i * 2.1f
                c.drawCircle(g.x + cos(a) * g.radius * 0.4f, g.y - 12f - i * 9f, 6f + i * 2f, fill)
            }
        }
    }

    private fun drawShadow(c: Canvas, x: Float, y: Float, scale: Float) {
        fill.color = 0x44000000
        c.drawOval(x - 13f * scale, y - 3.6f * scale, x + 13f * scale, y + 3.6f * scale, fill)
    }

    /**
     * 스틱맨 — 머리·몸통·상완/전완·허벅지/종아리·무기를 관절로 나눠 그린다.
     *
     * 자세는 [SkillPoses]가 스킬별 키프레임으로 낸다. 소용돌이베기는 온몸이 돌고,
     * 혜성은 두 손을 하늘로 들었다가 내리꽂고, 활은 시위를 당겼다 놓는다.
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
        motion: SkillMotion = SkillMotion.DEFAULT,
        time: Float = 0f,
        spin: Float = 0f,
    ) {
        val pose = SkillPoses.pose(motion, form, attack, walk, moving, time)
        val j = SkillPoses.skeleton(x, y, s, pose, facing)
        val turn = SkillPoses.bodySpin(motion, attack, spin)
        val bodyColor = if (alphaMul < 1f) withAlpha(body, (Color.alpha(body) * alphaMul).toInt()) else body
        val accentColor = if (alphaMul < 1f) withAlpha(accent, (Color.alpha(accent) * alphaMul).toInt()) else accent
        val dir = if (cos(facing) >= 0f) 1f else -1f
        val limbW = (3.2f - pose.crouch * 0.02f).coerceAtLeast(2.6f) * s
        val jointR = 2.1f * s

        c.save()
        if (turn != 0f) c.rotate(Math.toDegrees(turn.toDouble()).toFloat(), x, y - 28f * s)

        if (glow > 0f) {
            glowAt(c, j.shoulderX, j.shoulderY - 2f * s, 24f * s, accentColor, (70 * glow + 30).toInt().coerceIn(0, 255))
        }

        // 다리 — 허벅지와 종아리를 따로 꺾고 발끝을 찍는다
        stroke.strokeWidth = limbW
        stroke.color = bodyColor
        c.drawLine(j.hipX, j.hipY, j.frontKneeX, j.frontKneeY, stroke)
        c.drawLine(j.frontKneeX, j.frontKneeY, j.frontFootX, j.frontFootY, stroke)
        c.drawLine(j.hipX, j.hipY, j.backKneeX, j.backKneeY, stroke)
        c.drawLine(j.backKneeX, j.backKneeY, j.backFootX, j.backFootY, stroke)
        fill.color = bodyColor
        c.drawCircle(j.frontKneeX, j.frontKneeY, jointR * 0.8f, fill)
        c.drawCircle(j.backKneeX, j.backKneeY, jointR * 0.8f, fill)
        // 발
        stroke.strokeWidth = 2.2f * s
        c.drawLine(j.frontFootX, j.frontFootY, j.frontFootX + dir * 4.2f * s, j.frontFootY + 1.2f * s, stroke)
        c.drawLine(j.backFootX, j.backFootY, j.backFootX + dir * 4.2f * s, j.backFootY + 1.2f * s, stroke)

        // 몸통 — 허리에서 어깨까지 기울고, 어깨 폭을 한 줄 더 그린다
        stroke.strokeWidth = limbW * 1.05f
        stroke.color = bodyColor
        c.drawLine(j.hipX, j.hipY, j.shoulderX, j.shoulderY, stroke)
        val nx = -(j.shoulderY - j.hipY)
        val ny = j.shoulderX - j.hipX
        val nl = hypot(nx, ny).coerceAtLeast(0.001f)
        val half = 3.0f * s
        c.drawLine(
            j.shoulderX - nx / nl * half, j.shoulderY - ny / nl * half,
            j.shoulderX + nx / nl * half, j.shoulderY + ny / nl * half, stroke,
        )
        fill.color = bodyColor
        c.drawCircle(j.hipX, j.hipY, jointR, fill)

        // 팔 — 어깨·팔꿈치·손을 잇는 두 마디
        stroke.strokeWidth = limbW * 0.92f
        stroke.color = bodyColor
        c.drawLine(j.shoulderX, j.shoulderY, j.backElbowX, j.backElbowY, stroke)
        c.drawLine(j.backElbowX, j.backElbowY, j.backHandX, j.backHandY, stroke)
        c.drawLine(j.shoulderX, j.shoulderY, j.frontElbowX, j.frontElbowY, stroke)
        c.drawLine(j.frontElbowX, j.frontElbowY, j.frontHandX, j.frontHandY, stroke)
        fill.color = bodyColor
        c.drawCircle(j.frontElbowX, j.frontElbowY, jointR * 0.85f, fill)
        c.drawCircle(j.backElbowX, j.backElbowY, jointR * 0.85f, fill)

        // 무기 — 앞손에 쥐고 자세 각도대로 뻗는다
        val wAng = facing + pose.weapon
        when (weapon) {
            WeaponVisualType.BOW -> drawBow(c, j.frontHandX, j.frontHandY, s * 0.92f, wAng, attack, bodyColor)
            WeaponVisualType.CROSSBOW -> drawCrossbow(c, j.frontHandX, j.frontHandY, s * 0.9f, wAng, attack, accentColor)
            WeaponVisualType.STAFF -> drawHeldRod(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor, 30f, true)
            WeaponVisualType.WAND -> drawHeldRod(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor, 17f, false)
            WeaponVisualType.SCEPTRE -> drawHeldRod(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor, 23f, false)
            WeaponVisualType.QUARTERSTAFF ->
                drawPolearm(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor, 46f, false)
            WeaponVisualType.SPEAR ->
                drawPolearm(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor, 40f, true)
            WeaponVisualType.SWORD -> drawBlade(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor, bodyColor, 32f, false)
            WeaponVisualType.DAGGER -> drawBlade(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor, bodyColor, 17f, false)
            WeaponVisualType.AXE -> drawAxe(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor)
            WeaponVisualType.MACE -> drawMace(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor)
            WeaponVisualType.CLAW -> drawClaw(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor)
            WeaponVisualType.FLAIL -> drawFlail(c, j.frontHandX, j.frontHandY, s, wAng, attack, accentColor)
            WeaponVisualType.SHIELD -> {
                stroke.strokeWidth = 2f * s
                stroke.color = bodyColor
                c.drawLine(j.backElbowX, j.backElbowY, j.backHandX, j.backHandY, stroke)
                drawShield(c, j.backHandX, j.backHandY, s, wAng, accentColor)
            }
            WeaponVisualType.NONE -> {
                fill.color = bodyColor
                c.drawCircle(j.frontHandX, j.frontHandY, 2.6f * s, fill)
                c.drawCircle(j.backHandX, j.backHandY, 2.6f * s, fill)
            }
        }
        // 두 손 무기 — 뒷손을 자루에 얹는다
        if (pose.twoHanded && weapon != WeaponVisualType.NONE) {
            stroke.color = bodyColor
            stroke.strokeWidth = limbW * 0.7f
            val gx = j.frontHandX - cos(wAng) * 7f * s
            val gy = j.frontHandY - sin(wAng) * 7f * s
            c.drawLine(j.backHandX, j.backHandY, gx, gy, stroke)
        }

        if (windup > 0f) {
            stroke.color = 0xFFFF4B3D.toInt()
            stroke.strokeWidth = 2f * s
            c.drawCircle(j.headX, j.headY, j.headR * 2.4f + sin(windup * 20f) * 2f, stroke)
        }

        // 머리 — 목, 얼굴, 눈, 머리카락
        stroke.strokeWidth = limbW * 0.8f
        stroke.color = bodyColor
        c.drawLine(j.shoulderX, j.shoulderY, j.neckAnchorX(), j.neckAnchorY(), stroke)
        fill.color = bodyColor
        c.drawCircle(j.headX, j.headY, j.headR, fill)
        val faceDir = dir
        fill.color = alpha(0xFF20242C.toInt(), 170)
        c.drawCircle(j.headX + faceDir * j.headR * 0.42f, j.headY - j.headR * 0.08f, j.headR * 0.17f, fill)
        // 머리카락·투구 흔적
        stroke.color = withAlpha(accentColor, 150)
        stroke.strokeWidth = 1.2f * s
        c.drawLine(
            j.headX - faceDir * j.headR * 0.55f, j.headY - j.headR * 0.75f,
            j.headX - faceDir * j.headR * 1.25f, j.headY - j.headR * 1.55f, stroke,
        )
        if (boss) {
            stroke.strokeWidth = 2.3f * s
            stroke.color = alpha(bossColor, 235)
            c.drawLine(j.headX - j.headR * 0.7f, j.headY - j.headR * 0.7f, j.headX - j.headR * 2.1f, j.headY - j.headR * 2.4f, stroke)
            c.drawLine(j.headX + j.headR * 0.7f, j.headY - j.headR * 0.7f, j.headX + j.headR * 2.1f, j.headY - j.headR * 2.4f, stroke)
        }

        c.restore()
    }

    /** 목 좌표 — 어깨에서 머리 쪽으로 조금 올라간다 */
    private fun SkeletonJoints.neckAnchorX(): Float = shoulderX + (headX - shoulderX) * 0.35f
    private fun SkeletonJoints.neckAnchorY(): Float = shoulderY + (headY - shoulderY) * 0.35f

    /** 각도대로 드는 지팡이·완드 — 손에서 무기가 뻗는 방향이 자세를 따라간다 */
    private fun drawHeldRod(
        c: Canvas,
        x: Float,
        y: Float,
        s: Float,
        angle: Float,
        attack: Float,
        accent: Int,
        length: Float,
        staff: Boolean,
    ) {
        val len = length * s
        stroke.color = 0xFF8A6A46.toInt()
        stroke.strokeWidth = (if (staff) 2.2f else 1.8f) * s
        c.drawLine(x - cos(angle) * len * 0.35f, y - sin(angle) * len * 0.35f,
            x + cos(angle) * len, y + sin(angle) * len, stroke)
        val tipX = x + cos(angle) * len
        val tipY = y + sin(angle) * len
        val glowR = if (staff) 12f * s + attack * 6f * s else 8f * s + attack * 4f * s
        fill.shader = RadialGradient(
            tipX, tipY, glowR,
            intArrayOf(alpha(accent, 235), alpha(accent, 80), 0x00000000),
            null, Shader.TileMode.CLAMP,
        )
        c.drawCircle(tipX, tipY, glowR, fill)
        fill.shader = null
        fill.color = alpha(0xFFFFFFFF.toInt(), 215)
        c.drawCircle(tipX, tipY, 2.2f * s, fill)
        if (staff) {
            stroke.color = 0xFFC9A76A.toInt()
            stroke.strokeWidth = 1.4f * s
            c.drawCircle(tipX, tipY, 4.2f * s, stroke)
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

    /** 기름 지대 — 젖은 기름은 검붉게 번지고, 불붙은 기름은 계속 탄다 */
    private fun drawOils(c: Canvas, w: BattleWorld) {
        for (o in w.oils) {
            val fade = o.fade
            if (fade <= 0.02f) continue
            val a = (70 * fade).toInt().coerceIn(0, 255)
            fill.shader = RadialGradient(
                o.x, o.y, o.radius,
                intArrayOf(alpha(0xFF3A2E12.toInt(), (150 * fade).toInt().coerceIn(0, 255)), alpha(0xFF1A1408.toInt(), a), 0x00000000),
                floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawOval(o.x - o.radius, o.y - o.radius * 0.46f, o.x + o.radius, o.y + o.radius * 0.46f, fill)
            fill.shader = null
            stroke.color = alpha(0xFF8C7433.toInt(), (110 * fade).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.6f
            c.drawOval(o.x - o.radius * 0.94f, o.y - o.radius * 0.43f, o.x + o.radius * 0.94f, o.y + o.radius * 0.43f, stroke)
            // 기름 표면에 비치는 기름방울
            fill.color = alpha(0xFFB99A3D.toInt(), (90 * fade).toInt().coerceIn(0, 255))
            for (i in 0 until 5) {
                val ang = (o.pulse * 0.6f + i * 1.26f)
                val r = o.radius * (0.25f + (i % 3) * 0.22f)
                c.drawCircle(o.x + cos(ang) * r, o.y + sin(ang) * r * 0.42f, 2f + (i % 2), fill)
            }
            if (o.burning) {
                val flick = 0.6f + 0.4f * sin(w.elapsed * 17f + o.x).toFloat()
                fill.shader = RadialGradient(
                    o.x, o.y - 10f, o.radius * 1.1f,
                    intArrayOf(0xFFFFD27A.toInt(), alpha(0xFFFF7B3C.toInt(), 150), 0x00000000),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawOval(o.x - o.radius, o.y - o.radius * 0.6f, o.x + o.radius, o.y + o.radius * 0.5f, fill)
                fill.shader = null
                // 타오르는 불길
                for (i in 0 until 7) {
                    val ang = i * 0.9f + o.pulse * 1.7f
                    val fx = o.x + cos(ang) * o.radius * 0.6f
                    val fy = o.y + sin(ang) * o.radius * 0.25f
                    val h = (16f + (i % 3) * 9f) * flick * fade
                    path.reset()
                    path.moveTo(fx - 5f, fy)
                    path.quadTo(fx - 2f, fy - h * 0.6f, fx, fy - h)
                    path.quadTo(fx + 3f, fy - h * 0.5f, fx + 5f, fy)
                    path.close()
                    fill.color = alpha(if (i % 2 == 0) 0xFFFFC24D.toInt() else 0xFFFF8A3D.toInt(), (150 * fade).toInt().coerceIn(0, 255))
                    c.drawPath(path, fill)
                }
                // 연기가 피어오른다
                fill.color = alpha(0xFF3A3A3A.toInt(), (60 * fade).toInt().coerceIn(0, 255))
                for (i in 0 until 3) {
                    val ang = i * 2.1f + o.pulse
                    c.drawCircle(o.x + cos(ang) * o.radius * 0.4f, o.y - 18f - i * 8f, 5f + i * 2f, fill)
                }
            }
        }
        // 주문 토템
        for (t in w.totems) {
            val fade = t.fade
            if (fade <= 0.03f) continue
            val sway = sin(w.elapsed * 3.2f).toFloat() * 1.6f
            fill.color = 0x55000000
            c.drawOval(t.x - 12f, t.y - 4f, t.x + 12f, t.y + 4f, fill)
            stroke.color = alpha(0xFF8A6A46.toInt(), (230 * fade).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 3.4f
            c.drawLine(t.x + sway, t.y, t.x - sway * 0.6f, t.y - 30f, stroke)
            // 토템 머리 — 깎은 나무 얼굴과 보석
            fill.color = alpha(0xFF9C7A4E.toInt(), (240 * fade).toInt().coerceIn(0, 255))
            c.drawCircle(t.x - sway * 0.6f, t.y - 34f, 7.5f, fill)
            stroke.color = alpha(0xFF5A4326.toInt(), (220 * fade).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.2f
            c.drawCircle(t.x - sway * 0.6f, t.y - 34f, 7.5f, stroke)
            val pulse = 0.5f + 0.5f * sin(t.pulse * 8f).toFloat()
            fill.color = alpha(w.stats.fx.element.color, ((160 + pulse * 90) * fade).toInt().coerceIn(0, 255))
            c.drawCircle(t.x - sway * 0.6f, t.y - 34f, 2.6f + pulse * 1.6f, fill)
            if (pulse > 0.6f) {
                glowAt(c, t.x - sway * 0.6f, t.y - 34f, 16f, w.stats.fx.element.color, (120 * fade).toInt().coerceIn(0, 255))
            }
            // 토템 고리
            stroke.color = alpha(w.stats.fx.element.color, (70 * fade).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.2f
            c.drawOval(t.x - 20f, t.y - 8f, t.x + 20f, t.y + 8f, stroke)
        }
    }

    /** 서리 폭탄의 보주와 켈라리의 분신 — 바닥에 놓이는 것들 */
    private fun drawFrostBombs(c: Canvas, w: BattleWorld) {
        for (b in w.frostBombs) {
            val t = (b.fuse / BattleWorld.FROST_BOMB_FUSE).coerceIn(0f, 1f)
            val beat = 0.6f + 0.4f * sin(b.pulse * 6.0).toFloat()
            // 기폭이 가까울수록 밝게 맥동한다.
            val glow = (0.45f + (1f - t) * 0.55f) * beat
            glowAt(c, b.x, b.y - 22f, 26f, 0xFF9BE8FF.toInt(), (150 * glow).toInt().coerceIn(0, 255))
            fill.shader = RadialGradient(
                b.x, b.y - 22f, 13f,
                intArrayOf(0xFFF2FDFF.toInt(), alpha(0xFF9BE8FF.toInt(), 220), alpha(0xFF4FA8D8.toInt(), 90), 0x00000000),
                floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(b.x, b.y - 22f, 13f, fill)
            fill.shader = null
            // 보주의 가시 결정
            stroke.color = alpha(0xFFDFF9FF.toInt(), 210)
            stroke.strokeWidth = 1.1f
            for (i in 0 until 6) {
                val a = i * 1.047f + b.pulse * 0.6f
                c.drawLine(
                    b.x + cos(a) * 10f, b.y - 22f + sin(a) * 10f,
                    b.x + cos(a) * (15f + beat * 3f), b.y - 22f + sin(a) * (15f + beat * 3f), stroke,
                )
            }
            // 남은 시간 고리
            stroke.color = alpha(0xFF9BE8FF.toInt(), (90 + 90 * glow).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.2f
            c.drawOval(b.x - 96f * t, b.y - 40f * t, b.x + 96f * t, b.y + 40f * t, stroke)
        }
        val decoy = w.spiritDecoy ?: return
        val fade = decoy.fade
        if (fade <= 0.03f) return
        // 모래로 빚은 분신 — 반투명하게 흔들린다
        val sway = sin(w.elapsed * 5.0).toFloat() * 3f
        fill.color = alpha(0xFFD8C3A0.toInt(), (110 * fade).toInt().coerceIn(0, 255))
        c.drawOval(decoy.x - 13f, decoy.y - 5f, decoy.x + 13f, decoy.y + 5f, fill)
        stroke.color = alpha(0xFFE8D9B8.toInt(), (170 * fade).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 3.2f
        c.drawLine(decoy.x + sway, decoy.y - 46f, decoy.x, decoy.y - 8f, stroke)
        c.drawCircle(decoy.x + sway, decoy.y - 52f, 6.5f, stroke)
        fill.color = alpha(0xFFF2E6C8.toInt(), (200 * fade).toInt().coerceIn(0, 255))
        c.drawCircle(decoy.x + sway, decoy.y - 52f, 3.4f, fill)
        // 다시 쓰면 터진다는 표시
        stroke.color = alpha(0xFFE8D9B8.toInt(), (70 * fade).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 1f
        c.drawOval(decoy.x - 30f, decoy.y - 12f, decoy.x + 30f, decoy.y + 12f, stroke)
    }

    private fun drawGroundFx(c: Canvas, w: BattleWorld) {        for (fx in w.effects) {
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
                FxKind.OIL_SPLASH -> {
                    val a = (1f - t).coerceIn(0f, 1f)
                    val r = fx.radius * (0.35f + t * 0.9f)
                    fill.color = alpha(0xFF3A2E12.toInt(), (110 * a).toInt().coerceIn(0, 255))
                    c.drawOval(fx.x - r, fx.y - r * 0.44f, fx.x + r, fx.y + r * 0.44f, fill)
                    stroke.color = alpha(fx.color, (150 * a).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 1.6f * fx.width
                    c.drawOval(fx.x - r, fx.y - r * 0.44f, fx.x + r, fx.y + r * 0.44f, stroke)
                    // 튀어 오른 기름방울
                    fill.color = alpha(0xFFB99A3D.toInt(), (170 * a).toInt().coerceIn(0, 255))
                    for (i in 0 until 8) {
                        val ang = i * 0.785f + (fx.seed % 13) * 0.1f
                        val d = r * (0.4f + ((i * 37 + fx.seed) % 60) / 100f)
                        c.drawCircle(fx.x + cos(ang) * d, fx.y + sin(ang) * d * 0.44f, 2.4f * a + 0.6f, fill)
                    }
                }
                FxKind.FROST_NOVA -> {
                    val a = (1f - t).coerceIn(0f, 1f)
                    val r = fx.radius * (0.3f + t * 0.95f)
                    stroke.color = alpha(fx.color, (190 * a).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 2.4f * fx.width
                    c.drawOval(fx.x - r, fx.y - r * 0.44f, fx.x + r, fx.y + r * 0.44f, stroke)
                    // 톱니처럼 튀어나온 냉기 조각
                    for (i in 0 until 12) {
                        val ang = i * 0.524f
                        val inner = r * 0.78f
                        val outer = r * (1.02f + ((i * 29 + fx.seed) % 40) / 100f)
                        c.drawLine(
                            fx.x + cos(ang) * inner, fx.y + sin(ang) * inner * 0.44f,
                            fx.x + cos(ang) * outer, fx.y + sin(ang) * outer * 0.44f, stroke,
                        )
                    }
                    stroke.color = alpha(0xFFFFFFFF.toInt(), (120 * a).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 1.1f
                    c.drawOval(fx.x - r * 0.6f, fx.y - r * 0.26f, fx.x + r * 0.6f, fx.y + r * 0.26f, stroke)
                }
                FxKind.DUST -> {
                    val a = (1f - t).coerceIn(0f, 1f)
                    val r = fx.radius * (0.35f + t * 1.05f)
                    fill.color = alpha(fx.color, (46 * a).toInt().coerceIn(0, 255))
                    c.drawOval(fx.x - r, fx.y - r * 0.4f, fx.x + r, fx.y + r * 0.4f, fill)
                    stroke.color = alpha(fx.color, (110 * a).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 1.8f * fx.width
                    c.drawOval(fx.x - r, fx.y - r * 0.4f, fx.x + r, fx.y + r * 0.4f, stroke)
                }
                FxKind.SIGIL -> {
                    val a = (1f - t).coerceIn(0f, 1f)
                    val r = fx.radius * (0.55f + 0.35f * t) * a
                    if (r <= 1f) continue
                    stroke.color = alpha(fx.color, (170 * a).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 2f
                    c.drawOval(fx.x - r, fx.y - r * 0.46f, fx.x + r, fx.y + r * 0.46f, stroke)
                    stroke.strokeWidth = 1.2f
                    c.drawOval(fx.x - r * 0.62f, fx.y - r * 0.29f, fx.x + r * 0.62f, fx.y + r * 0.29f, stroke)
                    // 문양 — 회전하는 오각 표시
                    for (i in 0 until 5) {
                        val ang = (fx.seed % 17) * 0.1f + i * 1.256f + t * 1.6f
                        val ax = fx.x + cos(ang) * r * 0.72f
                        val ay = fx.y + sin(ang) * r * 0.33f
                        val bx = fx.x + cos(ang + 2.09f) * r * 0.72f
                        val by = fx.y + sin(ang + 2.09f) * r * 0.33f
                        c.drawLine(ax, ay, bx, by, stroke)
                    }
                    fill.color = alpha(fx.color, (90 * a).toInt().coerceIn(0, 255))
                    c.drawOval(fx.x - r * 0.3f, fx.y - r * 0.14f, fx.x + r * 0.3f, fx.y + r * 0.14f, fill)
                }
                FxKind.SHIFT -> {
                    val a = (1f - t).coerceIn(0f, 1f)
                    val r = fx.radius * (0.35f + t * 1.1f)
                    stroke.color = alpha(fx.color, (200 * a).toInt().coerceIn(0, 255))
                    stroke.strokeWidth = 3f * fx.width
                    c.drawOval(fx.x - r, fx.y - r * 0.55f, fx.x + r, fx.y + r * 0.55f, stroke)
                    fill.shader = RadialGradient(
                        fx.x, fx.y - 30f, r,
                        intArrayOf(alpha(fx.color, (110 * a).toInt().coerceIn(0, 255)), 0x00000000), null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(fx.x, fx.y - 30f, r, fill)
                    fill.shader = null
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
                FxKind.OIL_FLAME -> drawOilFlame(c, fx, t)
                FxKind.ARC_WEB -> drawArcWeb(c, fx, t)
                FxKind.CRYSTAL -> drawCrystal(c, fx, t)
                FxKind.WING_GUST -> drawWingGust(c, fx, t)
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
        // 바깥으로 퍼지는 옅은 빛무리 → 굵은 몸통 → 흰 심 순서로 겹쳐 인게임 검기처럼 만든다.
        stroke.color = alpha(fx.color, ((1f - t) * 42).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 13f * fx.width * (1f - t * 0.6f) + 1f
        rect.set(fx.x - r, fx.y - r, fx.x + r, fx.y + r)
        c.drawArc(rect, Math.toDegrees(startA.toDouble()).toFloat(), Math.toDegrees((endA - startA).toDouble()).toFloat(), false, stroke)
        for (i in 0 until 3) {
            stroke.color = alpha(fx.color, ((1f - t) * (160 - i * 40)).toInt().coerceIn(0, 255))
            stroke.strokeWidth = (4.6f - i * 1.2f) * fx.width
            rect.set(fx.x - r, fx.y - r, fx.x + r, fx.y + r)
            c.drawArc(rect, Math.toDegrees(startA.toDouble()).toFloat(), Math.toDegrees((endA - startA).toDouble()).toFloat(), false, stroke)
        }
        stroke.color = alpha(0xFFFFFFFF.toInt(), ((1f - t) * 190).toInt().coerceIn(0, 255))
        stroke.strokeWidth = 1.3f * fx.width
        rect.set(fx.x - r, fx.y - r, fx.x + r, fx.y + r)
        c.drawArc(rect, Math.toDegrees(startA.toDouble()).toFloat(), Math.toDegrees((endA - startA).toDouble()).toFloat(), false, stroke)
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

    /** 기름에 붙은 불길 — 방향을 따라 혀처럼 타오른다 */
    private fun drawOilFlame(c: Canvas, fx: Fx, t: Float) {
        val a = (1f - t).coerceIn(0f, 1f)
        val base = fx.angle
        for (i in 0 until 6) {
            val ang = base + (i - 2.5f) * 0.32f + sin(t * 9f + i) * 0.12f
            val len = fx.radius * (0.45f + ((i * 31 + fx.seed) % 50) / 100f) * (0.6f + a * 0.7f)
            val w = 6f + (i % 2) * 3f
            path.reset()
            path.moveTo(fx.x - 3f, fx.y)
            path.quadTo(fx.x + cos(ang) * len * 0.4f - w, fx.y + sin(ang) * len * 0.4f,
                fx.x + cos(ang) * len, fx.y + sin(ang) * len)
            path.quadTo(fx.x + cos(ang) * len * 0.4f + w, fx.y + sin(ang) * len * 0.4f, fx.x + 3f, fx.y)
            path.close()
            fill.color = alpha(if (i % 2 == 0) 0xFFFFC24D.toInt() else fx.color, (170 * a).toInt().coerceIn(0, 255))
            c.drawPath(path, fill)
        }
        glowAt(c, fx.x, fx.y, fx.radius * 0.6f, 0xFFFF8A3D.toInt(), (110 * a).toInt().coerceIn(0, 255))
    }

    /** 여러 갈래로 튀는 번개 그물 */
    private fun drawArcWeb(c: Canvas, fx: Fx, t: Float) {
        val a = (1f - t).coerceIn(0f, 1f)
        if (a <= 0.02f) return
        val branches = 7
        var seed = fx.seed
        for (i in 0 until branches) {
            seed = seed * 1664525 + 1013904223
            val ang = fx.angle + (i - branches / 2f) * (3.14f / branches) + ((seed shr 8) % 20 - 10) * 0.02f
            val len = fx.radius * (0.6f + ((seed shr 3) % 45) / 100f) * (0.7f + t * 0.6f)
            val nx = -sin(ang)
            val ny = cos(ang)
            path.reset()
            path.moveTo(fx.x, fx.y)
            var prev = 0f
            for (s in 1..4) {
                val p = s / 4f
                val jitter = (((seed shr (s * 5)) and 0x3F) / 63f - 0.5f) * 16f
                path.lineTo(fx.x + cos(ang) * len * p + nx * jitter, fx.y + sin(ang) * len * p + ny * jitter)
                prev = jitter
            }
            val flicker = 0.55f + 0.45f * sin(t * 30f + i).toFloat()
            stroke.color = alpha(fx.color, (60 * a * flicker).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 6f * a + 1.4f
            c.drawPath(path, stroke)
            stroke.color = alpha(0xFFFFF6C8.toInt(), (210 * a * flicker).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.4f
            c.drawPath(path, stroke)
        }
        fill.color = alpha(0xFFFFF6C8.toInt(), (170 * a).toInt().coerceIn(0, 255))
        c.drawCircle(fx.x, fx.y, 3.4f + t * 2f, fill)
    }

    /** 바닥에서 솟는 얼음 결정 */
    private fun drawCrystal(c: Canvas, fx: Fx, t: Float) {
        val a = (1f - t).coerceIn(0f, 1f)
        if (a <= 0.03f) return
        val spikes = 9
        for (i in 0 until spikes) {
            val ang = fx.angle + (i - spikes / 2f) * 0.34f + ((fx.seed shr i) and 7) * 0.03f
            val d = fx.radius * (0.25f + ((i * 23 + fx.seed) % 70) / 100f)
            val bx = fx.x + cos(ang) * d
            val by = fx.y + sin(ang) * d * 0.55f
            val h = (10f + ((fx.seed shr (i + 2)) and 15)) * (0.4f + a * 0.9f)
            val w = 3.2f + (i % 3)
            path.reset()
            path.moveTo(bx - w, by)
            path.lineTo(bx, by - h)
            path.lineTo(bx + w, by)
            path.close()
            fill.color = alpha(0xFFDFF9FF.toInt(), (190 * a).toInt().coerceIn(0, 255))
            c.drawPath(path, fill)
            stroke.color = alpha(0xFF9BE8FF.toInt(), (220 * a).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 0.9f
            c.drawPath(path, stroke)
        }
        glowAt(c, fx.x, fx.y - 6f, fx.radius * 0.5f, 0xFF9BE8FF.toInt(), (80 * a).toInt().coerceIn(0, 255))
    }

    /** 날개·회오리가 일으키는 돌풍 — 휘돌아 퍼지는 호 */
    private fun drawWingGust(c: Canvas, fx: Fx, t: Float) {
        val a = (1f - t).coerceIn(0f, 1f)
        if (a <= 0.03f) return
        for (i in 0 until 4) {
            val r = fx.radius * (0.3f + t * 0.8f + i * 0.08f)
            val spin = Math.toDegrees((fx.angle + t * 5f + i * 0.7f).toDouble()).toFloat()
            stroke.color = alpha(fx.color, ((120 - i * 22) * a).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 2.4f - i * 0.4f
            rect.set(fx.x - r, fx.y - r * 0.42f, fx.x + r, fx.y + r * 0.42f)
            c.drawArc(rect, spin, 250f - i * 20f, false, stroke)
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
                    // 전기불꽃: 지나온 자리를 지그재그 번개 줄기로 잇는다
                    drawSparkBolt(c, p)
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
                ProjKind.OIL -> {
                    // 기름 덩이 — 검은 덩어리에 기름방울이 흘러내린다
                    drawTrail(c, p, 0xFF8C7433.toInt(), 9f)
                    fill.shader = RadialGradient(
                        p.x, p.y, 14f,
                        intArrayOf(0xFFE0C56A.toInt(), alpha(0xFF8C7433.toInt(), 210), 0x00000000),
                        null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 14f, fill)
                    fill.shader = null
                    fill.color = 0xFF4A3A18.toInt()
                    c.drawCircle(p.x, p.y, 4.6f, fill)
                    // 표효에 달린 방울
                    fill.color = alpha(0xFFC7A64B.toInt(), 200)
                    for (i in 0 until 3) {
                        val ang = p.trail.size * 0.2f + i * 2.09f
                        c.drawCircle(p.x + cos(ang) * 7f, p.y + sin(ang) * 5f, 1.7f, fill)
                    }
                }
                ProjKind.CHARGED_OIL -> {
                    // 전기를 띤 기름 — 검은 덩이에 보랏빛·연둣빛 불꽃이 감긴다
                    drawTrail(c, p, 0xFF6FE08C.toInt(), 9f)
                    fill.shader = RadialGradient(
                        p.x, p.y, 21f,
                        intArrayOf(alpha(0xFF6FE08C.toInt(), 120), alpha(0xFFB07AFF.toInt(), 80), 0x00000000),
                        null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 21f, fill)
                    fill.shader = null
                    fill.shader = RadialGradient(
                        p.x, p.y, 13f,
                        intArrayOf(0xFFFFF6C8.toInt(), alpha(0xFFB99A3D.toInt(), 200), 0x00000000),
                        null, Shader.TileMode.CLAMP,
                    )
                    c.drawCircle(p.x, p.y, 13f, fill)
                    fill.shader = null
                    stroke.color = alpha(0xFFFFF3A8.toInt(), 220)
                    stroke.strokeWidth = 1.3f
                    for (i in 0 until 3) {
                        val a0 = p.trail.size * 0.4f + i * 2.09f
                        c.drawLine(
                            p.x + cos(a0) * 6f, p.y + sin(a0) * 6f,
                            p.x + cos(a0 + 2.4f) * 9f, p.y + sin(a0 + 2.4f) * 9f, stroke,
                        )
                    }
                }
            }
        }
    }

    /**
     * 전기불꽃 줄기 — 지나온 자리를 점마다 옆으로 흔들어 지그재그 번개로 잇는다.
     *
     * 가장 아래에 보랏빛 번개를 넓게 깔고, 그 위에 청백색 심과 흰 줄기를 올린다.
     * 꼬리 쪽은 옅게, 머리 쪽은 진하게 그려 방금 터진 불꽃처럼 보이게 하고,
     * 중간에서 한두 갈래가 옆으로 갈라진다. 머리에는 밝은 전구가 맺힌다.
     */
    private fun drawSparkBolt(c: Canvas, p: Projectile) {
        val trail = p.trail
        val points = trail.size / 2
        val flicker = 0.72f + 0.28f * sin((p.age * 42.0 + (p.seed % 11)).toDouble()).toFloat()
        if (points >= 3) {
            val amp = 3.5f + 7f * p.wobble
            val half = points / 2
            stroke.strokeCap = Paint.Cap.ROUND
            for (part in 0..1) {
                val from = if (part == 0) 0 else half - 1
                val to = if (part == 0) half else points - 1
                val a = if (part == 0) 0.42f else 1f
                buildSparkPath(trail, from, to, p, amp)
                if (part == 1) path.lineTo(p.x, p.y)
                stroke.color = alpha(0xFF7B5BFF.toInt(), (74 * a * flicker).toInt().coerceIn(0, 255))
                stroke.strokeWidth = 8.5f
                c.drawPath(path, stroke)
                stroke.color = alpha(0xFF9FD4FF.toInt(), (160 * a * flicker).toInt().coerceIn(0, 255))
                stroke.strokeWidth = 3.2f
                c.drawPath(path, stroke)
                stroke.color = alpha(0xFFFFFFFF.toInt(), (218 * a * flicker).toInt().coerceIn(0, 255))
                stroke.strokeWidth = 1.25f
                c.drawPath(path, stroke)
            }
            // 중간에서 옆으로 갈라지는 잔 가지
            for (k in 0 until 2) {
                val idx = (points * (if (k == 0) 5 else 8) / 12).coerceIn(1, points - 1)
                val bx = trail[2 * idx]
                val by = trail[2 * idx + 1]
                val baseAng = kotlin.math.atan2(p.dirY, p.dirX) + (if (k == 0) 1.15f else -1.15f)
                val len = 15f + abs(sparkNoise(p.seed, idx + k * 7)) * 34f
                path.reset()
                path.moveTo(bx, by)
                path.lineTo(
                    bx + cos(baseAng) * len * 0.55f + sparkNoise(p.seed, idx + 3) * 7f,
                    by + sin(baseAng) * len * 0.55f,
                )
                path.lineTo(bx + cos(baseAng) * len, by + sin(baseAng) * len)
                stroke.color = alpha(0xFFDBEEFF.toInt(), (150 * flicker).toInt().coerceIn(0, 255))
                stroke.strokeWidth = 1.1f
                c.drawPath(path, stroke)
            }
        }
        // 머리에 맺히는 전구 — 보랏빛 테두리 안의 청백색 빛
        fill.shader = RadialGradient(
            p.x, p.y, 17f,
            intArrayOf(
                0xFFFFFFFF.toInt(),
                alpha(0xFFBEE6FF.toInt(), 210),
                alpha(0xFF7B5BFF.toInt(), 90),
                0x00000000,
            ),
            floatArrayOf(0f, 0.32f, 0.62f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(p.x, p.y, 17f, fill)
        fill.shader = null
        fill.color = alpha(0xFFFFFFFF.toInt(), (235 * flicker).toInt().coerceIn(0, 255))
        c.drawCircle(p.x, p.y, 2.6f, fill)
    }

    /** 전기불꽃 줄기 하나를 점마다 흔들어 그린다 (꼬리→머리) */
    private fun buildSparkPath(trail: MutableList<Float>, from: Int, to: Int, p: Projectile, amp: Float) {
        path.reset()
        if (from >= to) return
        path.moveTo(trail[2 * from], trail[2 * from + 1])
        for (i in from + 1..to) {
            val x = trail[2 * i]
            val y = trail[2 * i + 1]
            val dx = x - trail[2 * i - 2]
            val dy = y - trail[2 * i - 1]
            val len = hypot(dx, dy).coerceAtLeast(0.001f)
            val o = sparkNoise(p.seed, i) * amp
            path.lineTo(x - dy / len * o, y + dx / len * o)
        }
    }

    /** 궤적을 흔들 때 쓰는 고정 난수 (-0.5~0.5) */
    private fun sparkNoise(seed: Int, i: Int): Float {
        var h = seed * 374761393 + i * 668265263
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0xFFFF) / 65535f - 0.5f
    }

    private fun drawTrail(c: Canvas, p: Projectile, color: Int, radius: Float) {        val trail = p.trail
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
        // 쓰러지면 가장자리가 붉게 어두워지고, 마지막 한 방의 섬광이 한 번 번진다.
        if (w.state == BattleWorld.State.DEFEAT) {
            val age = w.deathAge
            val gloom = DeathFx.gloom(age)
            if (gloom > 0.01f) {
                fill.shader = RadialGradient(
                    width / 2f, height / 2f, max(width, height) * 0.66f,
                    intArrayOf(
                        alpha(0xFF2A0508.toInt(), (gloom * 60).toInt()),
                        alpha(0xFF3A070B.toInt(), (gloom * 150).toInt()),
                        alpha(0xFF120204.toInt(), (gloom * 235).toInt()),
                    ),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
                fill.shader = null
            }
            val flash = DeathFx.flash(age)
            if (flash > 0.01f) {
                fill.color = alpha(0xFFFF3B32.toInt(), (flash * 120).toInt().coerceIn(0, 255))
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            }
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
        // 야수 변신·기름 지대 상태
        val beast = w.beastHud()
        val beastLines = if (beast.isBlank()) emptyList()
        else wrapText("\uD83D\uDC3E $beast", max(80f * dp, badgeW - 20f * dp), 1)
        val oil = w.oilHud()
        val oilLines = if (oil.isBlank()) emptyList()
        else wrapText("\u25CF $oil", max(80f * dp, badgeW - 20f * dp), 1)
        val badgeH = (27f + (triggerLines.size + ascendLines.size + comboLines.size + minionLines.size +
            beastLines.size + oilLines.size) * 11f) * dp

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
        if (beastLines.isNotEmpty()) {
            textLight.color = 0xFFFFC9A0.toInt()
            textLight.textSize = 9.5f * dp
            beastLines.forEachIndexed { index, line ->
                val y = pad + (33f + (triggerLines.size + ascendLines.size + comboLines.size +
                    minionLines.size + index) * 11f) * dp
                c.drawText(line, pad + 10f * dp, y, textLight)
            }
            textLight.textSize = 10f * dp
        }
        if (oilLines.isNotEmpty()) {
            textLight.color = 0xFFC7A64B.toInt()
            textLight.textSize = 9.5f * dp
            oilLines.forEachIndexed { index, line ->
                val y = pad + (33f + (triggerLines.size + ascendLines.size + comboLines.size +
                    minionLines.size + beastLines.size + index) * 11f) * dp
                c.drawText(line, pad + 10f * dp, y, textLight)
            }
            textLight.textSize = 10f * dp
        }
        drawChargeStrip(c, w, pad, pad + badgeH + 6f * dp)
        drawComboChips(c, w)

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

    /**
     * 콤보 칩 — 지금 돌리고 있는 순서를 전투 화면 아래에 칩으로 보여 준다.
     * 지금 서 있는 칸만 밝게 켜지고 나머지는 흐리게 남는다.
     */
    /**
     * 충전 3종 표시 — 권능(파랑)·인내(빨강)·격분(초록).
     * 배지 아래에 작은 알약 세 개로 나란히 두고, 가득 찬 충전은 테두리를 밝게 그린다.
     */
    private fun drawChargeStrip(c: Canvas, w: BattleWorld, x0: Float, y0: Float) {
        if (!w.chargeRules.usesCharges) return
        var x = x0
        textLight.textAlign = Paint.Align.LEFT
        textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textLight.textSize = 9.5f * dp
        for (kind in ChargeKind.values()) {
            val count = w.chargeCount(kind)
            val cap = w.chargeRules.cap(kind)
            val label = "${kind.label} $count"
            val wPill = textLight.measureText(label) + 22f * dp
            if (x + wPill > width - 10f * dp) break
            rect.set(x, y0, x + wPill, y0 + 17f * dp)
            fill.color = 0xCC0E1117.toInt()
            c.drawRoundRect(rect, 8f * dp, 8f * dp, fill)
            stroke.color = alpha(kind.color, if (count > 0) 225 else 70)
            stroke.strokeWidth = 1f * dp
            c.drawRoundRect(rect, 8f * dp, 8f * dp, stroke)
            // 알약 안의 점 — 남은 충전만큼 밝다
            fill.color = alpha(kind.color, if (count > 0) 255 else 80)
            c.drawCircle(x + 8f * dp, y0 + 8.5f * dp, 3f * dp, fill)
            textLight.color = alpha(0xFFE8EEF8.toInt(), if (count > 0) 255 else 120)
            c.drawText(label, x + 14f * dp, y0 + 12f * dp, textLight)
            if (count >= cap) {
                stroke.color = alpha(kind.color, 120)
                stroke.strokeWidth = 0.8f * dp
                rect.set(x + 2f * dp, y0 + 2f * dp, x + wPill - 2f * dp, y0 + 15f * dp)
                c.drawRoundRect(rect, 7f * dp, 7f * dp, stroke)
            }
            x += wPill + 5f * dp
        }
        textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private fun drawComboChips(c: Canvas, w: BattleWorld) {
        val plan = w.comboPlan
        if (plan.size <= 1) return
        val pad = 10f * dp
        textLight.textAlign = Paint.Align.LEFT
        textLight.textSize = 9f * dp
        var x = pad
        val y = height - 30f * dp
        val maxChips = 7
        val shown = if (plan.size > maxChips) plan.take(maxChips) else plan
        shown.forEachIndexed { index, step ->
            val active = index == w.comboCurrent
            val label = step.label + if (step.repeats > 1) " x${step.repeats}" else ""
            val tw = textLight.measureText(label)
            val chipW = tw + 12f * dp
            if (x + chipW > width - pad) return@forEachIndexed
            rect.set(x, y - 11f * dp, x + chipW, y + 4f * dp)
            fill.color = if (active) 0xDD2A3446.toInt() else 0xAA11151C.toInt()
            c.drawRoundRect(rect, 6f * dp, 6f * dp, fill)
            stroke.color = if (active) 0xFFFFD98A.toInt() else 0x555B6472
            stroke.strokeWidth = if (active) 1.4f * dp else 0.8f * dp
            c.drawRoundRect(rect, 6f * dp, 6f * dp, stroke)
            textLight.color = if (active) 0xFFFFE9B0.toInt() else 0xFF9AA3B0.toInt()
            c.drawText(label, x + 6f * dp, y, textLight)
            x += chipW + 4f * dp
        }
        if (plan.size > maxChips) {
            textLight.color = 0xFF9AA3B0.toInt()
            c.drawText("+${plan.size - maxChips}", x + 2f * dp, y, textLight)
        }
        textLight.textSize = 10f * dp
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

    /**
     * 사망 화면.
     *
     * 붉은 배너 → 사망 원인 → 스킬별 DPS 기록 → 다시 시작·닫기 순서로 알파가 0에서 1로
     * 차오른다. 전장 위에 어두운 막을 깔고 카드 안에서만 글자를 써서, 세로로 좁은
     * 화면에서도 글자가 겹치거나 잘리지 않게 한다.
     */
    private fun drawDeathScreen(c: Canvas, w: BattleWorld) {
        if (w.state != BattleWorld.State.DEFEAT || deathDismissed) return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 1f || vh <= 1f) return
        val age = w.deathAge
        val pad = 12f * dp

        // ---- 어두운 막
        val dim = DeathFx.alpha(age, 0.05f, 0.66f)
        fill.color = alpha(0xFF05070B.toInt(), (dim * 185f).toInt().coerceIn(0, 255))
        c.drawRect(0f, 0f, vw, vh, fill)

        val left = pad
        val right = vw - pad
        val cardW = right - left
        var y = pad

        // ---- 붉은 사망 배너
        val ba = DeathFx.bannerAlpha(age)
        if (ba > 0.002f) {
            val rise = (1f - ba) * 12f * dp
            rect.set(left, y + rise, right, y + rise + 66f * dp)
            fill.shader = LinearGradient(
                left, rect.top, right, rect.bottom,
                intArrayOf(
                    alpha(0xFF7A1216.toInt(), (ba * 255).toInt()),
                    alpha(0xFF2A0608.toInt(), (ba * 240).toInt()),
                ),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRoundRect(rect, 10f * dp, 10f * dp, fill)
            fill.shader = null
            stroke.color = alpha(0xFFFF6A5A.toInt(), (ba * 210f).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.4f * dp
            c.drawRoundRect(rect, 10f * dp, 10f * dp, stroke)

            text.textAlign = Paint.Align.LEFT
            text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            text.textSize = 26f * dp
            text.color = alpha(0xFFFFEDE7.toInt(), (ba * 255).toInt().coerceIn(0, 255))
            c.drawText("사망", left + 16f * dp, rect.top + 34f * dp, text)

            textLight.textAlign = Paint.Align.LEFT
            textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textLight.textSize = 10f * dp
            textLight.color = alpha(0xFFFFB4A6.toInt(), (ba * 230f).toInt().coerceIn(0, 255))
            val sub = String.format(
                Locale.KOREA, "전투 %.1f초 · 피격 %d회 · 처치 %d/%d",
                w.elapsed, w.hitsTaken, w.kills, w.totalEnemies,
            )
            c.drawText(sub, left + 16f * dp, rect.top + 52f * dp, textLight)

            // 오른쪽 끝의 붉은 표식
            val cx = right - 28f * dp
            val cy = rect.top + 33f * dp
            glowAt(c, cx, cy, 20f * dp, 0xFFFF3B3B.toInt(), (ba * 80f).toInt().coerceIn(0, 255))
            stroke.color = alpha(0xFFFF8A7A.toInt(), (ba * 200f).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.5f * dp
            c.drawLine(cx - 5f * dp, cy - 5f * dp, cx + 5f * dp, cy + 5f * dp, stroke)
            c.drawLine(cx - 5f * dp, cy + 5f * dp, cx + 5f * dp, cy - 5f * dp, stroke)
            y += 66f * dp + 8f * dp
        } else {
            y += 74f * dp
        }

        // ---- 사망 원인 카드
        val cause = w.deathCause
        val ca = DeathFx.causeAlpha(age)
        if (ca > 0.002f && cause != null) {
            textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textLight.textSize = 14.5f * dp
            val heads = wrapText(cause.headline, cardW - 26f * dp, 2)
            textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val causeH = (44f + heads.size * 18f) * dp
            rect.set(left, y, right, y + causeH)
            fill.color = alpha(0xFF12080A.toInt(), (ca * 232f).toInt().coerceIn(0, 255))
            c.drawRoundRect(rect, 10f * dp, 10f * dp, fill)
            stroke.color = alpha(0xFFB33A32.toInt(), (ca * 180f).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1f * dp
            c.drawRoundRect(rect, 10f * dp, 10f * dp, stroke)

            textLight.textAlign = Paint.Align.LEFT
            textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textLight.textSize = 9f * dp
            textLight.color = alpha(0xFFFF7A6A.toInt(), (ca * 235f).toInt().coerceIn(0, 255))
            c.drawText("사망 원인", left + 13f * dp, y + 16f * dp, textLight)

            textLight.textSize = 14.5f * dp
            textLight.color = alpha(0xFFFFF1EC.toInt(), (ca * 255).toInt().coerceIn(0, 255))
            heads.forEachIndexed { index, line ->
                c.drawText(line, left + 13f * dp, y + (36f + index * 18f) * dp, textLight)
            }
            textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textLight.textSize = 10.5f * dp
            textLight.color = alpha(0xFFFFC9BC.toInt(), (ca * 235f).toInt().coerceIn(0, 255))
            val detailY = y + (38f + heads.size * 18f) * dp
            c.drawText(cause.damageLine, left + 13f * dp, detailY, textLight)

            val recent = cause.recentLine
            if (recent.isNotBlank()) {
                textLight.textSize = 9.5f * dp
                textLight.color = alpha(0xFFB49A96.toInt(), (ca * 220f).toInt().coerceIn(0, 255))
                c.drawText(recent, left + 13f * dp, detailY + 14f * dp, textLight)
            }
            y += causeH + 8f * dp
        } else if (cause == null) {
            y += 0f
        } else {
            y += 0f
        }

        // ---- 스킬별 DPS 기록
        val rows = w.skillTally()
        val buttonH = 46f * dp
        val buttonTop = vh - pad - buttonH
        val headerH = 22f * dp
        val rowH = 23f * dp
        val listTop = y
        val listBottom = buttonTop - 10f * dp
        // 남은 자리에 들어가는 만큼만 줄을 놓는다 — 좁은 화면에서 글자가 겹치지 않게.
        val capacity = DeathFx.rowCapacity(listTop, listBottom, headerH, rowH, reserve = 14f * dp)
        val shown = if (capacity >= 1) SkillTally.pages(rows, capacity) else emptyList()
        val rest = SkillTally.rest(rows, capacity)
        val listH = DeathFx.listHeight(shown.size, headerH, rowH, if (rest > 0) 14f * dp else 0f) * dp
        if (listH > 4f * dp) {
            rect.set(left, listTop, right, listTop + listH + 10f * dp)
            fill.color = alpha(0xFF0C0E13.toInt(), (ca * 226f).toInt().coerceIn(0, 255))
            c.drawRoundRect(rect, 10f * dp, 10f * dp, fill)
            stroke.color = alpha(0xFF3A3F4C.toInt(), (ca * 190f).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1f * dp
            c.drawRoundRect(rect, 10f * dp, 10f * dp, stroke)

            textLight.textAlign = Paint.Align.LEFT
            textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textLight.textSize = 9f * dp
            textLight.color = alpha(0xFFCFE4FF.toInt(), (ca * 235f).toInt().coerceIn(0, 255))
            c.drawText("스킬별 DPS 기록", left + 13f * dp, listTop + 15f * dp, textLight)
            textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textLight.textAlign = Paint.Align.RIGHT
            textLight.textSize = 9f * dp
            textLight.color = alpha(0xFF7E8A9C.toInt(), (ca * 220f).toInt().coerceIn(0, 255))
            c.drawText("누적 피해 · 비율", right - 13f * dp, listTop + 15f * dp, textLight)

            if (shown.isEmpty()) {
                textLight.textAlign = Paint.Align.LEFT
                textLight.textSize = 9.5f * dp
                textLight.color = alpha(0xFF8894A6.toInt(), (ca * 220f).toInt().coerceIn(0, 255))
                c.drawText("기록된 피해가 없다", left + 13f * dp, listTop + headerH + 11f * dp, textLight)
            }
            shown.forEachIndexed { index, row ->
                val ra = DeathFx.rowAlpha(age, index)
                if (ra <= 0.002f) return@forEachIndexed
                val rowTop = listTop + headerH + index * rowH
                val slide = (1f - ra) * 8f * dp
                // 순위 점
                fill.color = alpha(if (index == 0) 0xFFFFC24D.toInt() else 0xFF5C6472.toInt(), (ra * 230f).toInt().coerceIn(0, 255))
                c.drawCircle(left + 15f * dp, rowTop + 9f * dp, 2.2f * dp, fill)

                textLight.textAlign = Paint.Align.LEFT
                textLight.typeface = Typeface.create(Typeface.DEFAULT, if (index == 0) Typeface.BOLD else Typeface.NORMAL)
                textLight.textSize = 10.5f * dp
                textLight.color = alpha(
                    if (index == 0) 0xFFFFE6A8.toInt() else 0xFFE2E8F2.toInt(),
                    (ra * 245f).toInt().coerceIn(0, 255),
                )
                val nameRoom = cardW * 0.56f
                var name = row.name
                while (name.length > 2 && textLight.measureText(name) > nameRoom) name = name.dropLast(2)
                if (name != row.name) name = name.trimEnd() + "…"
                c.drawText(name, left + 22f * dp, rowTop + 12.5f * dp + slide, textLight)

                textLight.textAlign = Paint.Align.RIGHT
                textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textLight.textSize = 10f * dp
                textLight.color = alpha(0xFFDDE6F2.toInt(), (ra * 240f).toInt().coerceIn(0, 255))
                c.drawText(
                    BattleWorld.formatShort(row.damage),
                    right - 44f * dp, rowTop + 12.5f * dp + slide, textLight,
                )
                textLight.textSize = 9f * dp
                textLight.color = alpha(0xFF9FB6D6.toInt(), (ra * 235f).toInt().coerceIn(0, 255))
                c.drawText(
                    String.format(Locale.KOREA, "%d%%", row.share.toInt()),
                    right - 13f * dp, rowTop + 12.5f * dp + slide, textLight,
                )

                // 비율 막대
                val barY = rowTop + 17.5f * dp
                fill.color = alpha(0xFF1B2029.toInt(), (ra * 220f).toInt().coerceIn(0, 255))
                c.drawRoundRect(left + 22f * dp, barY, right - 13f * dp, barY + 2.6f * dp, 1.3f * dp, 1.3f * dp, fill)
                val barW = (right - 13f * dp - (left + 22f * dp)) * (row.share / 100.0).toFloat()
                if (barW > 0.5f) {
                    fill.color = alpha(
                        if (index == 0) 0xFFFFC24D.toInt() else 0xFF5B8FD6.toInt(),
                        (ra * 240f).toInt().coerceIn(0, 255),
                    )
                    c.drawRoundRect(left + 22f * dp, barY, left + 22f * dp + barW, barY + 2.6f * dp, 1.3f * dp, 1.3f * dp, fill)
                }
            }
            if (rest > 0) {
                textLight.textAlign = Paint.Align.LEFT
                textLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textLight.textSize = 9f * dp
                textLight.color = alpha(0xFF7E8A9C.toInt(), (ca * 225f).toInt().coerceIn(0, 255))
                c.drawText(
                    "+ ${rest}개 스킬 더",
                    left + 22f * dp,
                    listTop + headerH + shown.size * rowH + 11f * dp,
                    textLight,
                )
            }
        }

        // ---- 다시 시작 · 닫기
        val bAlpha = DeathFx.buttonAlpha(age)
        if (bAlpha > 0.002f) {
            val gap = 8f * dp
            val bw = (cardW - gap) / 2f
            retryRect.set(left, buttonTop, left + bw, buttonTop + buttonH)
            closeRect.set(right - bw, buttonTop, right, buttonTop + buttonH)

            fill.color = alpha(0xFF8E1A18.toInt(), (bAlpha * 240f).toInt().coerceIn(0, 255))
            c.drawRoundRect(retryRect, 9f * dp, 9f * dp, fill)
            stroke.color = alpha(0xFFFF8A78.toInt(), (bAlpha * 220f).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 1.2f * dp
            c.drawRoundRect(retryRect, 9f * dp, 9f * dp, stroke)
            text.textAlign = Paint.Align.CENTER
            text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            text.textSize = 12.5f * dp
            text.color = alpha(0xFFFFF2EC.toInt(), (bAlpha * 255).toInt().coerceIn(0, 255))
            c.drawText("다시 시작", retryRect.centerX(), retryRect.centerY() + 4.4f * dp, text)

            fill.color = alpha(0xFF181C24.toInt(), (bAlpha * 235f).toInt().coerceIn(0, 255))
            c.drawRoundRect(closeRect, 9f * dp, 9f * dp, fill)
            stroke.color = alpha(0xFF4A5160.toInt(), (bAlpha * 220f).toInt().coerceIn(0, 255))
            c.drawRoundRect(closeRect, 9f * dp, 9f * dp, stroke)
            text.color = alpha(0xFFD6DCE8.toInt(), (bAlpha * 245f).toInt().coerceIn(0, 255))
            c.drawText("닫기", closeRect.centerX(), closeRect.centerY() + 4.4f * dp, text)
        }
        text.textAlign = Paint.Align.LEFT
    }

    /** 사망 화면을 닫는다 — 전장과 버튼은 그대로 두고 카드만 걷는다 */
    fun dismissDeath() {
        deathDismissed = true
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = world ?: return false
        if (w.state != BattleWorld.State.DEFEAT || deathDismissed) return false
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        // 카드가 다 올라오기 전의 터치는 무시한다.
        if (DeathFx.buttonAlpha(w.deathAge) < 0.9f) return true
        val x = event.x
        val y = event.y
        if (retryRect.contains(x, y)) {
            deathDismissed = true
            onRetry?.invoke()
            return true
        }
        if (closeRect.contains(x, y)) {
            dismissDeath()
            onClose?.invoke()
            return true
        }
        return true
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

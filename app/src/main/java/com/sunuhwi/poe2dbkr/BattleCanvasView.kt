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

    private fun schedule() {
        if (frameScheduled) return
        frameScheduled = true
        chrome.postFrameCallback(frameCallback)
    }

    fun attach(world: BattleWorld) {
        this.world = world
        finished = false
        paused = false
        lastFrameNanos = 0L
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
        schedule()
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        if (world == null) return
        paused = false
        lastFrameNanos = 0L
        running = true
        schedule()
    }

    val isPaused: Boolean get() = paused

    fun setSpeed(value: Float) {
        speed = value
    }

    fun stop() {
        running = false
        chrome.removeFrameCallback(frameCallback)
        frameScheduled = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (world != null && !running && !paused) play()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
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
        drawGroundFx(canvas, w)
        drawEntities(canvas, w)
        drawProjectiles(canvas, w)
        drawAirFx(canvas, w)
        drawParticles(canvas, w)
        drawPopups(canvas, w)
        canvas.restore()

        drawVignette(canvas, w)
        drawHud(canvas, w)
        drawBanner(canvas, w)
        if (paused) drawPaused(canvas)
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

    // -------------------------------------------------------------- 엔티티

    private fun drawEntities(canvas: Canvas, w: BattleWorld) {
        val list = ArrayList<Any>(w.enemies.size + 1)
        for (e in w.enemies) if (e.alive || e.deadFade < 1f) list.add(e)
        list.add(w.player)
        list.sortBy { o ->
            when (o) {
                is Enemy -> o.y
                is PlayerState -> o.y
                else -> 0f
            }
        }
        for (o in list) {
            when (o) {
                is Enemy -> drawEnemy(canvas, w, o)
                is PlayerState -> drawPlayer(canvas, w)
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
        val bodyColor = blend(0xFFF4F0E4.toInt(), 0xFFFF5555.toInt(), p.hurtFlash * 0.85f)
        drawStickman(
            c = c, x = p.x, y = p.y, s = 1.62f,
            body = bodyColor, accent = element.color,
            facing = p.facing, walk = p.walkPhase, moving = p.moving,
            attack = p.attackAnim, weapon = st.weapon,
            glow = 0.35f + breath * 0.25f,
        )
        if (w.state == BattleWorld.State.DEFEAT) {
            stroke.color = 0x99FF4444.toInt()
            stroke.strokeWidth = 1.6f
            c.drawLine(p.x - 21f, p.y - 58f, p.x - 9f, p.y - 46f, stroke)
            c.drawLine(p.x - 9f, p.y - 58f, p.x - 21f, p.y - 46f, stroke)
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

        drawStickman(
            c = c, x = e.x, y = e.y, s = scale,
            body = withAlpha(body, (255 * alphaF).toInt()),
            accent = if (e.boss != null) e.boss!!.element.color else e.element.color,
            facing = if (e.x > w.player.x) 3.1415927f else 0f,
            walk = e.walkPhase, moving = true,
            attack = e.attackAnim, weapon = if (e.ranged) WeaponVisualType.STAFF else WeaponVisualType.SWORD,
            glow = 0f,
            boss = e.boss != null,
            bossColor = e.boss?.element?.color ?: baseColor,
            alphaMul = alphaF,
            windup = e.windup,
        )

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
                val handX = x + fwd * 11f * s
                val handY = shY + 3f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x + fwd * 5f * s, handY + 6f * s, stroke)
                drawBow(c, handX, handY, s, facing, attack, body)
            }
            WeaponVisualType.CROSSBOW -> {
                val handX = x + fwd * 10f * s
                val handY = shY + 4f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - fwd * 3f * s, handY + 5f * s, stroke)
                drawCrossbow(c, handX, handY, s, facing, attack, accent)
            }
            WeaponVisualType.STAFF -> {
                val handX = x + fwd * 9f * s
                val handY = shY + 5f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - fwd * 3f * s, handY + 4f * s, stroke)
                drawStaff(c, handX, handY, s, facing, attack, accent, body)
            }
            WeaponVisualType.WAND -> {
                val handX = x + fwd * 10f * s
                val handY = shY + 6f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - fwd * 2f * s, handY + 3f * s, stroke)
                drawShortRod(c, handX, handY, s, facing, attack, accent, 17f)
            }
            WeaponVisualType.SCEPTRE -> {
                val handX = x + fwd * 9f * s
                val handY = shY + 6f * s + up
                stroke.strokeWidth = 2.6f * s
                c.drawLine(x, shY + 1f * s + up, handX, handY, stroke)
                c.drawLine(x, shY + 1f * s + up, x - fwd * 3f * s, handY + 3f * s, stroke)
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
                FxKind.PILLAR -> drawPillar(c, fx, t)
                FxKind.MINION_HIT -> drawStarBurst(c, fx, t)
                FxKind.METEOR -> drawMeteor(c, fx, t)
                FxKind.CYCLONE -> Unit
                else -> Unit
            }
        }
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
        val triggerText = st.triggerText
        val hasTrigger = triggerText.isNotBlank()
        val badgeH = (if (hasTrigger) 41f else 27f) * dp
        val badgeW = min(width - pad * 2f, nameW + chipW + 30f * dp)

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

        if (hasTrigger) {
            textLight.color = 0xFFCFF3FF.toInt()
            textLight.textSize = 9.5f * dp
            var label = "⟳ $triggerText"
            val limit = badgeW - 22f * dp
            while (label.isNotEmpty() && textLight.measureText(label) > limit) {
                label = label.dropLast(2) + "…"
            }
            c.drawText(label, pad + 10f * dp, pad + 33f * dp, textLight)
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

        // 체력 / 보호막
        val barW = min(width * 0.46f, 220f * dp)
        val barX = pad
        val barY = pad + badgeH + 6f * dp
        drawPoolBar(c, barX, barY, barW, st.life, w.player.life, 0xFFD9534F.toInt())
        drawPoolBar(c, barX, barY + 8f * dp, barW, st.es, w.player.es, 0xFF4FA8D9.toInt())

        // 보스 체력
        val boss = w.bossEnemy
        if (boss != null) {
            val barY2 = pad + badgeH + 28f * dp
            val w2 = width - pad * 2f
            text.textAlign = Paint.Align.LEFT
            text.textSize = 10.5f * dp
            text.color = 0xFFFFD9C9.toInt()
            c.drawText("${boss.boss?.name.orEmpty()}  ·  ${boss.boss?.pattern.orEmpty()}", pad, barY2 - 3f * dp, text)
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

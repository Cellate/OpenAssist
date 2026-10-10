package com.openassist.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Circle to Search overlay canvas:
 *  - Mode.CIRCLE: Freehand glowing lasso with Google neon glow effect (extracts bounding crop)
 *  - Mode.BOX: Drag out a rectangular selection with 8 resize handles
 *  - In both modes: 2-finger pinch zooms in/out (0.6x to 12x) and 2-finger pan slides the picture anywhere
 *  - Double-tap toggles zoom
 */
class SelectionView(context: Context) : View(context) {

    enum class Mode { CIRCLE, BOX }

    var mode: Mode = Mode.CIRCLE

    var onStart: (() -> Unit)? = null
    var onSelected: ((RectF) -> Unit)? = null

    private enum class State { NONE, DRAW, MOVE, RESIZE, TRANSFORM, IGNORE }

    private var bitmap: Bitmap? = null
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private var fitScale = 1f
    private var needFit = true

    // Selected rectangle in bitmap coordinates
    private var box: RectF? = null
    private val boxAtDown = RectF()
    private var hadBoxAtDown = false

    // Circle mode freehand drawing path (in view coordinates)
    private val circlePath = Path()
    private var circleMinX = 0f
    private var circleMinY = 0f
    private var circleMaxX = 0f
    private var circleMaxY = 0f
    private var hasCircle = false

    private var state = State.NONE
    private var downX = 0f
    private var downY = 0f
    private var resizeFlags = 0

    private var lastFx = 0f
    private var lastFy = 0f
    private var lastSpan = -1f

    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    private val density = context.resources.displayMetrics.density
    private val slop = 30f * density
    private val minBoxView = 24f * density

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bgPaint = Paint().apply { color = Color.parseColor("#FF0E0E10") }
    private val dimLight = Paint().apply { color = Color.argb(70, 0, 0, 0) }
    private val dimStrong = Paint().apply { color = Color.argb(150, 0, 0, 0) }

    // Google Neon Glow for Circle to Search
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 11f * density
        color = Color.argb(90, 138, 180, 248)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.parseColor("#8AB4F8")
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#8AB4F8")
    }

    private val tmp = RectF()

    companion object {
        private const val L = 1
        private const val T = 2
        private const val R = 4
        private const val B = 8
    }

    fun setBitmap(b: Bitmap?) {
        bitmap = b
        clearSelection()
        state = State.NONE
        needFit = true
        fitIfPossible()
        invalidate()
    }

    fun hasSelection(): Boolean = box != null

    fun clearSelection() {
        box = null
        circlePath.reset()
        hasCircle = false
        invalidate()
    }

    fun selectAll() {
        val bmp = bitmap ?: return
        box = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        circlePath.reset()
        hasCircle = false
        invalidate()
        onSelected?.invoke(RectF(box))
    }

    fun resetView() {
        needFit = true
        fitIfPossible()
        invalidate()
    }

    fun cropFrom(src: Bitmap, r: RectF): Bitmap? {
        val left = r.left.toInt().coerceIn(0, src.width - 1)
        val top = r.top.toInt().coerceIn(0, src.height - 1)
        val right = r.right.toInt().coerceIn(left + 1, src.width)
        val bottom = r.bottom.toInt().coerceIn(top + 1, src.height)
        return try {
            Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        } catch (e: Exception) {
            null
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (needFit) fitIfPossible()
    }

    private fun fitIfPossible() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        fitScale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        scale = fitScale
        tx = (width - bmp.width * scale) / 2f
        ty = (height - bmp.height * scale) / 2f
        needFit = false
    }

    private fun toBmpX(x: Float) = (x - tx) / scale
    private fun toBmpY(y: Float) = (y - ty) / scale

    private fun toView(r: RectF, out: RectF) {
        out.set(r.left * scale + tx, r.top * scale + ty, r.right * scale + tx, r.bottom * scale + ty)
    }

    private fun clampTranslation() {
        val bmp = bitmap ?: return
        val w = bmp.width * scale
        val h = bmp.height * scale
        tx = tx.coerceIn(width * 0.25f - w, width * 0.75f)
        ty = ty.coerceIn(height * 0.25f - h, height * 0.75f)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (bmp == null) return

        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bmp, 0f, 0f, bmpPaint)
        canvas.restore()

        val b = box
        if (b != null) {
            toView(b, tmp)
            canvas.save()
            canvas.clipOutRect(tmp)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimStrong)
            canvas.restore()
            canvas.drawRoundRect(tmp, 6f * density, 6f * density, border)
            drawHandles(canvas, tmp)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimLight)
        }

        if (mode == Mode.CIRCLE && !circlePath.isEmpty) {
            canvas.drawPath(circlePath, glow)
            canvas.drawPath(circlePath, line)
        }
    }

    private fun drawHandles(canvas: Canvas, r: RectF) {
        val rad = 7f * density
        val cx = r.centerX()
        val cy = r.centerY()
        val pts = floatArrayOf(
            r.left, r.top, r.right, r.top, r.left, r.bottom, r.right, r.bottom,
            cx, r.top, cx, r.bottom, r.left, cy, r.right, cy
        )
        var i = 0
        while (i < pts.size) {
            val small = i >= 8
            val rr = if (small) rad * 0.7f else rad
            canvas.drawCircle(pts[i], pts[i + 1], rr, handleFill)
            canvas.drawCircle(pts[i], pts[i + 1], rr, handleRing)
            i += 2
        }
    }

    private fun hitFlags(x: Float, y: Float): Int {
        val b = box ?: return 0
        toView(b, tmp)
        val handleRadius = 26f * density // Generous 52dp touch target
        val cx = tmp.centerX()
        val cy = tmp.centerY()

        if (hypot(x - tmp.left, y - tmp.top) <= handleRadius) return L or T
        if (hypot(x - cx, y - tmp.top) <= handleRadius) return T
        if (hypot(x - tmp.right, y - tmp.top) <= handleRadius) return R or T
        if (hypot(x - tmp.left, y - cy) <= handleRadius) return L
        if (hypot(x - tmp.right, y - cy) <= handleRadius) return R
        if (hypot(x - tmp.left, y - tmp.bottom) <= handleRadius) return L or B
        if (hypot(x - cx, y - tmp.bottom) <= handleRadius) return B
        if (hypot(x - tmp.right, y - tmp.bottom) <= handleRadius) return R or B

        val inY = y >= tmp.top - slop && y <= tmp.bottom + slop
        val inX = x >= tmp.left - slop && x <= tmp.right + slop
        var f = 0
        if (inY && abs(x - tmp.left) <= slop) f = f or L
        if (inY && abs(x - tmp.right) <= slop) f = f or R
        if (inX && abs(y - tmp.top) <= slop) f = f or T
        if (inX && abs(y - tmp.bottom) <= slop) f = f or B
        if ((f and L) != 0 && (f and R) != 0) f = f and (if (abs(x - tmp.left) < abs(x - tmp.right)) R.inv() else L.inv())
        if ((f and T) != 0 && (f and B) != 0) f = f and (if (abs(y - tmp.top) < abs(y - tmp.bottom)) B.inv() else T.inv())
        return f
    }

    private fun insideBox(x: Float, y: Float): Boolean {
        val b = box ?: return false
        toView(b, tmp)
        return tmp.contains(x, y)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val bmp = bitmap ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                hadBoxAtDown = box != null
                box?.let { boxAtDown.set(it) }
                onStart?.invoke()

                if (box != null) {
                    val flags = hitFlags(e.x, e.y)
                    when {
                        flags != 0 -> {
                            state = State.RESIZE
                            resizeFlags = flags
                            circlePath.reset()
                        }
                        insideBox(e.x, e.y) -> {
                            state = State.MOVE
                            circlePath.reset()
                        }
                        else -> startNewSelection(e.x, e.y, bmp)
                    }
                } else {
                    startNewSelection(e.x, e.y, bmp)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (state == State.DRAW || state == State.MOVE || state == State.RESIZE) {
                    box = if (hadBoxAtDown) RectF(boxAtDown) else null
                    circlePath.reset()
                }
                state = State.TRANSFORM
                lastSpan = -1f
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                when (state) {
                    State.TRANSFORM -> if (e.pointerCount >= 2) transform(e)
                    State.DRAW -> {
                        if (mode == Mode.CIRCLE) {
                            circlePath.lineTo(e.x, e.y)
                            circleMinX = min(circleMinX, e.x); circleMaxX = max(circleMaxX, e.x)
                            circleMinY = min(circleMinY, e.y); circleMaxY = max(circleMaxY, e.y)
                            invalidate()
                        } else {
                            dragBox(e.x, e.y, bmp)
                        }
                    }
                    State.MOVE, State.RESIZE -> dragBox(e.x, e.y, bmp)
                    else -> {}
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (state == State.TRANSFORM) {
                    lastSpan = -1f
                    if (e.pointerCount - 1 < 2) state = State.IGNORE
                }
            }
            MotionEvent.ACTION_UP -> {
                val s = state
                state = State.NONE
                if (s == State.DRAW) {
                    if (mode == Mode.CIRCLE) finishCircle(bmp) else finishBox(e, s)
                } else if (s == State.MOVE || s == State.RESIZE) {
                    finishBox(e, s)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (state == State.DRAW || state == State.MOVE || state == State.RESIZE) {
                    box = if (hadBoxAtDown) RectF(boxAtDown) else null
                    circlePath.reset()
                }
                state = State.NONE
                invalidate()
            }
        }
        return true
    }

    private fun startNewSelection(x: Float, y: Float, bmp: Bitmap) {
        state = State.DRAW
        if (mode == Mode.CIRCLE) {
            circlePath.reset()
            circlePath.moveTo(x, y)
            circleMinX = x; circleMaxX = x
            circleMinY = y; circleMaxY = y
            box = null
        } else {
            val bx = toBmpX(x).coerceIn(0f, bmp.width.toFloat())
            val by = toBmpY(y).coerceIn(0f, bmp.height.toFloat())
            box = RectF(bx, by, bx, by)
        }
    }

    private fun finishCircle(bmp: Bitmap) {
        val pad = 12f * density
        val vx1 = max(0f, circleMinX - pad)
        val vy1 = max(0f, circleMinY - pad)
        val vx2 = min(width.toFloat(), circleMaxX + pad)
        val vy2 = min(height.toFloat(), circleMaxY + pad)
        if ((vx2 - vx1) < minBoxView && (vy2 - vy1) < minBoxView) {
            circlePath.reset()
            box = if (hadBoxAtDown) RectF(boxAtDown) else null
            invalidate()
            return
        }
        val bx1 = toBmpX(vx1).coerceIn(0f, bmp.width.toFloat())
        val by1 = toBmpY(vy1).coerceIn(0f, bmp.height.toFloat())
        val bx2 = toBmpX(vx2).coerceIn(0f, bmp.width.toFloat())
        val by2 = toBmpY(vy2).coerceIn(0f, bmp.height.toFloat())
        val res = RectF(min(bx1, bx2), min(by1, by2), max(bx1, bx2), max(by1, by2))
        box = res
        circlePath.reset()
        invalidate()
        onSelected?.invoke(RectF(res))
    }

    private fun dragBox(x: Float, y: Float, bmp: Bitmap) {
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val cur = box ?: return
        when (state) {
            State.DRAW -> {
                val ax = toBmpX(downX).coerceIn(0f, bw)
                val ay = toBmpY(downY).coerceIn(0f, bh)
                val bx = toBmpX(x).coerceIn(0f, bw)
                val by = toBmpY(y).coerceIn(0f, bh)
                cur.set(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by))
            }
            State.MOVE -> {
                val dx = (x - downX) / scale
                val dy = (y - downY) / scale
                val clampedDx = dx.coerceIn(-boxAtDown.left, bw - boxAtDown.right)
                val clampedDy = dy.coerceIn(-boxAtDown.top, bh - boxAtDown.bottom)
                cur.set(boxAtDown.left + clampedDx, boxAtDown.top + clampedDy, boxAtDown.right + clampedDx, boxAtDown.bottom + clampedDy)
            }
            State.RESIZE -> {
                val dx = (x - downX) / scale
                val dy = (y - downY) / scale
                var l = boxAtDown.left
                var t = boxAtDown.top
                var r = boxAtDown.right
                var b = boxAtDown.bottom
                if ((resizeFlags and L) != 0) l += dx
                if ((resizeFlags and R) != 0) r += dx
                if ((resizeFlags and T) != 0) t += dy
                if ((resizeFlags and B) != 0) b += dy
                cur.set(
                    min(l, r).coerceIn(0f, bw), min(t, b).coerceIn(0f, bh),
                    max(l, r).coerceIn(0f, bw), max(t, b).coerceIn(0f, bh)
                )
            }
            else -> {}
        }
        invalidate()
    }

    private fun finishBox(e: MotionEvent, s: State) {
        val b = box
        val movedFar = hypot(e.x - downX, e.y - downY) > 12f * density
        if (!movedFar && s != State.RESIZE) {
            box = if (hadBoxAtDown) RectF(boxAtDown) else null
            handleTap(e.x, e.y)
            invalidate()
            return
        }
        if (b == null) return
        toView(b, tmp)
        if (tmp.width() < minBoxView || tmp.height() < minBoxView) {
            box = if (hadBoxAtDown) RectF(boxAtDown) else null
            invalidate()
            return
        }
        invalidate()
        onSelected?.invoke(RectF(b))
    }

    private fun handleTap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val isDouble = now - lastTapTime < 320 && hypot(x - lastTapX, y - lastTapY) < 48f * density
        lastTapTime = now
        lastTapX = x
        lastTapY = y
        if (!isDouble) return
        lastTapTime = 0L
        val target = if (scale > fitScale * 1.3f) fitScale else fitScale * 2.5f
        zoomAbout(x, y, target / scale)
    }

    private fun transform(e: MotionEvent) {
        val x0 = e.getX(0)
        val y0 = e.getY(0)
        val x1 = e.getX(1)
        val y1 = e.getY(1)
        val fx = (x0 + x1) / 2f
        val fy = (y0 + y1) / 2f
        val span = hypot(x1 - x0, y1 - y0)
        if (lastSpan > 0f) {
            tx += fx - lastFx
            ty += fy - lastFy
            if (span > 0f) zoomAbout(fx, fy, span / lastSpan, redraw = false)
            clampTranslation()
            invalidate()
        }
        lastFx = fx
        lastFy = fy
        lastSpan = span
    }

    private fun zoomAbout(fx: Float, fy: Float, factor: Float, redraw: Boolean = true) {
        val newScale = (scale * factor).coerceIn(fitScale * 0.6f, fitScale * 12f)
        val f = newScale / scale
        tx = fx - (fx - tx) * f
        ty = fy - (fy - ty) * f
        scale = newScale
        clampTranslation()
        if (redraw) invalidate()
    }
}

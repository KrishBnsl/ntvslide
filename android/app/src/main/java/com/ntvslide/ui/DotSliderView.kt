package com.ntvslide.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.floor
import kotlin.math.roundToInt

/** Vertical pill slider drawn as a dot matrix: lit dots are volume, dim dots are headroom. */
class DotSliderView(context: Context) : View(context) {

    var level = 0
        set(value) { field = value.coerceIn(0, 100); invalidate() }
    var muted = false
        set(value) { field = value; invalidate() }
    var onLevelChanged: ((Int) -> Unit)? = null

    private val pitch = NothingTheme.dp(context, 8f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pill = Path()
    private val rect = RectF()
    private var lastHaptic = -1

    override fun onMeasure(w: Int, h: Int) {
        // 7 dots wide, 28 dots tall — identical proportions to the web preview
        setMeasuredDimension((7 * pitch).toInt() + 2, (28 * pitch).toInt() + 2)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(.5f, .5f, w - .5f, h - .5f)
        pill.reset()
        pill.addRoundRect(rect, w / 2, w / 2, Path.Direction.CW)

        paint.style = Paint.Style.FILL
        paint.color = NothingTheme.SURFACE
        canvas.drawPath(pill, paint)

        canvas.save()
        canvas.clipPath(pill)
        val cols = floor(w / pitch).toInt()
        val rows = floor(h / pitch).toInt()
        val ox = (w - cols * pitch) / 2
        val oy = (h - rows * pitch) / 2
        val lit = (level / 100f * rows).roundToInt()
        for (r in 0 until rows) {
            val cy = h - oy - (r + .5f) * pitch
            val on = r < lit
            val edge = r == lit - 1 && !muted
            paint.color = when {
                !on -> NothingTheme.DOT_OFF
                muted -> (NothingTheme.RED and 0x00FFFFFF) or (0x73 shl 24)
                else -> NothingTheme.DOT_ON
            }
            val radius = pitch * if (edge) .40f else if (on) .30f else .24f
            for (c in 0 until cols) canvas.drawCircle(ox + (c + .5f) * pitch, cy, radius, paint)
        }
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = NothingTheme.LINE
        canvas.drawPath(pill, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE -> {}
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); return true }
            else -> return false
        }
        change(((1f - e.y / height) * 100).roundToInt())
        return true
    }

    private fun change(v: Int) {
        val n = v.coerceIn(0, 100)
        if (n == level && !muted) return
        level = n
        onLevelChanged?.invoke(n)
        if (n % 10 == 0 && n != lastHaptic) {
            lastHaptic = n
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // TalkBack: swipe up/down to adjust in steps of 5
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, 100f, level.toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, args: android.os.Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { change(level + 5); true }
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { change(level - 5); true }
        else -> super.performAccessibilityAction(action, args)
    }
}

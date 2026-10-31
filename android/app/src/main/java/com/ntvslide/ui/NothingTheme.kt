package com.ntvslide.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View

/** The NothingTheme palette: black, off-white, one red dot. */
object NothingTheme {
    const val PANEL = 0xFF101010.toInt()
    const val SURFACE = 0xFF0A0A0A.toInt()
    const val LINE = 0xFF262626.toInt()
    const val DOT_OFF = 0xFF2F2F2F.toInt()
    const val DOT_ON = 0xFFF5F5F5.toInt()
    const val MUTED_TEXT = 0xFF8A8A8A.toInt()
    const val RED = 0xFFD71921.toInt()

    // 3×5 dot-matrix glyphs, rows concatenated (same table as the web preview)
    private val FONT = mapOf(
        '0' to "111101101101111", '1' to "010110010010111", '2' to "111001111100111",
        '3' to "111001111001111", '4' to "101101111001001", '5' to "111100111001111",
        '6' to "111100111101111", '7' to "111001001001001", '8' to "111101111101111",
        '9' to "111101111001111", 'M' to "101111111101101", 'U' to "101101101101111",
        'T' to "111010010010010", 'E' to "111100111100111"
    )

    fun glyph(c: Char): String = FONT[c] ?: "000000000000000"

    fun dp(context: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)
}

/** Renders short text (digits, "MUTE") as a dot matrix. */
class DotTextView(context: Context) : View(context) {
    private val pitch = NothingTheme.dp(context, 3f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var text: String = ""
        set(value) { if (field != value) { field = value; requestLayout(); invalidate() } }
    var color: Int = NothingTheme.DOT_ON
        set(value) { field = value; invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        val cols = (text.length * 4 - 1).coerceAtLeast(0)
        setMeasuredDimension((cols * pitch).toInt(), (5 * pitch).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = color
        text.forEachIndexed { i, ch ->
            val g = NothingTheme.glyph(ch)
            for (y in 0 until 5) for (x in 0 until 3) {
                if (g[y * 3 + x] == '1') canvas.drawCircle((i * 4 + x + .5f) * pitch, (y + .5f) * pitch, pitch * .36f, paint)
            }
        }
    }
}

package com.ntvslide.ui

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ntvslide.AppEntry
import com.ntvslide.AppVolumeController

/**
 * The ntvslide panel: one NothingTheme-style column per app. It is shaped to sit directly left of the
 * system volume panel, so the two read as one control.
 */
class PanelView(context: Context) : FrameLayout(context) {

    /** Called on every touch, so the host can keep the panel on screen while it is being used. */
    var onInteraction: (() -> Unit)? = null

    private val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(strip)
    }

    init {
        val d = { v: Float -> NothingTheme.dp(context, v) }
        background = GradientDrawable().apply {
            setColor(NothingTheme.PANEL)
            cornerRadius = d(36f)
            setStroke(1, NothingTheme.LINE)
        }
        val pad = d(14f).toInt()
        setPadding(pad, pad, pad, pad)
        addView(scroller)
    }

    fun bind(entries: List<AppEntry>, controller: AppVolumeController) {
        strip.removeAllViews()
        if (entries.isEmpty()) {
            strip.addView(TextView(context).apply {
                text = "NO APPS\nPLAYING"
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                letterSpacing = .12f
                setTextColor(NothingTheme.MUTED_TEXT)
                val p = NothingTheme.dp(context, 12f).toInt()
                setPadding(p, NothingTheme.dp(context, 90f).toInt(), p, NothingTheme.dp(context, 90f).toInt())
            })
            return
        }
        entries.forEach { strip.addView(column(it, controller)) }
    }

    private fun column(entry: AppEntry, controller: AppVolumeController): View {
        val store = controller.store
        val d = { v: Float -> NothingTheme.dp(context, v).toInt() }
        val readout = DotTextView(context)
        val slider = DotSliderView(context)
        val icon = ImageView(context)
        val label = TextView(context)

        fun refresh() {
            val muted = store.muted(entry.pkg)
            slider.level = store.level(entry.pkg)
            slider.muted = muted
            readout.text = if (muted) "MUTE" else store.level(entry.pkg).toString()
            readout.color = if (muted) NothingTheme.RED else NothingTheme.DOT_ON
            label.setTextColor(if (muted) NothingTheme.RED else NothingTheme.MUTED_TEXT)
            icon.alpha = if (muted) .4f else 1f
            icon.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(NothingTheme.SURFACE)
                setStroke(1, if (muted) NothingTheme.RED else NothingTheme.LINE)
            }
        }

        slider.onLevelChanged = {
            store.setLevel(entry.pkg, it)
            controller.apply(entry)
            refresh()
        }

        icon.apply {
            setImageDrawable(entry.icon)
            // monochrome app icons: the NothingTheme look, and it stops the panel fighting the wallpaper
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(d(10f), d(10f), d(10f), d(10f))
            clipToOutline = true
            contentDescription = "Mute ${entry.label}"
            setOnClickListener {
                store.toggleMute(entry.pkg)
                controller.apply(entry)
                refresh()
            }
        }

        label.apply {
            text = entry.label.uppercase()
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            letterSpacing = .12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        slider.contentDescription = "${entry.label} volume"

        refresh()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(d(56f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = d(12f) }
            addView(readout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = d(10f) })
            addView(slider)
            addView(icon, LinearLayout.LayoutParams(d(48f), d(48f)).apply { topMargin = d(10f) })
            addView(label, LinearLayout.LayoutParams(d(56f), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = d(10f) })
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        onInteraction?.invoke()
        return super.dispatchTouchEvent(ev)
    }
}

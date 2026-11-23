package com.taplink.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import com.taplink.app.domain.Category
import com.taplink.app.domain.Money
import com.taplink.app.domain.SpendingSummary
import kotlin.math.min

/**
 * Real Android custom View, embedded with AndroidView. Only debit aggregates are accepted.
 *
 * The View draws with the framework Canvas, so it cannot read Compose string resources; the
 * hosting composable hands every visible word over in [setCopy] and this class only paints them.
 */
class SpendingChartView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var summary = SpendingSummary(0, emptyMap())
    private var foreground = android.graphics.Color.rgb(20, 44, 69)
    private var muted = android.graphics.Color.rgb(83, 101, 120)
    private val palette = intArrayOf(0xFF1266D6.toInt(), 0xFF008C91.toInt(), 0xFF8B6DD1.toInt(), 0xFFF2AB54.toInt(), 0xFF6E94AB.toInt())

    private var monthLabel = ""
    private var emptyLabel = ""
    private var noteLabel = ""
    private var categoryLabels: Map<Category, String> = emptyMap()

    /** Wording supplied by the caller, already localized. */
    fun setCopy(month: String, empty: String, note: String, categories: Map<Category, String>, description: String) {
        monthLabel = month; emptyLabel = empty; noteLabel = note; categoryLabels = categories
        contentDescription = description
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        invalidate()
    }

    fun setData(value: SpendingSummary, textColor: Int, secondaryColor: Int) {
        summary = value; foreground = textColor; muted = secondaryColor
        invalidate()
    }
    /** Shrinks the paint until [text] fits [maxWidth], down to a readable floor. */
    private fun fitTextSize(paint: Paint, text: String, maxWidth: Float, preferred: Float): Float {
        var size = preferred
        paint.textSize = size
        val floor = 9 * resources.displayMetrics.density
        while (size > floor && paint.measureText(text) > maxWidth) {
            size = (size - 1 * resources.displayMetrics.density).coerceAtLeast(floor)
            paint.textSize = size
        }
        return size
    }

    /** Last resort for a word that is still too wide at the floor size. */
    private fun fitText(paint: Paint, text: String, maxWidth: Float): CharSequence =
        if (paint.measureText(text) <= maxWidth) text
        else TextUtils.ellipsize(text, TextPaint(paint), maxWidth, TextUtils.TruncateAt.END)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val diameter = min(width * .52f, 180 * density)
        val cx = width / 2f
        val cy = diameter / 2 + 8 * density
        val stroke = 20 * density
        bounds.set(cx - diameter / 2 + stroke, cy - diameter / 2 + stroke, cx + diameter / 2 - stroke, cy + diameter / 2 - stroke)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = stroke; paint.strokeCap = Paint.Cap.BUTT
        paint.color = 0xFFE0E8F0.toInt(); canvas.drawArc(bounds, 0f, 360f, false, paint)
        var angle = -90f
        summary.byCategory.filterValues { it > 0 }.forEach { (category, amount) ->
            val sweep = if (summary.totalCents == 0L) 0f else (amount.toDouble() / summary.totalCents * 360).toFloat()
            paint.color = palette[category.ordinal]; canvas.drawArc(bounds, angle, sweep, false, paint); angle += sweep
        }
        // Both centre lines are fitted to the inside of the ring instead of assuming a length:
        // "This month" is wider than 本月, and a longer translation or a large system font must not
        // spill over the stroke.
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        val inner = diameter - 2 * stroke
        val maxTextWidth = inner * 0.66f
        val headline = if (summary.totalCents == 0L) emptyLabel else monthLabel
        // A bold headline inside a thin ring is the readable pairing: at a glance the month is the
        // value the user came for, so it carries the weight, and the small note stays medium so the
        // two lines are not the same voice.
        val bold = Typeface.create("sans-serif", Typeface.BOLD)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.color = foreground; paint.isFakeBoldText = false; paint.typeface = bold
        paint.textSize = 15 * density
        paint.letterSpacing = 0.02f
        val headlineSize = fitTextSize(paint, headline, maxTextWidth, 15 * density)
        paint.letterSpacing = 0f
        paint.typeface = medium
        paint.textSize = 10 * density
        val noteSize = fitTextSize(paint, noteLabel, maxTextWidth, 10 * density)
        val gap = 6 * density
        val block = headlineSize + gap + noteSize
        val top = cy - block / 2
        paint.typeface = bold; paint.textSize = headlineSize; paint.color = foreground
        paint.letterSpacing = 0.02f
        canvas.drawText(fitText(paint, headline, maxTextWidth).toString(), cx, top + headlineSize, paint)
        paint.letterSpacing = 0f
        paint.typeface = medium; paint.textSize = noteSize; paint.color = muted
        canvas.drawText(fitText(paint, noteLabel, maxTextWidth).toString(), cx, top + headlineSize + gap + noteSize, paint)
        paint.typeface = Typeface.DEFAULT
        var y = diameter + 30 * density
        paint.textAlign = Paint.Align.LEFT; paint.textSize = 14 * density
        Category.entries.filter { (summary.byCategory[it] ?: 0L) > 0 }.forEach { category ->
            paint.color = palette[category.ordinal]; canvas.drawCircle(8 * density, y - 5 * density, 5 * density, paint)
            paint.color = foreground; canvas.drawText(categoryLabels[category] ?: category.name, 24 * density, y, paint)
            paint.textAlign = Paint.Align.RIGHT; canvas.drawText(Money.format(summary.byCategory[category] ?: 0), width.toFloat(), y, paint)
            paint.textAlign = Paint.Align.LEFT; y += 30 * density
        }
    }
}

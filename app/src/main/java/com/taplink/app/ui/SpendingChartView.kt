package com.taplink.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.taplink.app.domain.Category
import com.taplink.app.domain.Money
import com.taplink.app.domain.SpendingSummary
import kotlin.math.min

/** Real Android custom View, embedded with AndroidView. Only debit aggregates are accepted. */
class SpendingChartView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var summary = SpendingSummary(0, emptyMap())
    private var foreground = android.graphics.Color.rgb(20, 44, 69)
    private var muted = android.graphics.Color.rgb(83, 101, 120)
    private val palette = intArrayOf(0xFF1266D6.toInt(), 0xFF008C91.toInt(), 0xFF8B6DD1.toInt(), 0xFFF2AB54.toInt(), 0xFF6E94AB.toInt())
    fun setData(value: SpendingSummary, textColor: Int, secondaryColor: Int) {
        summary = value; foreground = textColor; muted = secondaryColor
        contentDescription = "Spending chart. ${Money.format(value.totalCents)} total. " + value.byCategory.entries.joinToString { "${it.key.label()} ${Money.format(it.value)}" } + ". Credits and top-ups excluded."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val diameter = min(width * .48f, 172 * density)
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
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.color = foreground; paint.textSize = 20 * density; paint.isFakeBoldText = true
        canvas.drawText(if (summary.totalCents == 0L) "No spending" else "This month", cx, cy + 3 * density, paint)
        paint.isFakeBoldText = false
        paint.textSize = 12 * density; paint.color = muted
        canvas.drawText("Debits only", cx, cy + 24 * density, paint)
        var y = diameter + 30 * density
        paint.textAlign = Paint.Align.LEFT; paint.textSize = 14 * density
        Category.entries.filter { (summary.byCategory[it] ?: 0L) > 0 }.forEach { category ->
            paint.color = palette[category.ordinal]; canvas.drawCircle(8 * density, y - 5 * density, 5 * density, paint)
            paint.color = foreground; canvas.drawText(category.label(), 24 * density, y, paint)
            paint.textAlign = Paint.Align.RIGHT; canvas.drawText(Money.format(summary.byCategory[category] ?: 0), width.toFloat(), y, paint)
            paint.textAlign = Paint.Align.LEFT; y += 30 * density
        }
    }
}

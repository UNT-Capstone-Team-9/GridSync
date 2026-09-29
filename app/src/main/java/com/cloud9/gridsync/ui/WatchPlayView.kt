package com.cloud9.gridsync.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.cloud9.gridsync.network.PointData
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Lightweight, read-only route renderer for full-Android watches. */
class WatchPlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var role: String = ""
    private var points: List<PointData> = emptyList()
    private val board = RectF()

    private val fieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 46, 28) }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(125, 255, 255, 255)
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = dp(4f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 136)
        style = Paint.Style.FILL
    }
    private val rolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 136)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun showRoute(role: String, route: List<PointData>) {
        this.role = role.trim()
        this.points = route.map { PointData(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
        visibility = if (points.size >= 2) VISIBLE else GONE
        invalidate()
    }

    fun clearRoute() {
        role = ""
        points = emptyList()
        visibility = GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val pad = dp(8f)
        board.set(pad, pad, width - pad, height - pad)
        canvas.drawRoundRect(board, dp(10f), dp(10f), fieldPaint)

        // Simple field reference lines; the route itself uses normalized coordinates.
        for (i in 1..5) {
            val y = board.top + board.height() * i / 6f
            canvas.drawLine(board.left, y, board.right, y, linePaint)
        }

        val path = Path()
        var prevX = 0f
        var prevY = 0f
        var lastX = 0f
        var lastY = 0f

        points.forEachIndexed { index, point ->
            val x = board.left + point.x * board.width()
            val y = board.top + point.y * board.height()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            if (index == points.lastIndex - 1) { prevX = x; prevY = y }
            if (index == points.lastIndex) { lastX = x; lastY = y }
        }

        canvas.drawPath(path, routePaint)
        val first = points.first()
        val firstX = board.left + first.x * board.width()
        val firstY = board.top + first.y * board.height()
        canvas.drawCircle(firstX, firstY, dp(5f), startPaint)
        drawArrow(canvas, prevX, prevY, lastX, lastY)

        if (role.isNotBlank()) {
            rolePaint.textSize = min(board.width(), board.height()) * 0.09f
            canvas.drawText(role, board.centerX(), board.bottom - dp(10f), rolePaint)
        }
    }

    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val length = dp(13f)
        val spread = Math.toRadians(28.0)
        val ax1 = x2 - (length * cos(angle - spread)).toFloat()
        val ay1 = y2 - (length * sin(angle - spread)).toFloat()
        val ax2 = x2 - (length * cos(angle + spread)).toFloat()
        val ay2 = y2 - (length * sin(angle + spread)).toFloat()
        canvas.drawLine(x2, y2, ax1, ay1, routePaint)
        canvas.drawLine(x2, y2, ax2, ay2, routePaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

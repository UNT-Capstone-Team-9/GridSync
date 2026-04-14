package com.cloud9.gridsync.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.cloud9.gridsync.network.PointData
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class WatchRouteView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val boardRect = RectF()
    private val contentRect = RectF()

    private var currentRole: String = "Unassigned"
    private var movements: Map<String, List<PointData>> = emptyMap()

    private val boardFillPaint = Paint().apply {
        color = Color.rgb(245, 245, 240)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val boardBorderPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = dp(1.2f)
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.argb(210, 40, 40, 40)
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val dashPaint = Paint().apply {
        color = Color.argb(140, 90, 90, 90)
        strokeWidth = dp(0.9f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(4f)), 0f)
        isAntiAlias = true
    }

    private val currentRoutePaint = Paint().apply {
        color = Color.rgb(0, 220, 90)
        strokeWidth = dp(4.8f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val otherRoutePaint = Paint().apply {
        color = Color.argb(180, 0, 190, 80)
        strokeWidth = dp(4f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val startPointPaint = Paint().apply {
        color = Color.rgb(0, 160, 60)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val yardNumberPaint = Paint().apply {
        color = Color.rgb(0, 220, 90)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        textSize = dp(15f)
        isFakeBoldText = true
    }

    private val roleLabelPaint = Paint().apply {
        color = Color.rgb(0, 255, 110)
        textSize = dp(14f)
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val roleLabelBackgroundPaint = Paint().apply {
        color = Color.argb(205, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun setRole(role: String) {
        currentRole = role.trim()
        invalidate()
    }

    fun setMovements(newMovements: Map<String, List<PointData>>) {
        movements = newMovements
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBoard(canvas)
        drawRoutes(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        val outerPad = dp(6f)

        boardRect.set(
            outerPad,
            outerPad,
            width - outerPad,
            height - outerPad
        )

        val contentPadX = dp(22f)
        val contentPadY = dp(10f)

        contentRect.set(
            boardRect.left + contentPadX,
            boardRect.top + contentPadY,
            boardRect.right - contentPadX,
            boardRect.bottom - contentPadY
        )

        canvas.drawRect(boardRect, boardFillPaint)
        canvas.drawRect(boardRect, boardBorderPaint)

        val horizontalLines = 7
        val gapY = contentRect.height() / (horizontalLines - 1)

        repeat(horizontalLines) { i ->
            val y = contentRect.top + i * gapY
            canvas.drawLine(contentRect.left, y, contentRect.right, y, linePaint)
        }

        val guide1X = contentRect.left + contentRect.width() * 0.33f
        val guide2X = contentRect.left + contentRect.width() * 0.66f

        canvas.drawLine(guide1X, contentRect.top, guide1X, contentRect.bottom, dashPaint)
        canvas.drawLine(guide2X, contentRect.top, guide2X, contentRect.bottom, dashPaint)

        drawVerticalText(canvas, "30", boardRect.left + dp(12f), contentRect.top + gapY)
        drawVerticalText(canvas, "20", boardRect.left + dp(12f), contentRect.top + gapY * 3f)
        drawVerticalText(canvas, "10", boardRect.left + dp(12f), contentRect.top + gapY * 5f)

        drawVerticalText(canvas, "30", boardRect.right - dp(12f), contentRect.top + gapY)
        drawVerticalText(canvas, "20", boardRect.right - dp(12f), contentRect.top + gapY * 3f)
        drawVerticalText(canvas, "0", boardRect.right - dp(12f), contentRect.top + gapY * 5f)
    }

    private fun drawVerticalText(canvas: Canvas, text: String, cx: Float, cy: Float) {
        canvas.save()
        canvas.rotate(-90f, cx, cy)
        val baselineAdjust = (yardNumberPaint.descent() + yardNumberPaint.ascent()) / 2f
        canvas.drawText(text, cx, cy - baselineAdjust, yardNumberPaint)
        canvas.restore()
    }

    private fun drawRoutes(canvas: Canvas) {
        if (movements.isEmpty()) return

        movements.forEach { entry ->
            val role = entry.key
            val points = entry.value
            if (points.size < 2) return@forEach

            val path = Path()
            var firstX = 0f
            var firstY = 0f
            var lastX = 0f
            var lastY = 0f
            var prevX = 0f
            var prevY = 0f

            points.forEachIndexed { index, point ->
                val scaledX = contentRect.left + point.x.coerceIn(0f, 1f) * contentRect.width()
                val scaledY = contentRect.top + point.y.coerceIn(0f, 1f) * contentRect.height()

                if (index == 0) {
                    firstX = scaledX
                    firstY = scaledY
                    path.moveTo(scaledX, scaledY)
                } else {
                    path.lineTo(scaledX, scaledY)
                }

                if (index == points.lastIndex - 1) {
                    prevX = scaledX
                    prevY = scaledY
                }

                if (index == points.lastIndex) {
                    lastX = scaledX
                    lastY = scaledY
                }
            }

            val paint = if (role.equals(currentRole, ignoreCase = true)) {
                currentRoutePaint
            } else {
                otherRoutePaint
            }

            canvas.drawPath(path, paint)
            canvas.drawCircle(firstX, firstY, dp(4f), startPointPaint)
            drawArrowHead(canvas, prevX, prevY, lastX, lastY, paint)
            drawRoleLabel(canvas, role, firstX, firstY)
        }
    }

    private fun drawRoleLabel(canvas: Canvas, role: String, x: Float, y: Float) {
        val paddingX = dp(6f)
        val paddingY = dp(4f)
        val textWidth = roleLabelPaint.measureText(role)
        val textHeight = roleLabelPaint.textSize

        val left = x + dp(6f)
        val top = y - textHeight - dp(6f)
        val right = left + textWidth + paddingX * 2
        val bottom = top + textHeight + paddingY * 2

        canvas.drawRoundRect(
            RectF(left, top, right, bottom),
            dp(6f),
            dp(6f),
            roleLabelBackgroundPaint
        )

        canvas.drawText(
            role,
            left + paddingX,
            bottom - paddingY - roleLabelPaint.descent(),
            roleLabelPaint
        )
    }

    private fun drawArrowHead(
        canvas: Canvas,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        paint: Paint
    ) {
        val angle = atan2(toY - fromY, toX - fromX)
        val arrowLength = dp(14f)
        val arrowAngle = Math.toRadians(28.0).toFloat()

        val x1 = toX - arrowLength * cos(angle - arrowAngle)
        val y1 = toY - arrowLength * sin(angle - arrowAngle)
        val x2 = toX - arrowLength * cos(angle + arrowAngle)
        val y2 = toY - arrowLength * sin(angle + arrowAngle)

        canvas.drawLine(toX, toY, x1, y1, paint)
        canvas.drawLine(toX, toY, x2, y2, paint)
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
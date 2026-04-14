package com.cloud9.gridsync.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.cloud9.gridsync.network.PointData
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CoachDrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val pointsMap = mutableMapOf<String, MutableList<PointData>>()
    private var currentRole = "QB"

    private val boardRect = RectF()

    private val currentPathPaint = Paint().apply {
        color = Color.rgb(240, 190, 30)
        strokeWidth = dp(3.5f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val otherPathPaint = Paint().apply {
        color = Color.argb(150, 90, 90, 90)
        strokeWidth = dp(3f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val boardFillPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val solidLinePaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = dp(1.2f)
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val dashedGuidePaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(5f)), 0f)
        isAntiAlias = true
    }

    private val yardNumberPaint = Paint().apply {
        color = Color.DKGRAY
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val startPointPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val currentRoleLabelPaint = Paint().apply {
        color = Color.rgb(240, 190, 30)
        textSize = dp(17f)
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val otherRoleLabelPaint = Paint().apply {
        color = Color.rgb(55, 55, 55)
        textSize = dp(16f)
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val labelBackgroundPaint = Paint().apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun setRole(role: String) {
        currentRole = role.trim()
        invalidate()
    }

    fun getMovements(): Map<String, List<PointData>> {
        return pointsMap.mapValues { it.value.toList() }
    }

    fun setMovements(movements: Map<String, List<PointData>>) {
        pointsMap.clear()

        movements.forEach { entry ->
            val role = entry.key.trim()
            val normalizedPoints = entry.value.map { point ->
                PointData(
                    x = point.x.coerceIn(0f, 1f),
                    y = point.y.coerceIn(0f, 1f)
                )
            }

            if (normalizedPoints.isNotEmpty()) {
                pointsMap[role] = normalizedPoints.toMutableList()
            }
        }

        invalidate()
    }

    fun clearCurrentRole() {
        pointsMap.remove(currentRole)
        invalidate()
    }

    fun clearAll() {
        pointsMap.clear()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!boardRect.contains(event.x, event.y)) {
            return super.onTouchEvent(event)
        }

        val normalizedX = ((event.x - boardRect.left) / boardRect.width()).coerceIn(0f, 1f)
        val normalizedY = ((event.y - boardRect.top) / boardRect.height()).coerceIn(0f, 1f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val points = pointsMap.getOrPut(currentRole) { mutableListOf() }
                points.clear()
                points.add(PointData(normalizedX, normalizedY))
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                pointsMap[currentRole]?.add(PointData(normalizedX, normalizedY))
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                pointsMap[currentRole]?.add(PointData(normalizedX, normalizedY))
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCoachBoard(canvas)
        drawStoredPaths(canvas)
    }

    private fun drawCoachBoard(canvas: Canvas) {
        val outerPadding = dp(10f)
        val topPadding = dp(10f)
        val usableWidth = width - (outerPadding * 2)
        val usableHeight = height - (topPadding * 2)

        boardRect.set(
            outerPadding,
            topPadding,
            outerPadding + usableWidth,
            topPadding + usableHeight
        )

        canvas.drawRect(boardRect, boardFillPaint)
        canvas.drawRect(boardRect, solidLinePaint)

        val totalHorizontalLines = 7
        val bandHeight = boardRect.height() / (totalHorizontalLines - 1)

        val horizontalYs = ArrayList<Float>()
        repeat(totalHorizontalLines) { i ->
            val y = boardRect.top + i * bandHeight
            horizontalYs.add(y)
            canvas.drawLine(boardRect.left, y, boardRect.right, y, solidLinePaint)
        }

        val guide1X = boardRect.left + boardRect.width() * 0.33f
        val guide2X = boardRect.left + boardRect.width() * 0.66f

        canvas.drawLine(guide1X, boardRect.top, guide1X, boardRect.bottom, dashedGuidePaint)
        canvas.drawLine(guide2X, boardRect.top, guide2X, boardRect.bottom, dashedGuidePaint)

        drawSidelineTicks(canvas, boardRect.left, true)
        drawSidelineTicks(canvas, boardRect.right, false)

        val textSize = min(boardRect.width(), boardRect.height()) * 0.035f
        yardNumberPaint.textSize = textSize

        drawVerticalText(canvas, "30", boardRect.left + dp(18f), horizontalYs[1])
        drawVerticalText(canvas, "20", boardRect.left + dp(18f), horizontalYs[3])
        drawVerticalText(canvas, "10", boardRect.left + dp(18f), horizontalYs[5])

        drawVerticalText(canvas, "30", boardRect.right - dp(18f), horizontalYs[1])
        drawVerticalText(canvas, "20", boardRect.right - dp(18f), horizontalYs[3])
        drawVerticalText(canvas, "0", boardRect.right - dp(18f), horizontalYs[5])
    }

    private fun drawSidelineTicks(canvas: Canvas, edgeX: Float, isLeft: Boolean) {
        val totalTicks = 30
        val gap = boardRect.height() / totalTicks

        for (i in 0..totalTicks) {
            val y = boardRect.top + i * gap
            val tickLength = if (i % 5 == 0) dp(10f) else dp(6f)

            if (isLeft) {
                canvas.drawLine(edgeX, y, edgeX + tickLength, y, solidLinePaint)
            } else {
                canvas.drawLine(edgeX - tickLength, y, edgeX, y, solidLinePaint)
            }
        }
    }

    private fun drawVerticalText(canvas: Canvas, text: String, cx: Float, cy: Float) {
        canvas.save()
        canvas.rotate(-90f, cx, cy)
        val baselineAdjust = (yardNumberPaint.descent() + yardNumberPaint.ascent()) / 2f
        canvas.drawText(text, cx, cy - baselineAdjust, yardNumberPaint)
        canvas.restore()
    }

    private fun drawStoredPaths(canvas: Canvas) {
        pointsMap.forEach { entry ->
            val role = entry.key
            val points = entry.value
            if (points.size < 2) return@forEach

            val path = Path()
            var firstX = 0f
            var firstY = 0f
            var prevX = 0f
            var prevY = 0f
            var lastX = 0f
            var lastY = 0f

            points.forEachIndexed { index, point ->
                val x = boardRect.left + point.x.coerceIn(0f, 1f) * boardRect.width()
                val y = boardRect.top + point.y.coerceIn(0f, 1f) * boardRect.height()

                if (index == 0) {
                    firstX = x
                    firstY = y
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }

                if (index == points.lastIndex - 1) {
                    prevX = x
                    prevY = y
                }

                if (index == points.lastIndex) {
                    lastX = x
                    lastY = y
                }
            }

            val paint = if (role == currentRole) currentPathPaint else otherPathPaint
            canvas.drawPath(path, paint)
            canvas.drawCircle(firstX, firstY, dp(3.5f), startPointPaint)
            drawArrowHead(canvas, prevX, prevY, lastX, lastY, paint)
            drawRoleLabel(canvas, role, firstX, firstY, role == currentRole)
        }
    }

    private fun drawRoleLabel(
        canvas: Canvas,
        role: String,
        x: Float,
        y: Float,
        isCurrent: Boolean
    ) {
        val textPaint = if (isCurrent) currentRoleLabelPaint else otherRoleLabelPaint
        val paddingX = dp(8f)
        val paddingY = dp(5f)
        val textWidth = textPaint.measureText(role)
        val textHeight = textPaint.textSize

        val left = x + dp(8f)
        val top = y - textHeight - dp(8f)
        val right = left + textWidth + paddingX * 2
        val bottom = top + textHeight + paddingY * 2

        canvas.drawRoundRect(
            RectF(left, top, right, bottom),
            dp(7f),
            dp(7f),
            labelBackgroundPaint
        )

        canvas.drawText(
            role,
            left + paddingX,
            bottom - paddingY - textPaint.descent(),
            textPaint
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
        val arrowLength = dp(12f)
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
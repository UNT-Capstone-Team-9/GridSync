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
import com.cloud9.gridsync.network.PlayerPosition
import com.cloud9.gridsync.network.PointData
import com.cloud9.gridsync.network.RouteBranch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class WatchRouteView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val boardRect = RectF()
    private val contentRect = RectF()

    private var currentRole: String = "Unassigned"
    private var movements: Map<String, List<PointData>> = emptyMap()

    // Dashed option branches, keyed by role label like movements.
    private var options: Map<String, List<RouteBranch>> = emptyMap()

    // Normalized positions from the tablet. A full play (QB) has all 11 players, a role specific
    // play has only the wearer's own marker.
    private var players: List<PlayerPosition> = emptyList()
    private var isFullPlay = false

    private val playerFillPaint = Paint().apply {
        color = Color.rgb(20, 33, 61)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val ownPlayerFillPaint = Paint().apply {
        color = Color.rgb(0, 200, 80)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val playerLabelPaint = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

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

    // Option branches use the same colour and width as their main route, only dashed. The dash is
    // sized from the stroke so it stays readable on a small watch screen.
    private val currentOptionPaint = dashedCopy(currentRoutePaint)
    private val otherOptionPaint = dashedCopy(otherRoutePaint)

    private val branchPointPaint = Paint().apply {
        style = Paint.Style.FILL
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
        options = emptyMap()
        players = emptyList()
        isFullPlay = false
        invalidate()
    }

    fun setPlay(
        newMovements: Map<String, List<PointData>>,
        newPlayers: List<PlayerPosition>,
        fullPlay: Boolean,
        newOptions: Map<String, List<RouteBranch>> = emptyMap()
    ) {
        movements = newMovements
        options = newOptions
        players = newPlayers.filter { it.isActive && it.x != null && it.y != null }
        isFullPlay = fullPlay
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBoard(canvas)
        drawRoutes(canvas)
        drawPlayers(canvas)
    }

    private fun drawPlayers(canvas: Canvas) {
        if (players.isEmpty()) return

        // The full play has 11 markers on a small screen, so they are smaller than a single marker.
        val base = min(contentRect.width(), contentRect.height())
        val radius = if (isFullPlay) base * 0.045f else base * 0.07f
        playerLabelPaint.textSize = radius * 0.8f

        players.forEach { player ->
            val cx = scaleX(player.x!!)
            val cy = scaleY(player.y!!)
            val isOwn = player.displayLabel.equals(currentRole, ignoreCase = true)

            canvas.drawCircle(cx, cy, radius, if (isOwn) ownPlayerFillPaint else playerFillPaint)

            val label = player.displayLabel
            val maxWidth = radius * 1.7f
            val measured = playerLabelPaint.measureText(label)
            val originalSize = playerLabelPaint.textSize
            if (measured > maxWidth) playerLabelPaint.textSize = originalSize * maxWidth / measured

            val baseline = cy - (playerLabelPaint.descent() + playerLabelPaint.ascent()) / 2f
            canvas.drawText(label, cx, baseline, playerLabelPaint)
            playerLabelPaint.textSize = originalSize
        }
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

            points.forEachIndexed { index, point ->
                val scaledX = scaleX(point.x)
                val scaledY = scaleY(point.y)

                if (index == 0) {
                    firstX = scaledX
                    firstY = scaledY
                    path.moveTo(scaledX, scaledY)
                } else {
                    path.lineTo(scaledX, scaledY)
                }

            }

            val paint = if (role.equals(currentRole, ignoreCase = true)) {
                currentRoutePaint
            } else {
                otherRoutePaint
            }

            canvas.drawPath(path, paint)
            canvas.drawCircle(firstX, firstY, dp(4f), startPointPaint)
            drawArrowHead(canvas, points, paint)
            drawOptions(canvas, role, paint)
            if (players.isEmpty()) {
                drawRoleLabel(canvas, role, firstX, firstY)
            }
        }
    }

    // Each option starts at a branch point on the main route and is dashed in the route's colour.
    private fun drawOptions(canvas: Canvas, role: String, routePaint: Paint) {
        val branches = options[role] ?: return
        val optionPaint = if (routePaint === currentRoutePaint) currentOptionPaint else otherOptionPaint
        branchPointPaint.color = routePaint.color

        branches.forEach { branch ->
            val points = branch.points
            if (points.size < 2) return@forEach

            val path = Path()
            points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(scaleX(point.x), scaleY(point.y))
                else path.lineTo(scaleX(point.x), scaleY(point.y))
            }

            canvas.drawPath(path, optionPaint)
            // The arrowhead stays solid so its direction reads clearly.
            drawArrowHead(canvas, points, routePaint)
            canvas.drawCircle(scaleX(points[0].x), scaleY(points[0].y), routePaint.strokeWidth * 0.9f, branchPointPaint)
        }
    }

    private fun dashedCopy(source: Paint): Paint {
        return Paint(source).apply {
            val width = source.strokeWidth
            strokeCap = Paint.Cap.BUTT
            pathEffect = DashPathEffect(floatArrayOf(width * 2.4f, width * 1.7f), 0f)
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

    // Points the arrow along the end of the route. The last two points of a finger drawn route
    // can be a pixel apart and twist the arrow, so it looks back until the points are far enough
    // apart to give a steady direction.
    private fun drawArrowHead(canvas: Canvas, points: List<PointData>, paint: Paint) {
        val arrowLength = dp(12f)
        val toX = scaleX(points.last().x)
        val toY = scaleY(points.last().y)

        var fromX = scaleX(points[points.lastIndex - 1].x)
        var fromY = scaleY(points[points.lastIndex - 1].y)
        for (i in points.lastIndex - 1 downTo 0) {
            fromX = scaleX(points[i].x)
            fromY = scaleY(points[i].y)
            if (hypot(toX - fromX, toY - fromY) >= arrowLength) break
        }
        if (hypot(toX - fromX, toY - fromY) < 1f) return

        val angle = atan2(toY - fromY, toX - fromX)
        val arrowAngle = Math.toRadians(28.0).toFloat()

        val x1 = toX - arrowLength * cos(angle - arrowAngle)
        val y1 = toY - arrowLength * sin(angle - arrowAngle)
        val x2 = toX - arrowLength * cos(angle + arrowAngle)
        val y2 = toY - arrowLength * sin(angle + arrowAngle)

        canvas.drawLine(toX, toY, x1, y1, paint)
        canvas.drawLine(toX, toY, x2, y2, paint)
    }

    // Tablet coordinates are normalized 0 to 1, so the same route fits any watch screen.
    private fun scaleX(normalizedX: Float): Float {
        return contentRect.left + normalizedX.coerceIn(0f, 1f) * contentRect.width()
    }

    private fun scaleY(normalizedY: Float): Float {
        return contentRect.top + normalizedY.coerceIn(0f, 1f) * contentRect.height()
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
package com.cloud9.gridsync.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.cloud9.gridsync.network.PlayFormation
import com.cloud9.gridsync.network.PlayerPosition
import com.cloud9.gridsync.network.PointData
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class CoachDrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    enum class Mode { MOVE, ROUTE }

    interface Listener {
        fun onPlayerSelected(player: PlayerPosition?)
        fun onSwapTargetChosen(activePlayer: PlayerPosition)
        fun onFormationChanged()
        fun onSelectionNeeded()
    }

    var listener: Listener? = null

    var mode = Mode.MOVE
        set(value) {
            field = value
            invalidate()
        }

    // While true, touches pick the active player a bench player will replace.
    var swapPending = false
        set(value) {
            field = value
            swapHoverId = null
            invalidate()
        }

    // Players and routes both use normalized 0 to 1 field coordinates. Routes are keyed by player id.
    private val players = mutableListOf<PlayerPosition>()
    private val pointsMap = mutableMapOf<String, MutableList<PointData>>()
    private var selectedId: String? = null

    private var dragId: String? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var routeId: String? = null
    private var routeBackup: List<PointData>? = null
    private var routeTravel = 0f

    private var swapHoverId: String? = null

    private val boardRect = RectF()

    private val gold = Color.parseColor("#FCA311")
    private val navy = Color.parseColor("#14213D")
    private val slate = Color.parseColor("#415A77")

    private val selectedRoutePaint = Paint().apply {
        color = gold
        strokeWidth = dp(4f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(dp(6f))
        isAntiAlias = true
    }

    private val otherRoutePaint = Paint().apply {
        color = Color.argb(200, 20, 33, 61)
        strokeWidth = dp(3f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(dp(6f))
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

    private val markerFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val markerOutlinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val selectedRingPaint = Paint().apply {
        color = gold
        strokeWidth = dp(3f)
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val swapTargetPaint = Paint().apply {
        color = gold
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
        isAntiAlias = true
    }

    private val swapHoverFillPaint = Paint().apply {
        color = Color.argb(110, 252, 163, 17)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val markerLabelPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    fun setFormation(newPlayers: List<PlayerPosition>, movements: Map<String, List<PointData>>) {
        players.clear()
        players.addAll(newPlayers)

        pointsMap.clear()
        movements.forEach { (id, points) ->
            if (players.any { it.id == id && it.isActive } && points.size >= 2) {
                pointsMap[id] = points.map {
                    PointData(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f))
                }.toMutableList()
            }
        }

        if (players.none { it.id == selectedId && it.isActive }) {
            selectedId = null
        }

        cancelTouchState()
        listener?.onPlayerSelected(getSelectedPlayer())
        listener?.onFormationChanged()
        invalidate()
    }

    fun getPlayers(): List<PlayerPosition> = players.toList()

    fun getMovements(): Map<String, List<PointData>> {
        return pointsMap
            .filterValues { it.size >= 2 }
            .mapValues { it.value.toList() }
    }

    fun getSelectedPlayer(): PlayerPosition? = players.firstOrNull { it.id == selectedId }

    fun selectPlayer(id: String?) {
        val newId = id?.takeIf { wanted -> players.any { it.id == wanted && it.isActive } }
        if (newId == selectedId) return
        selectedId = newId
        listener?.onPlayerSelected(getSelectedPlayer())
        invalidate()
    }

    // Replaces a player's assignment data while keeping the position the view already holds.
    fun updatePlayerDetails(id: String, assignmentType: String, instruction: String) {
        val index = players.indexOfFirst { it.id == id }
        if (index < 0) return
        players[index] = players[index].copy(assignmentType = assignmentType, instruction = instruction)
    }

    fun hasRoute(id: String): Boolean = (pointsMap[id]?.size ?: 0) >= 2

    fun clearRoute(id: String) {
        pointsMap.remove(id)
        listener?.onFormationChanged()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = dp(10f)
        boardRect.set(padding, padding, w - padding, h - padding)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (boardRect.isEmpty) return false

        val nx = ((event.x - boardRect.left) / boardRect.width()).coerceIn(0f, 1f)
        val ny = ((event.y - boardRect.top) / boardRect.height()).coerceIn(0f, 1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!boardRect.contains(event.x, event.y)) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                handleDown(event.x, event.y, nx, ny)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                handleMove(event.x, event.y, nx, ny)
                return true
            }

            MotionEvent.ACTION_UP -> {
                handleUp(event.x, event.y, nx, ny)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                routeId?.let { id -> restoreRoute(id) }
                cancelTouchState()
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun handleDown(px: Float, py: Float, nx: Float, ny: Float) {
        val hit = hitTest(px, py)

        if (swapPending) {
            swapHoverId = hit?.id
            invalidate()
            return
        }

        when (mode) {
            Mode.MOVE -> {
                if (hit != null) {
                    selectPlayer(hit.id)
                    dragId = hit.id
                    lastTouchX = nx
                    lastTouchY = ny
                }
            }

            Mode.ROUTE -> {
                val startPlayer = hit ?: getSelectedPlayer()
                if (startPlayer == null) {
                    listener?.onSelectionNeeded()
                    return
                }

                val startX = startPlayer.x ?: return
                val startY = startPlayer.y ?: return

                selectPlayer(startPlayer.id)
                routeId = startPlayer.id
                routeBackup = pointsMap[startPlayer.id]?.toList()
                routeTravel = 0f

                // Every route starts at its player's marker, even when the finger lands elsewhere.
                val newRoute = mutableListOf(PointData(startX, startY))
                if (hit == null) newRoute.add(PointData(nx, ny))
                pointsMap[startPlayer.id] = newRoute
            }
        }

        invalidate()
    }

    private fun handleMove(px: Float, py: Float, nx: Float, ny: Float) {
        if (swapPending) {
            val hoverId = hitTest(px, py)?.id
            if (hoverId != swapHoverId) {
                swapHoverId = hoverId
                invalidate()
            }
            return
        }

        dragId?.let { id ->
            movePlayerBy(id, nx - lastTouchX, ny - lastTouchY)
            lastTouchX = nx
            lastTouchY = ny
            invalidate()
            return
        }

        routeId?.let { id ->
            val points = pointsMap[id] ?: return
            val last = points.last()
            val distance = hypot(
                (nx - last.x) * boardRect.width(),
                (ny - last.y) * boardRect.height()
            )

            if (distance >= dp(4f)) {
                points.add(PointData(nx, ny))
                routeTravel += distance
                invalidate()
            }
        }
    }

    private fun handleUp(px: Float, py: Float, nx: Float, ny: Float) {
        if (swapPending) {
            val target = hitTest(px, py)
            val hoverId = swapHoverId
            swapHoverId = null
            invalidate()
            if (target != null && target.id == hoverId) {
                listener?.onSwapTargetChosen(target)
            }
            return
        }

        if (dragId != null) {
            dragId = null
            listener?.onFormationChanged()
            return
        }

        routeId?.let { id ->
            pointsMap[id]?.let { points ->
                val last = points.last()
                routeTravel += hypot(
                    (nx - last.x) * boardRect.width(),
                    (ny - last.y) * boardRect.height()
                )
                points.add(PointData(nx, ny))
            }

            // A short tap in route mode only selects the player and keeps any existing route.
            if (routeTravel < dp(12f)) {
                restoreRoute(id)
            }

            routeId = null
            routeBackup = null
            listener?.onFormationChanged()
            invalidate()
        }
    }

    private fun restoreRoute(id: String) {
        val backup = routeBackup
        if (backup != null && backup.size >= 2) {
            pointsMap[id] = backup.toMutableList()
        } else {
            pointsMap.remove(id)
        }
    }

    // Moves a player inside the field and shifts its route by the same amount so the route stays attached.
    private fun movePlayerBy(id: String, dx: Float, dy: Float) {
        val index = players.indexOfFirst { it.id == id }
        if (index < 0) return

        val player = players[index]
        val oldX = player.x ?: return
        val oldY = player.y ?: return

        val marginX = markerRadius() / boardRect.width()
        val marginY = markerRadius() / boardRect.height()

        val newX = (oldX + dx).coerceIn(marginX, 1f - marginX)
        val newY = (oldY + dy).coerceIn(marginY, 1f - marginY)

        players[index] = player.copy(x = newX, y = newY)

        val appliedDx = newX - oldX
        val appliedDy = newY - oldY
        pointsMap[id]?.let { points ->
            for (i in points.indices) {
                points[i] = PointData(
                    (points[i].x + appliedDx).coerceIn(0f, 1f),
                    (points[i].y + appliedDy).coerceIn(0f, 1f)
                )
            }
        }
    }

    private fun hitTest(px: Float, py: Float): PlayerPosition? {
        val touchRadius = markerRadius() * 1.3f

        return players
            .filter { it.isActive && it.x != null && it.y != null }
            .map { player -> player to hypot(toPixelX(player.x!!) - px, toPixelY(player.y!!) - py) }
            .filter { it.second <= touchRadius }
            .minByOrNull { it.second }
            ?.first
    }

    private fun cancelTouchState() {
        dragId = null
        routeId = null
        routeBackup = null
        swapHoverId = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCoachBoard(canvas)
        drawRoutes(canvas)
        drawPlayers(canvas)
    }

    private fun drawCoachBoard(canvas: Canvas) {
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

    private fun drawRoutes(canvas: Canvas) {
        // Draw the selected route last so it sits on top of the others.
        val ordered = pointsMap.entries.sortedBy { it.key == selectedId }

        ordered.forEach { (id, points) ->
            if (points.size < 2) return@forEach

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = toPixelX(point.x)
                val y = toPixelY(point.y)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            val paint = if (id == selectedId) selectedRoutePaint else otherRoutePaint
            canvas.drawPath(path, paint)
            drawRouteArrow(canvas, points, paint)
        }
    }

    // Uses a point a little way back from the end so hand jitter does not twist the arrowhead.
    private fun drawRouteArrow(canvas: Canvas, points: List<PointData>, paint: Paint) {
        val end = points.last()
        val endX = toPixelX(end.x)
        val endY = toPixelY(end.y)

        var fromX = toPixelX(points[points.lastIndex - 1].x)
        var fromY = toPixelY(points[points.lastIndex - 1].y)

        for (i in points.lastIndex - 1 downTo 0) {
            val x = toPixelX(points[i].x)
            val y = toPixelY(points[i].y)
            fromX = x
            fromY = y
            if (hypot(endX - x, endY - y) >= dp(14f)) break
        }

        if (hypot(endX - fromX, endY - fromY) < 1f) return

        val angle = atan2(endY - fromY, endX - fromX)
        val arrowLength = dp(14f)
        val arrowAngle = Math.toRadians(28.0).toFloat()

        canvas.drawLine(
            endX, endY,
            endX - arrowLength * cos(angle - arrowAngle),
            endY - arrowLength * sin(angle - arrowAngle),
            paint
        )
        canvas.drawLine(
            endX, endY,
            endX - arrowLength * cos(angle + arrowAngle),
            endY - arrowLength * sin(angle + arrowAngle),
            paint
        )
    }

    private fun drawPlayers(canvas: Canvas) {
        val radius = markerRadius()

        players.filter { it.isActive && it.x != null && it.y != null }.forEach { player ->
            val cx = toPixelX(player.x!!)
            val cy = toPixelY(player.y!!)
            val isSelected = player.id == selectedId

            if (swapPending) {
                if (player.id == swapHoverId) {
                    canvas.drawCircle(cx, cy, radius * 1.45f, swapHoverFillPaint)
                    canvas.drawCircle(cx, cy, radius * 1.45f, selectedRingPaint)
                } else {
                    canvas.drawCircle(cx, cy, radius * 1.3f, swapTargetPaint)
                }
            }

            markerFillPaint.color = when {
                isSelected -> gold
                PlayFormation.isLineman(player) -> slate
                else -> navy
            }

            canvas.drawCircle(cx, cy, radius, markerFillPaint)
            canvas.drawCircle(cx, cy, radius, markerOutlinePaint)

            if (isSelected) {
                canvas.drawCircle(cx, cy, radius + dp(4f), selectedRingPaint)
            }

            drawMarkerLabel(canvas, player.displayLabel, cx, cy, radius, isSelected)
        }
    }

    private fun drawMarkerLabel(
        canvas: Canvas,
        label: String,
        cx: Float,
        cy: Float,
        radius: Float,
        isSelected: Boolean
    ) {
        markerLabelPaint.color = if (isSelected) navy else Color.WHITE
        markerLabelPaint.textSize = radius * 0.72f

        val maxWidth = radius * 1.6f
        val measured = markerLabelPaint.measureText(label)
        if (measured > maxWidth) {
            markerLabelPaint.textSize *= maxWidth / measured
        }

        val baseline = cy - (markerLabelPaint.descent() + markerLabelPaint.ascent()) / 2f
        canvas.drawText(label, cx, baseline, markerLabelPaint)
    }

    private fun markerRadius(): Float {
        return (min(boardRect.width(), boardRect.height()) * 0.032f).coerceIn(dp(16f), dp(26f))
    }

    private fun toPixelX(normalizedX: Float): Float {
        return boardRect.left + normalizedX.coerceIn(0f, 1f) * boardRect.width()
    }

    private fun toPixelY(normalizedY: Float): Float {
        return boardRect.top + normalizedY.coerceIn(0f, 1f) * boardRect.height()
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}

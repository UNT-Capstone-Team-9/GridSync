package com.cloud9.gridsync.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposePathEffect
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
import com.cloud9.gridsync.network.RouteBranch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class CoachDrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // OPTION adds a dashed option branch to the selected player's existing route.
    enum class Mode { MOVE, ROUTE, OPTION }

    interface Listener {
        fun onPlayerSelected(player: PlayerPosition?)
        fun onSwapTargetChosen(activePlayer: PlayerPosition)
        fun onFormationChanged()
        fun onSelectionNeeded()
        fun onRouteOptionHint(message: String)
    }

    var listener: Listener? = null

    var mode = Mode.MOVE
        set(value) {
            field = value
            pendingBranchPoint = null
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

    // Dashed option branches per player id. Each branch starts at a point on that player's main
    // route and is moved and cleared together with it.
    private val optionsMap = mutableMapOf<String, MutableList<MutableList<PointData>>>()

    private var dragId: String? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var routeId: String? = null
    private var routeBackup: List<PointData>? = null
    private var routeTravel = 0f
    private var routeOptionsBackup: List<MutableList<PointData>>? = null

    // The option branch being drawn. It is always the last branch in optionsMap[optionId].
    private var optionId: String? = null
    private var optionTravel = 0f

    // A branch point picked with a short tap. The next drag starts the option from here.
    private var pendingBranchPoint: PointData? = null

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

    // Option branches keep the player's route colour and width and only add the dash.
    private val selectedOptionPaint = dashedCopy(selectedRoutePaint)
    private val otherOptionPaint = dashedCopy(otherRoutePaint)

    private val branchPointPaint = Paint().apply {
        style = Paint.Style.FILL
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

    fun setFormation(
        newPlayers: List<PlayerPosition>,
        movements: Map<String, List<PointData>>,
        options: Map<String, List<RouteBranch>> = emptyMap()
    ) {
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

        optionsMap.clear()
        options.forEach { (id, branches) ->
            if (!pointsMap.containsKey(id)) return@forEach
            val kept = branches.filter { it.points.size >= 2 }.map { branch ->
                branch.points.map { PointData(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }.toMutableList()
            }
            if (kept.isNotEmpty()) optionsMap[id] = kept.toMutableList()
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

    fun getRouteOptions(): Map<String, List<RouteBranch>> {
        return optionsMap
            .filterKeys { (pointsMap[it]?.size ?: 0) >= 2 }
            .mapValues { (_, branches) -> branches.filter { it.size >= 2 }.map { RouteBranch(it.toList()) } }
            .filterValues { it.isNotEmpty() }
    }

    fun getSelectedPlayer(): PlayerPosition? = players.firstOrNull { it.id == selectedId }

    fun selectPlayer(id: String?) {
        val newId = id?.takeIf { wanted -> players.any { it.id == wanted && it.isActive } }
        if (newId == selectedId) return
        selectedId = newId
        pendingBranchPoint = null
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

    fun hasOptions(id: String): Boolean = optionsMap[id].orEmpty().isNotEmpty()

    // Removes the main route and every option branch that hangs off it.
    fun clearRoute(id: String) {
        pointsMap.remove(id)
        optionsMap.remove(id)
        pendingBranchPoint = null
        listener?.onFormationChanged()
        invalidate()
    }

    // Removes the most recently added option branch and keeps the main route.
    fun removeLastOption(id: String): Boolean {
        val branches = optionsMap[id] ?: return false
        if (branches.isEmpty()) return false
        branches.removeAt(branches.lastIndex)
        if (branches.isEmpty()) optionsMap.remove(id)
        listener?.onFormationChanged()
        invalidate()
        return true
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
                optionId?.let { id -> discardOptionDraft(id) }
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

                // A redrawn main route no longer passes through the old branch points, so its
                // options are removed. A short tap or cancel brings them back with the old route.
                routeOptionsBackup = optionsMap.remove(startPlayer.id)

                // Every route starts at its player's marker, even when the finger lands elsewhere.
                val newRoute = mutableListOf(PointData(startX, startY))
                if (hit == null) newRoute.add(PointData(nx, ny))
                pointsMap[startPlayer.id] = newRoute
            }

            Mode.OPTION -> handleOptionDown(hit, px, py, nx, ny)
        }

        invalidate()
    }

    private fun handleOptionDown(hit: PlayerPosition?, px: Float, py: Float, nx: Float, ny: Float) {
        val selected = getSelectedPlayer()
        val branchPoint = selected?.let { nearestPointOnRoute(it.id, px, py) }

        when {
            selected != null && branchPoint != null -> startOption(selected.id, branchPoint, null)

            hit != null && hit.id != selected?.id -> {
                selectPlayer(hit.id)
                if (!hasRoute(hit.id)) {
                    listener?.onRouteOptionHint("Draw ${hit.displayLabel}'s main route first")
                }
            }

            selected == null -> listener?.onRouteOptionHint("Select a player with a route first")

            !hasRoute(selected.id) ->
                listener?.onRouteOptionHint("Draw ${selected.displayLabel}'s main route first")

            // After a tap picked the branch point, the coach can drag from anywhere.
            pendingBranchPoint != null -> startOption(selected.id, pendingBranchPoint!!, PointData(nx, ny))

            else -> listener?.onRouteOptionHint("Select a point on the player's existing route.")
        }
    }

    private fun startOption(id: String, branchPoint: PointData, firstPoint: PointData?) {
        val draft = mutableListOf(branchPoint)
        if (firstPoint != null) draft.add(firstPoint)
        optionsMap.getOrPut(id) { mutableListOf() }.add(draft)
        optionId = id
        optionTravel = 0f
        pendingBranchPoint = branchPoint
    }

    private fun discardOptionDraft(id: String) {
        val branches = optionsMap[id] ?: return
        if (branches.isNotEmpty()) branches.removeAt(branches.lastIndex)
        if (branches.isEmpty()) optionsMap.remove(id)
    }

    // Closest point on the player's main route to the touch, or null when the touch is too far
    // from the route. The point is projected onto the nearest segment so it sits exactly on the line.
    private fun nearestPointOnRoute(id: String, px: Float, py: Float): PointData? {
        val points = pointsMap[id] ?: return null
        if (points.size < 2) return null

        var bestDistance = Float.MAX_VALUE
        var bestX = 0f
        var bestY = 0f

        for (i in 0 until points.lastIndex) {
            val ax = toPixelX(points[i].x)
            val ay = toPixelY(points[i].y)
            val bx = toPixelX(points[i + 1].x)
            val by = toPixelY(points[i + 1].y)

            val dx = bx - ax
            val dy = by - ay
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)

            val cx = ax + t * dx
            val cy = ay + t * dy
            val distance = hypot(px - cx, py - cy)
            if (distance < bestDistance) {
                bestDistance = distance
                bestX = cx
                bestY = cy
            }
        }

        if (bestDistance > dp(28f)) return null

        return PointData(
            ((bestX - boardRect.left) / boardRect.width()).coerceIn(0f, 1f),
            ((bestY - boardRect.top) / boardRect.height()).coerceIn(0f, 1f)
        )
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
            return
        }

        optionId?.let { id ->
            val points = optionsMap[id]?.lastOrNull() ?: return
            val last = points.last()
            val distance = hypot(
                (nx - last.x) * boardRect.width(),
                (ny - last.y) * boardRect.height()
            )

            if (distance >= dp(4f)) {
                points.add(PointData(nx, ny))
                optionTravel += distance
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
            routeOptionsBackup = null
            listener?.onFormationChanged()
            invalidate()
            return
        }

        optionId?.let { id ->
            optionsMap[id]?.lastOrNull()?.let { points ->
                val last = points.last()
                optionTravel += hypot(
                    (nx - last.x) * boardRect.width(),
                    (ny - last.y) * boardRect.height()
                )
                points.add(PointData(nx, ny))
            }

            if (optionTravel < dp(12f)) {
                // A short tap only picks the branch point, which stays highlighted for the next drag.
                discardOptionDraft(id)
            } else {
                pendingBranchPoint = null
                listener?.onFormationChanged()
            }

            optionId = null
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

        val optionsBackup = routeOptionsBackup
        if (optionsBackup != null && pointsMap.containsKey(id)) {
            optionsMap[id] = optionsBackup.toMutableList()
        }
        routeOptionsBackup = null
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
        pointsMap[id]?.let { points -> shiftPoints(points, appliedDx, appliedDy) }
        optionsMap[id]?.forEach { branch -> shiftPoints(branch, appliedDx, appliedDy) }
    }

    private fun shiftPoints(points: MutableList<PointData>, dx: Float, dy: Float) {
        for (i in points.indices) {
            points[i] = PointData(
                (points[i].x + dx).coerceIn(0f, 1f),
                (points[i].y + dy).coerceIn(0f, 1f)
            )
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
        routeOptionsBackup = null
        optionId = null
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

            drawOptions(canvas, id, paint)
        }

        pendingBranchPoint?.let { point ->
            val cx = toPixelX(point.x)
            val cy = toPixelY(point.y)
            branchPointPaint.color = gold
            canvas.drawCircle(cx, cy, dp(7f), branchPointPaint)
            canvas.drawCircle(cx, cy, dp(7f), markerOutlinePaint)
        }
    }

    // Dashed branches in the player's route colour, with a small dot at the branch point. The
    // arrowheads use the solid route paint so they do not break up into dashes.
    private fun drawOptions(canvas: Canvas, id: String, routePaint: Paint) {
        val branches = optionsMap[id] ?: return
        val optionPaint = if (routePaint === selectedRoutePaint) selectedOptionPaint else otherOptionPaint
        branchPointPaint.color = routePaint.color

        branches.forEach { points ->
            if (points.size < 2) return@forEach

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = toPixelX(point.x)
                val y = toPixelY(point.y)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            canvas.drawPath(path, optionPaint)
            drawRouteArrow(canvas, points, routePaint)
            canvas.drawCircle(toPixelX(points[0].x), toPixelY(points[0].y), dp(4.5f), branchPointPaint)
        }
    }

    private fun dashedCopy(source: Paint): Paint {
        return Paint(source).apply {
            strokeCap = Paint.Cap.BUTT
            pathEffect = ComposePathEffect(
                DashPathEffect(floatArrayOf(dp(12f), dp(8f)), 0f),
                CornerPathEffect(dp(6f))
            )
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

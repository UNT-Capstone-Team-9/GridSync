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
import com.cloud9.gridsync.network.PlayFormation
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PlayerPosition
import com.cloud9.gridsync.network.PointData
import com.cloud9.gridsync.network.RouteBranch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// Read only drawing of a whole saved play for the Play Library and Hurry-Up cards. It reads the
// same PlayMessage the watches get and scales its normalized coordinates to the view, so no
// thumbnail is ever stored. The view keeps the coach board's aspect ratio set by the layout.
class PlayPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // options are the player's dashed option branches, drawn in the same colour as the main route.
    private data class Route(
        val label: String,
        val points: List<PointData>,
        val color: Int,
        val options: List<RouteBranch> = emptyList()
    )

    private var players: List<PlayerPosition> = emptyList()
    private var routes: List<Route> = emptyList()
    private var routeColorById: Map<String, Int> = emptyMap()
    private var hasSavedFormation = false
    // Null when unknown, as on a single receiver's watch, so no line is drawn in the wrong place.
    private var lineOfScrimmage: Float? = 0.60f

    // The QB watch shows this view on a tiny screen, so it asks for larger, always labelled markers.
    var largeMarkers = false
        set(value) {
            field = value
            invalidate()
        }


    private val fieldRect = RectF()

    private val fieldPaint = Paint().apply {
        color = Color.parseColor("#16301F")
        style = Paint.Style.FILL
    }

    private val yardLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE
    }

    private val hashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 255, 255, 255)
        style = Paint.Style.STROKE
    }

    private val scrimmagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F8EF7")
        style = Paint.Style.STROKE
    }

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val branchPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val markerOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B1410")
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        textAlign = Paint.Align.CENTER
    }

    private val lineman = Color.parseColor("#B0BEC5")
    private val quarterback = Color.parseColor("#FCA311")
    private val skillNoRoute = Color.WHITE

    fun setPlay(play: PlayMessage?) {
        if (play == null) {
            players = emptyList()
            routes = emptyList()
            routeColorById = emptyMap()
            hasSavedFormation = false
            invalidate()
            return
        }

        // fromSavedPlay already handles older plays, so the preview matches what the editor loads.
        val loaded = PlayFormation.fromSavedPlay(play)
        val savedPlayers = play.players?.filterNotNull()
        hasSavedFormation = savedPlayers != null && PlayFormation.validate(savedPlayers) == null

        val activePlayers = loaded.players.filter { it.isActive && it.x != null && it.y != null }

        // The same colours the watches get, so a receiver's route looks the same everywhere.
        val colors = PlayFormation.routeColors(activePlayers, loaded.movements)
            .mapValues { Color.parseColor(it.value) }
        val builtRoutes = activePlayers.sortedBy { it.x }.mapNotNull { player ->
            val color = colors[player.id] ?: return@mapNotNull null
            Route(player.displayLabel, loaded.movements.getValue(player.id), color, loaded.options[player.id].orEmpty())
        }

        routeColorById = colors
        routes = builtRoutes

        // Older plays only have routes, so only the players who run one are drawn, at the
        // start of their route, rather than inventing a formation the coach never saved.
        players = if (hasSavedFormation) {
            activePlayers
        } else {
            activePlayers.filter { it.id in colors }.map { player ->
                val start = loaded.movements.getValue(player.id).first()
                player.copy(x = start.x, y = start.y)
            }
        }

        val center = activePlayers.firstOrNull { it.positionType.equals("C", ignoreCase = true) }
        lineOfScrimmage = if (hasSavedFormation) center?.y ?: 0.60f else 0.60f

        invalidate()
    }

    // One player's own assignment on their watch: their marker, route and options, in the colour
    // the tablet worked out from the whole play. A missing colour falls back to the first one.
    fun setPlayerRoute(
        player: PlayerPosition?,
        route: List<PointData>,
        options: List<RouteBranch>,
        colorHex: String?
    ) {
        val color = try {
            Color.parseColor(colorHex ?: PlayFormation.routePalette.first())
        } catch (_: IllegalArgumentException) {
            Color.parseColor(PlayFormation.routePalette.first())
        }

        val start = route.firstOrNull()
        val marker = when {
            player != null && player.x != null && player.y != null -> player
            player != null && start != null -> player.copy(x = start.x, y = start.y)
            else -> null
        }

        players = listOfNotNull(marker)
        routes = if (route.size >= 2) {
            listOf(Route(marker?.displayLabel.orEmpty(), route, color, options))
        } else {
            emptyList()
        }
        routeColorById = marker?.let { mapOf(it.id to color) }.orEmpty()
        hasSavedFormation = true
        lineOfScrimmage = null
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Wrap content height follows a landscape playbook card shape.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val height = if (heightMode == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            (width / ASPECT_RATIO).toInt()
        }
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fieldRect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (fieldRect.isEmpty) return

        drawField(canvas)
        drawRoutes(canvas)
        drawPlayers(canvas)

        if (players.isEmpty() && routes.isEmpty()) {
            drawCaption(canvas, "No formation saved")
        } else if (!hasSavedFormation) {
            drawCaption(canvas, "Routes only")
        }
    }

    private fun drawField(canvas: Canvas) {
        canvas.drawRect(fieldRect, fieldPaint)

        val stroke = scale() * 0.004f
        yardLinePaint.strokeWidth = stroke.coerceAtLeast(1f)
        hashPaint.strokeWidth = stroke.coerceAtLeast(1f)

        // Same six bands as the coach board, so positions read the same way.
        for (i in 1 until 6) {
            val y = fieldRect.top + fieldRect.height() * i / 6f
            canvas.drawLine(fieldRect.left, y, fieldRect.right, y, yardLinePaint)
        }

        val hashLength = fieldRect.width() * 0.015f
        val ticks = 30
        for (i in 1 until ticks) {
            val y = fieldRect.top + fieldRect.height() * i / ticks
            listOf(0.33f, 0.66f).forEach { fraction ->
                val x = fieldRect.left + fieldRect.width() * fraction
                canvas.drawLine(x - hashLength / 2f, y, x + hashLength / 2f, y, hashPaint)
            }
        }

        val scrimmageY = lineOfScrimmage ?: return
        scrimmagePaint.strokeWidth = (scale() * 0.008f).coerceAtLeast(1.5f)
        val losY = toPixelY(scrimmageY)
        canvas.drawLine(fieldRect.left, losY, fieldRect.right, losY, scrimmagePaint)
    }

    private fun drawRoutes(canvas: Canvas) {
        routePaint.strokeWidth = (scale() * 0.011f).coerceAtLeast(1.5f)

        routes.forEach { route ->
            val path = Path()
            route.points.forEachIndexed { index, point ->
                val x = toPixelX(point.x)
                val y = toPixelY(point.y)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            routePaint.color = route.color
            canvas.drawPath(path, routePaint)
            drawArrow(canvas, route.points)
            drawOptions(canvas, route)
        }
    }

    // Same colour as the main route, but dashed, with a dot where the route can change.
    private fun drawOptions(canvas: Canvas, route: Route) {
        if (route.options.isEmpty()) return

        val width = routePaint.strokeWidth
        val dashEffect = DashPathEffect(floatArrayOf(width * 2.4f, width * 1.8f), 0f)
        branchPointPaint.color = route.color

        route.options.forEach { branch ->
            val points = branch.points
            if (points.size < 2) return@forEach

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = toPixelX(point.x)
                val y = toPixelY(point.y)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            routePaint.strokeCap = Paint.Cap.BUTT
            routePaint.pathEffect = dashEffect
            canvas.drawPath(path, routePaint)

            // Back to a solid line so the arrowhead and every later route are not dashed.
            routePaint.pathEffect = null
            routePaint.strokeCap = Paint.Cap.ROUND
            drawArrow(canvas, points)

            canvas.drawCircle(toPixelX(points[0].x), toPixelY(points[0].y), width * 1.1f, branchPointPaint)
        }
    }

    // Looks back along the route so hand jitter at the very end does not twist the arrowhead.
    private fun drawArrow(canvas: Canvas, points: List<PointData>) {
        val arrowLength = scale() * 0.045f
        val endX = toPixelX(points.last().x)
        val endY = toPixelY(points.last().y)

        var fromX = endX
        var fromY = endY
        for (i in points.lastIndex - 1 downTo 0) {
            fromX = toPixelX(points[i].x)
            fromY = toPixelY(points[i].y)
            if (hypot(endX - fromX, endY - fromY) >= arrowLength) break
        }
        if (hypot(endX - fromX, endY - fromY) < 1f) return

        val angle = atan2(endY - fromY, endX - fromX)
        val spread = Math.toRadians(28.0).toFloat()

        canvas.drawLine(
            endX, endY,
            endX - arrowLength * cos(angle - spread),
            endY - arrowLength * sin(angle - spread),
            routePaint
        )
        canvas.drawLine(
            endX, endY,
            endX - arrowLength * cos(angle + spread),
            endY - arrowLength * sin(angle + spread),
            routePaint
        )
    }

    private fun drawPlayers(canvas: Canvas) {
        val radius = scale() * if (largeMarkers) 0.05f else 0.032f
        markerOutlinePaint.strokeWidth = (radius * 0.18f).coerceAtLeast(1f)

        // Labels only fit once the card is big enough to read them.
        val showLabels = largeMarkers || radius >= dp(7f)

        players.forEach { player ->
            val cx = toPixelX(player.x ?: return@forEach)
            val cy = toPixelY(player.y ?: return@forEach)

            markerPaint.color = when {
                PlayFormation.isQuarterback(player) -> quarterback
                PlayFormation.isLineman(player) -> lineman
                else -> routeColorById[player.id] ?: skillNoRoute
            }

            // Linemen are squares and everyone else is a circle, as in a paper playbook.
            if (PlayFormation.isLineman(player)) {
                val half = radius * 0.9f
                canvas.drawRect(cx - half, cy - half, cx + half, cy + half, markerPaint)
                canvas.drawRect(cx - half, cy - half, cx + half, cy + half, markerOutlinePaint)
            } else {
                canvas.drawCircle(cx, cy, radius, markerPaint)
                canvas.drawCircle(cx, cy, radius, markerOutlinePaint)
            }

            if (showLabels) drawLabel(canvas, player.displayLabel, cx, cy, radius)
        }
    }

    private fun drawLabel(canvas: Canvas, label: String, cx: Float, cy: Float, radius: Float) {
        labelPaint.color = Color.parseColor("#0B1410")
        labelPaint.textSize = radius * 0.85f

        val maxWidth = radius * 1.7f
        val measured = labelPaint.measureText(label)
        if (measured > maxWidth) labelPaint.textSize *= maxWidth / measured

        val baseline = cy - (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText(label, cx, baseline, labelPaint)
    }

    private fun drawCaption(canvas: Canvas, text: String) {
        captionPaint.textSize = scale() * 0.06f
        canvas.drawText(text, fieldRect.centerX(), fieldRect.bottom - captionPaint.textSize * 0.8f, captionPaint)
    }

    private fun scale(): Float = min(fieldRect.width(), fieldRect.height() * ASPECT_RATIO)

    private fun toPixelX(normalizedX: Float): Float {
        return fieldRect.left + normalizedX.coerceIn(0f, 1f) * fieldRect.width()
    }

    private fun toPixelY(normalizedY: Float): Float {
        return fieldRect.top + normalizedY.coerceIn(0f, 1f) * fieldRect.height()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        // Close to the coach board on a landscape tablet, so cards are not visibly stretched.
        const val ASPECT_RATIO = 1.6f
    }
}

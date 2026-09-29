package com.cloud9.gridsync

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.cloud9.gridsync.network.SessionLogManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Shows the session activity log grouped by date:
 *
 *   Today
 *     Session 1
 *     Session 2
 *   Yesterday
 *     Session 1
 *   September 25, 2026
 *     Session 1
 *
 * Tap a session to expand it. Use the trash icon to delete one session, or
 * "Clear all" to delete everything.
 */
class SessionLogActivity : SwipeBackActivity(), SessionLogManager.SessionLogListener {

    private lateinit var logContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var clearAllButton: TextView

    private val expandedSessionIds = mutableSetOf<Long>()
    private var autoExpandedCurrent = false

    private val dayKeyFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val fullDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    private val entryTimeFormat = SimpleDateFormat("h:mm:ss a", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_log)

        SessionLogManager.init(applicationContext)

        logContainer = findViewById(R.id.logContainer)
        emptyText = findViewById(R.id.emptyText)
        clearAllButton = findViewById(R.id.clearAllButton)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { goBack() }

        clearAllButton.setOnClickListener { confirmClearAll() }
    }

    override fun onStart() {
        super.onStart()
        SessionLogManager.addListener(this)
    }

    override fun onStop() {
        SessionLogManager.removeListener(this)
        super.onStop()
    }

    override fun onSessionLogChanged(entries: List<String>) {
        render()
    }

    // ---- Rendering ---------------------------------------------------------

    private fun render() {
        val sessions = SessionLogManager.getSessions()

        if (!autoExpandedCurrent) {
            SessionLogManager.getCurrentSessionId()?.let { expandedSessionIds.add(it) }
            autoExpandedCurrent = true
        }

        logContainer.removeAllViews()

        if (sessions.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            clearAllButton.visibility = View.GONE
            return
        }

        emptyText.visibility = View.GONE
        clearAllButton.visibility = View.VISIBLE

        // Group by calendar day, newest day first. Inside a day: Session 1, 2, 3...
        val byDay = sessions
            .groupBy { dayKeyFormat.format(Date(it.startTime)) }
            .toSortedMap(compareByDescending<String> { it })

        val todayKey = dayKeyFormat.format(Date())
        val yesterdayKey = dayKeyFormat.format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
        )

        byDay.forEach { (dayKey, daySessions) ->
            val label = when (dayKey) {
                todayKey -> "Today"
                yesterdayKey -> "Yesterday"
                else -> fullDateFormat.format(Date(daySessions.first().startTime))
            }

            logContainer.addView(buildDateHeader(label))

            daySessions.sortedBy { it.startTime }.forEachIndexed { index, session ->
                logContainer.addView(buildSessionCard(session, index + 1, label))
            }
        }
    }

    private fun buildDateHeader(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF14213D.toInt())
            setPadding(dpToPx(4), dpToPx(22), 0, dpToPx(8))
        }
    }

    private fun buildSessionCard(
        session: SessionLogManager.LogSession,
        number: Int,
        dateLabel: String
    ): View {
        val expanded = session.id in expandedSessionIds

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_background)
            elevation = dpToPx(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(10) }
        }

        // Header row: title/subtitle, delete button, expand chevron
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(8), dpToPx(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (session.id in expandedSessionIds) {
                    expandedSessionIds.remove(session.id)
                } else {
                    expandedSessionIds.add(session.id)
                }
                render()
            }
        }

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        titles.addView(TextView(this).apply {
            text = "Session $number"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF1B263B.toInt())
        })

        val count = session.entries.size
        titles.addView(TextView(this).apply {
            text = "Started ${timeFormat.format(Date(session.startTime))}  \u2022  " +
                    "$count ${if (count == 1) "event" else "events"}"
            textSize = 13f
            setTextColor(0xFF52606D.toInt())
        })

        val deleteButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete)
            setColorFilter(0xFF7B8794.toInt())
            val ripple = obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            )
            background = ripple.getDrawable(0)
            ripple.recycle()
            contentDescription = "Delete Session $number"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
            layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44))
            setOnClickListener { confirmDeleteSession(session, number, dateLabel) }
        }

        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(0xFF9AA5B1.toInt())
            rotation = if (expanded) 90f else 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)).apply {
                marginStart = dpToPx(2)
                marginEnd = dpToPx(4)
            }
        }

        header.addView(titles)
        header.addView(deleteButton)
        header.addView(chevron)
        card.addView(header)

        if (expanded) {
            card.addView(View(this).apply {
                setBackgroundColor(0xFFE4EAF1.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(1)
                )
            })

            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(12))
            }

            if (session.entries.isEmpty()) {
                body.addView(TextView(this).apply {
                    text = "No events"
                    textSize = 14f
                    setTextColor(0xFF6B7A8C.toInt())
                })
            } else {
                // Newest first, like the live log.
                session.entries.asReversed().forEach { entry ->
                    body.addView(buildEntryRow(entry))
                }
            }
            card.addView(body)
        }

        return card
    }

    private fun buildEntryRow(entry: SessionLogManager.LogEntry): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(5), 0, dpToPx(5))

            addView(TextView(this@SessionLogActivity).apply {
                text = entryTimeFormat.format(Date(entry.time))
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setTextColor(0xFF7B8794.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(92),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })

            addView(TextView(this@SessionLogActivity).apply {
                text = entry.message
                textSize = 15f
                setTextColor(0xFF1B263B.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            })
        }
    }

    // ---- Deleting ----------------------------------------------------------

    private fun confirmDeleteSession(
        session: SessionLogManager.LogSession,
        number: Int,
        dateLabel: String
    ) {
        AlertDialog.Builder(this)
            .setTitle("Delete Session $number?")
            .setMessage("This removes Session $number from $dateLabel. This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                expandedSessionIds.remove(session.id)
                SessionLogManager.deleteSession(session.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("Clear all logs?")
            .setMessage("This deletes every session in the activity log. This can't be undone.")
            .setPositiveButton("Clear all") { _, _ ->
                expandedSessionIds.clear()
                SessionLogManager.deleteAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

package com.cloud9.gridsync

import android.app.Activity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PlayPersonnel
import com.cloud9.gridsync.ui.PlayPreviewView

// A larger look at one play card, shared by the Play Library and Hurry-Up Package screens.
object PlayDetailDialog {

    fun show(
        activity: Activity,
        play: PlayMessage,
        onSend: (PlayMessage) -> Unit,
        onEdit: ((PlayMessage) -> Unit)? = null
    ) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val preview = PlayPreviewView(activity).apply { setPlay(play) }
        val details = TextView(activity).apply {
            val personnel = PlayPersonnel.describe(play)
            text = listOfNotNull(
                PlayCardAdapter.formationLabel(play),
                play.playType,
                personnel.label,
                personnel.composition
            ).joinToString("  ·  ")
            textSize = 15f
            setPadding(0, dp(12), 0, 0)
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(
                preview,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(details)
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle(play.playName)
            .setView(content)
            .setPositiveButton("Send") { _, _ -> onSend(play) }
            .setNegativeButton("Close", null)

        if (onEdit != null) {
            builder.setNeutralButton("Edit") { _, _ -> onEdit(play) }
        }

        val dialog = builder.create()
        dialog.show()

        // Wide enough for the diagram to be readable on a tablet.
        val width = (activity.resources.displayMetrics.widthPixels * 0.7f).toInt().coerceAtLeast(dp(320))
        dialog.window?.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
}

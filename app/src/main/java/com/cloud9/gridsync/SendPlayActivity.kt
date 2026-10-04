package com.cloud9.gridsync

import android.content.res.ColorStateList
import android.view.Gravity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cloud9.gridsync.network.ConnectedWatch
import com.cloud9.gridsync.network.RoleRepository
import com.cloud9.gridsync.network.RoleStatusInfo
import com.cloud9.gridsync.network.SessionLogManager
import com.cloud9.gridsync.network.TabletServerManager

class SendPlayActivity : AppCompatActivity(), TabletServerManager.WatchListListener {

    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var roleStatusContainer: LinearLayout

    private var roles: List<String> = emptyList()

    private data class RoleRowViews(
        val statusText: TextView,
        val checkBox: CheckBox
    )

    private val roleRowMap = linkedMapOf<String, RoleRowViews>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_play)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        roleStatusContainer = findViewById(R.id.roleStatusContainer)

        roles = RoleRepository.getRoles(this).map { it.trim() }

        backButton.setOnClickListener {
            finish()
        }

        buildRoleRows()
        updateStatuses()

        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()

            if (message.isEmpty()) {
                Toast.makeText(this@SendPlayActivity, "Enter a message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedRoles = roleRowMap
                .filterValues { it.checkBox.isChecked }
                .keys
                .toList()

            if (selectedRoles.isEmpty()) {
                Toast.makeText(this@SendPlayActivity, "Select at least one role", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val connectedRoles = TabletServerManager.getConnectedRoles()

            val delivered = mutableListOf<String>()
            val skipped = mutableListOf<String>()

            selectedRoles.forEach { role ->
                if (connectedRoles.contains(role)) {
                    TabletServerManager.sendToRole(role, message)
                    delivered.add(role)
                } else {
                    skipped.add(role)
                }
            }

            if (delivered.isNotEmpty()) {
                SessionLogManager.addEntry("Coach message sent to ${delivered.joinToString(", ")}")
            }

            val resultText = buildString {
                if (delivered.isNotEmpty()) {
                    append("Sent to ${delivered.joinToString(", ")}")
                }
                if (skipped.isNotEmpty()) {
                    if (isNotEmpty()) append("  ")
                    append("Skipped ${skipped.joinToString(", ")}")
                }
            }

            Toast.makeText(this@SendPlayActivity, resultText, Toast.LENGTH_LONG).show()
            messageInput.setText("")
            updateStatuses()
        }
    }

    override fun onStart() {
        super.onStart()
        TabletServerManager.addListener(this)
        updateStatuses()
    }

    override fun onStop() {
        super.onStop()
        TabletServerManager.removeListener(this)
    }

    override fun onWatchListChanged(watches: List<ConnectedWatch>) {
        runOnUiThread {
            updateStatuses()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatuses()
    }

    private fun buildRoleRows() {
        roleStatusContainer.removeAllViews()
        roleRowMap.clear()

        roles.forEachIndexed { index, role ->
            if (index > 0) {
                roleStatusContainer.addView(View(this).apply {
                    setBackgroundColor(color(R.color.gridsync_divider))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                })
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val roleText = TextView(this).apply {
                text = role
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(color(R.color.gridsync_text_primary))
                layoutParams = LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val statusText = TextView(this).apply {
                text = "Unassigned"
                textSize = 15f
                setTextColor(color(R.color.gridsync_text_muted))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val checkBox = CheckBox(this).apply {
                isChecked = false
                buttonTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(color(R.color.gridsync_green_bright), color(R.color.gridsync_text_muted))
                )
            }

            row.addView(roleText)
            row.addView(statusText)
            row.addView(checkBox)

            roleStatusContainer.addView(row)
            roleRowMap[role] = RoleRowViews(statusText, checkBox)
        }
    }

    private fun updateStatuses() {
        val statusMap = TabletServerManager.getRoleStatuses(roles).associateBy { it.role }

        roles.forEach { role ->
            val info = statusMap[role]
            val row = roleRowMap[role] ?: return@forEach
            applyRoleStatus(row.statusText, info)
        }
    }

    private fun applyRoleStatus(textView: TextView, info: RoleStatusInfo?) {
        val status = info?.status ?: "Unassigned"
        textView.text = status

        textView.setTextColor(
            color(
                when (status) {
                    "Online" -> R.color.gridsync_green_bright
                    "Connecting" -> R.color.gridsync_amber
                    "Offline" -> R.color.gridsync_text_secondary
                    else -> R.color.gridsync_text_muted
                }
            )
        )
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
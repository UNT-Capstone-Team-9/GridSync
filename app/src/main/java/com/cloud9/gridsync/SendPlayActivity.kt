package com.cloud9.gridsync

import android.graphics.Color
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

        roles.forEach { role ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val roleText = TextView(this).apply {
                text = role
                textSize = 15f
                setTextColor(Color.parseColor("#14213D"))
                layoutParams = LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val statusText = TextView(this).apply {
                text = "Unassigned"
                textSize = 15f
                setTextColor(Color.parseColor("#1F2937"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val checkBox = CheckBox(this).apply {
                isChecked = false
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
            when (status) {
                "Online" -> Color.parseColor("#0F9D58")
                "Connecting" -> Color.parseColor("#F59E0B")
                "Offline" -> Color.parseColor("#7B8794")
                else -> Color.parseColor("#1F2937")
            }
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
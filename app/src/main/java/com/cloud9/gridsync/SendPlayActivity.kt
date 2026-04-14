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

    private lateinit var statusQB: TextView
    private lateinit var statusWR1: TextView
    private lateinit var statusWR2: TextView
    private lateinit var statusRB: TextView
    private lateinit var statusTE: TextView
    private lateinit var statusLT: TextView
    private lateinit var statusLG: TextView
    private lateinit var statusC: TextView
    private lateinit var statusRG: TextView
    private lateinit var statusRT: TextView
    private lateinit var statusCB: TextView

    private lateinit var checkQB: CheckBox
    private lateinit var checkWR1: CheckBox
    private lateinit var checkWR2: CheckBox
    private lateinit var checkRB: CheckBox
    private lateinit var checkTE: CheckBox
    private lateinit var checkLT: CheckBox
    private lateinit var checkLG: CheckBox
    private lateinit var checkC: CheckBox
    private lateinit var checkRG: CheckBox
    private lateinit var checkRT: CheckBox
    private lateinit var checkCB: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_play)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        roleStatusContainer = findViewById(R.id.roleStatusContainer)

        statusQB = findViewById(R.id.statusQB)
        statusWR1 = findViewById(R.id.statusWR1)
        statusWR2 = findViewById(R.id.statusWR2)
        statusRB = findViewById(R.id.statusRB)
        statusTE = findViewById(R.id.statusTE)
        statusLT = findViewById(R.id.statusLT)
        statusLG = findViewById(R.id.statusLG)
        statusC = findViewById(R.id.statusC)
        statusRG = findViewById(R.id.statusRG)
        statusRT = findViewById(R.id.statusRT)
        statusCB = findViewById(R.id.statusCB)

        checkQB = findViewById(R.id.checkQB)
        checkWR1 = findViewById(R.id.checkWR1)
        checkWR2 = findViewById(R.id.checkWR2)
        checkRB = findViewById(R.id.checkRB)
        checkTE = findViewById(R.id.checkTE)
        checkLT = findViewById(R.id.checkLT)
        checkLG = findViewById(R.id.checkLG)
        checkC = findViewById(R.id.checkC)
        checkRG = findViewById(R.id.checkRG)
        checkRT = findViewById(R.id.checkRT)
        checkCB = findViewById(R.id.checkCB)

        buildRoleRows()
        updateStatuses()

        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()

            if (message.isEmpty()) {
                Toast.makeText(this@SendPlayActivity, "Enter a message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedRoles = mutableSetOf<String>()
            if (checkQB.isChecked) selectedRoles.add("QB")
            if (checkWR1.isChecked) selectedRoles.add("WR1")
            if (checkWR2.isChecked) selectedRoles.add("WR2")
            if (checkRB.isChecked) selectedRoles.add("RB")
            if (checkTE.isChecked) selectedRoles.add("TE")
            if (checkLT.isChecked) selectedRoles.add("LT")
            if (checkLG.isChecked) selectedRoles.add("LG")
            if (checkC.isChecked) selectedRoles.add("C")
            if (checkRG.isChecked) selectedRoles.add("RG")
            if (checkRT.isChecked) selectedRoles.add("RT")
            if (checkCB.isChecked) selectedRoles.add("CB")

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

            Toast.makeText(this, resultText, Toast.LENGTH_LONG).show()
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

        setRoleStatus(statusQB, connectedRoles.contains("QB"))
        setRoleStatus(statusWR1, connectedRoles.contains("WR1"))
        setRoleStatus(statusWR2, connectedRoles.contains("WR2"))
        setRoleStatus(statusRB, connectedRoles.contains("RB"))
        setRoleStatus(statusTE, connectedRoles.contains("TE"))
        setRoleStatus(statusLT, connectedRoles.contains("LT"))
        setRoleStatus(statusLG, connectedRoles.contains("LG"))
        setRoleStatus(statusC, connectedRoles.contains("C"))
        setRoleStatus(statusRG, connectedRoles.contains("RG"))
        setRoleStatus(statusRT, connectedRoles.contains("RT"))
        setRoleStatus(statusCB, connectedRoles.contains("CB"))
    }

    private fun setRoleStatus(textView: TextView, connected: Boolean) {
        if (connected) {
            textView.text = "• Connected"
            textView.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            textView.text = "◦ Disconnected"
            textView.setTextColor(Color.parseColor("#B00020"))
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
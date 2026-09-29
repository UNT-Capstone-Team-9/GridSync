package com.cloud9.gridsync

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.cloud9.gridsync.network.ConnectedWatch
import com.cloud9.gridsync.network.SessionLogManager
import com.cloud9.gridsync.network.TabletServerManager

class SendPlayActivity : SwipeBackActivity(), TabletServerManager.WatchListListener {

    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

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

        // Some tablet layouts do not include a backButton. Do not crash if it is absent.
        findViewById<ImageButton?>(R.id.backButton)?.setOnClickListener { goBack() }
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

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

        updateStatuses()

        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isEmpty()) {
                Toast.makeText(this, "Enter a message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedRoles = buildSet {
                if (checkQB.isChecked) add("QB")
                if (checkWR1.isChecked) add("WR1")
                if (checkWR2.isChecked) add("WR2")
                if (checkRB.isChecked) add("RB")
                if (checkTE.isChecked) add("TE")
                if (checkLT.isChecked) add("LT")
                if (checkLG.isChecked) add("LG")
                if (checkC.isChecked) add("C")
                if (checkRG.isChecked) add("RG")
                if (checkRT.isChecked) add("RT")
                if (checkCB.isChecked) add("CB")
            }

            if (selectedRoles.isEmpty()) {
                Toast.makeText(this, "Select at least one role", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val connectedRoles = TabletServerManager.getConnectedRoles()
            val delivered = mutableListOf<String>()
            val skipped = mutableListOf<String>()

            selectedRoles.forEach { role ->
                if (role in connectedRoles) {
                    TabletServerManager.sendToRole(role, message)
                    delivered += role
                } else {
                    skipped += role
                }
            }

            if (delivered.isNotEmpty()) {
                SessionLogManager.addEntry("Coach message sent to ${delivered.joinToString(", ")}")
            }

            val resultText = buildString {
                if (delivered.isNotEmpty()) append("Sent to ${delivered.joinToString(", ")}")
                if (skipped.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append("Skipped ${skipped.joinToString(", ")} (not connected)")
                }
            }

            Toast.makeText(this, resultText, Toast.LENGTH_LONG).show()
            if (delivered.isNotEmpty()) messageInput.setText("")
            updateStatuses()
        }
    }

    override fun onStart() {
        super.onStart()
        TabletServerManager.addListener(this)
        updateStatuses()
    }

    override fun onStop() {
        TabletServerManager.removeListener(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateStatuses()
    }

    override fun onWatchListChanged(watches: List<ConnectedWatch>) {
        runOnUiThread { updateStatuses() }
    }

    private fun updateStatuses() {
        val connectedRoles = TabletServerManager.getConnectedRoles()
        setRoleStatus(statusQB, "QB" in connectedRoles)
        setRoleStatus(statusWR1, "WR1" in connectedRoles)
        setRoleStatus(statusWR2, "WR2" in connectedRoles)
        setRoleStatus(statusRB, "RB" in connectedRoles)
        setRoleStatus(statusTE, "TE" in connectedRoles)
        setRoleStatus(statusLT, "LT" in connectedRoles)
        setRoleStatus(statusLG, "LG" in connectedRoles)
        setRoleStatus(statusC, "C" in connectedRoles)
        setRoleStatus(statusRG, "RG" in connectedRoles)
        setRoleStatus(statusRT, "RT" in connectedRoles)
        setRoleStatus(statusCB, "CB" in connectedRoles)
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
}

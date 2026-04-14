package com.cloud9.gridsync

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.cloud9.gridsync.network.TabletServerManager

class SendPlayActivity : AppCompatActivity() {

    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    private lateinit var statusQB: TextView
    private lateinit var statusWR1: TextView
    private lateinit var statusWR2: TextView
    private lateinit var statusRB: TextView
    private lateinit var statusTE: TextView

    private lateinit var checkQB: CheckBox
    private lateinit var checkWR1: CheckBox
    private lateinit var checkWR2: CheckBox
    private lateinit var checkRB: CheckBox
    private lateinit var checkTE: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_play)

        // Start server (IMPORTANT)
        TabletServerManager.start(applicationContext)

        // UI
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        statusQB = findViewById(R.id.statusQB)
        statusWR1 = findViewById(R.id.statusWR1)
        statusWR2 = findViewById(R.id.statusWR2)
        statusRB = findViewById(R.id.statusRB)
        statusTE = findViewById(R.id.statusTE)

        checkQB = findViewById(R.id.checkQB)
        checkWR1 = findViewById(R.id.checkWR1)
        checkWR2 = findViewById(R.id.checkWR2)
        checkRB = findViewById(R.id.checkRB)
        checkTE = findViewById(R.id.checkTE)

        // Update status every second
        updateStatuses()

        sendButton.setOnClickListener {
            sendPlay()
        }
    }

    private fun updateStatuses() {
        val handler = android.os.Handler(mainLooper)

        handler.post(object : Runnable {
            override fun run() {

                val connected = TabletServerManager.getConnectedRoles()

                updateStatus(statusQB, connected.contains("QB"))
                updateStatus(statusWR1, connected.contains("WR1"))
                updateStatus(statusWR2, connected.contains("WR2"))
                updateStatus(statusRB, connected.contains("RB"))
                updateStatus(statusTE, connected.contains("TE"))

                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun updateStatus(view: TextView, isConnected: Boolean) {
        if (isConnected) {
            view.text = "● Connected"
            view.setTextColor(resources.getColor(android.R.color.holo_green_dark))
        } else {
            view.text = "○ Disconnected"
            view.setTextColor(resources.getColor(android.R.color.holo_red_dark))
        }
    }

    private fun sendPlay() {
        val message = messageInput.text.toString().trim()

        if (message.isEmpty()) {
            toast("Enter a message")
            return
        }

        val selected = mutableListOf<String>()

        if (checkQB.isChecked) selected.add("QB")
        if (checkWR1.isChecked) selected.add("WR1")
        if (checkWR2.isChecked) selected.add("WR2")
        if (checkRB.isChecked) selected.add("RB")
        if (checkTE.isChecked) selected.add("TE")

        if (selected.isEmpty()) {
            toast("Select at least one role")
            return
        }

        val connected = TabletServerManager.getConnectedRoles()

        val sent = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (role in selected) {
            if (connected.contains(role)) {
                TabletServerManager.sendToRole(role, message)
                sent.add(role)
            } else {
                skipped.add(role)
            }
        }

        val result = buildString {
            if (sent.isNotEmpty()) {
                append("Sent to: ${sent.joinToString(", ")}\n")
            }
            if (skipped.isNotEmpty()) {
                append("Skipped (not connected): ${skipped.joinToString(", ")}")
            }
        }

        toast(result)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
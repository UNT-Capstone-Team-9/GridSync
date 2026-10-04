package com.cloud9.gridsync

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.database.HurryUpPackageSummary
import com.cloud9.gridsync.database.HurryUpRepository
import kotlin.concurrent.thread

class HurryUpPackagesActivity : AppCompatActivity() {

    private lateinit var adapter: HurryUpPackageAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hurry_up_packages)

        recyclerView = findViewById(R.id.packageRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.newPackageButton).setOnClickListener {
            HurryUpDialogs.startCreatePackageFlow(this) { _, _ -> loadPackages() }
        }

        adapter = HurryUpPackageAdapter(
            onOpen = { openPackage(it) },
            onMenu = { view, summary -> showPackageMenu(view, summary) }
        )
        recyclerView.layoutManager = GridLayoutManager(this, (resources.configuration.screenWidthDp / 300).coerceIn(1, 4))
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadPackages()
    }

    private fun loadPackages() {
        thread {
            val summaries = HurryUpRepository.getSummaries(applicationContext)
            runOnUiThread {
                adapter.replaceAll(summaries)
                emptyStateText.visibility = if (summaries.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (summaries.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun openPackage(summary: HurryUpPackageSummary) {
        startActivity(
            Intent(this, HurryUpPackageDetailActivity::class.java)
                .putExtra(HurryUpPackageDetailActivity.EXTRA_PACKAGE_ID, summary.packageId)
        )
    }

    private fun showPackageMenu(anchor: View, summary: HurryUpPackageSummary) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Open")
        popup.menu.add("Rename")
        popup.menu.add("Delete")
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "Open" -> openPackage(summary)
                "Rename" -> HurryUpDialogs.renamePackage(this, summary.packageId, summary.packageName) {
                    loadPackages()
                }
                "Delete" -> HurryUpDialogs.confirmDeletePackage(this, summary.packageId, summary.packageName) {
                    loadPackages()
                }
            }
            true
        }
        popup.show()
    }
}

package com.cloud9.gridsync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.database.HurryUpPackageSummary

class HurryUpPackageAdapter(
    private val onOpen: (HurryUpPackageSummary) -> Unit,
    private val onMenu: (View, HurryUpPackageSummary) -> Unit
) : RecyclerView.Adapter<HurryUpPackageAdapter.PackageViewHolder>() {

    private val packages = mutableListOf<HurryUpPackageSummary>()

    class PackageViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_hurry_up_package, parent, false)
    ) {
        val nameText: TextView = itemView.findViewById(R.id.packageNameText)
        val countText: TextView = itemView.findViewById(R.id.playCountText)
        val openButton: Button = itemView.findViewById(R.id.openButton)
        val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PackageViewHolder(parent)

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        val summary = packages[position]
        holder.nameText.text = summary.packageName
        holder.countText.text = if (summary.playCount == 1) "1 play" else "${summary.playCount} plays"
        holder.itemView.setOnClickListener { onOpen(summary) }
        holder.openButton.setOnClickListener { onOpen(summary) }
        holder.menuButton.setOnClickListener { onMenu(it, summary) }
    }

    override fun getItemCount(): Int = packages.size

    fun replaceAll(newPackages: List<HurryUpPackageSummary>) {
        packages.clear()
        packages.addAll(newPackages)
        notifyDataSetChanged()
    }
}

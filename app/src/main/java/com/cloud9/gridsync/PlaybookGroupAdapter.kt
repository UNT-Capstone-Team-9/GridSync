package com.cloud9.gridsync

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Formation or personnel groups built from the coach's saved plays.
class PlaybookGroupAdapter(
    private val onOpen: (Group) -> Unit
) : RecyclerView.Adapter<PlaybookGroupAdapter.GroupViewHolder>() {

    data class Group(val name: String, val playCount: Int)

    private val groups = mutableListOf<Group>()

    class GroupViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_playbook_group, parent, false)
    ) {
        val nameText: TextView = itemView.findViewById(R.id.groupNameText)
        val countText: TextView = itemView.findViewById(R.id.groupCountText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = GroupViewHolder(parent)

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.nameText.text = group.name
        holder.countText.text = if (group.playCount == 1) "1 play" else "${group.playCount} plays"
        holder.itemView.setOnClickListener { onOpen(group) }
    }

    override fun getItemCount(): Int = groups.size

    fun replaceAll(newGroups: List<Group>) {
        groups.clear()
        groups.addAll(newGroups)
        notifyDataSetChanged()
    }
}

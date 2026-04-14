package com.cloud9.gridsync

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.network.PlayMessage

class TrashListAdapter(
    private val plays: MutableList<PlayMessage>,
    private val onRestoreClicked: (PlayMessage) -> Unit,
    private val onDeleteForeverClicked: (PlayMessage) -> Unit
) : RecyclerView.Adapter<TrashListAdapter.TrashViewHolder>() {

    class TrashViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_trash_play, parent, false)
    ) {
        val titleText: TextView = itemView.findViewById(R.id.playTitleText)
        val restoreButton: Button = itemView.findViewById(R.id.btnRestorePlay)
        val deleteForeverButton: Button = itemView.findViewById(R.id.btnDeleteForever)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        return TrashViewHolder(parent)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        val play = plays[position]
        holder.titleText.text = play.playName

        holder.restoreButton.setOnClickListener {
            onRestoreClicked(play)
        }

        holder.deleteForeverButton.setOnClickListener {
            onDeleteForeverClicked(play)
        }
    }

    override fun getItemCount(): Int = plays.size

    fun replaceAll(newPlays: List<PlayMessage>) {
        plays.clear()
        plays.addAll(newPlays)
        notifyDataSetChanged()
    }
}
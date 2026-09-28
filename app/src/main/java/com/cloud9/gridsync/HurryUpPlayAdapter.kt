package com.cloud9.gridsync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.network.PlayMessage

class HurryUpPlayAdapter(
    private val onSend: (PlayMessage) -> Unit,
    private val onMenu: (View, PlayMessage) -> Unit
) : RecyclerView.Adapter<HurryUpPlayAdapter.PlayViewHolder>() {

    private val plays = mutableListOf<PlayMessage>()

    class PlayViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_hurry_up_play, parent, false)
    ) {
        val orderText: TextView = itemView.findViewById(R.id.orderText)
        val nameText: TextView = itemView.findViewById(R.id.playNameText)
        val detailText: TextView = itemView.findViewById(R.id.playDetailText)
        val sendButton: Button = itemView.findViewById(R.id.sendButton)
        val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PlayViewHolder(parent)

    override fun onBindViewHolder(holder: PlayViewHolder, position: Int) {
        val play = plays[position]
        holder.orderText.text = "${position + 1}."
        holder.nameText.text = play.playName

        val details = listOfNotNull(
            play.formationName?.takeIf { it.isNotBlank() },
            play.playType?.takeIf { it.isNotBlank() }
        ).joinToString(" | ")
        holder.detailText.text = details
        holder.detailText.visibility = if (details.isBlank()) View.GONE else View.VISIBLE

        holder.sendButton.setOnClickListener { onSend(play) }
        holder.menuButton.setOnClickListener { onMenu(it, play) }
    }

    override fun getItemCount(): Int = plays.size

    fun replaceAll(newPlays: List<PlayMessage>) {
        plays.clear()
        plays.addAll(newPlays)
        notifyDataSetChanged()
    }
}

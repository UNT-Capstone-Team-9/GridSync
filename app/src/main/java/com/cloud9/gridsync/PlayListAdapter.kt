package com.cloud9.gridsync

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.network.PlayMessage

class PlayListAdapter(
    private val plays: MutableList<PlayMessage>,
    private val onPlayClicked: (PlayMessage) -> Unit,
    private val onEditClicked: (PlayMessage) -> Unit,
    private val onDeleteClicked: (PlayMessage) -> Unit
) : RecyclerView.Adapter<PlayListAdapter.PlayViewHolder>() {

    private var selectedPlayName: String? = null

    class PlayViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_play_library, parent, false)
    ) {
        val titleText: TextView = itemView.findViewById(R.id.playTitleText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayViewHolder {
        return PlayViewHolder(parent)
    }

    override fun onBindViewHolder(holder: PlayViewHolder, position: Int) {
        val play = plays[position]
        holder.titleText.text = play.playName

        if (play.playName == selectedPlayName) {
            holder.titleText.setBackgroundResource(R.drawable.button_background_selected)
        } else {
            holder.titleText.setBackgroundResource(R.drawable.button_background)
        }

        holder.titleText.setOnClickListener {
            selectedPlayName = play.playName
            notifyDataSetChanged()
            onPlayClicked(play)
        }

        holder.titleText.setOnLongClickListener { view ->
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.menu.add("Edit")
            popupMenu.menu.add("Delete")

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Edit" -> {
                        onEditClicked(play)
                        true
                    }

                    "Delete" -> {
                        onDeleteClicked(play)
                        true
                    }

                    else -> false
                }
            }

            popupMenu.show()
            true
        }
    }

    override fun getItemCount(): Int = plays.size

    fun replaceAll(newPlays: List<PlayMessage>) {
        plays.clear()
        plays.addAll(newPlays)

        if (selectedPlayName != null && plays.none { it.playName == selectedPlayName }) {
            selectedPlayName = null
        }

        notifyDataSetChanged()
    }

    fun getSelectedPlay(): PlayMessage? {
        return plays.firstOrNull { it.playName == selectedPlayName }
    }
}
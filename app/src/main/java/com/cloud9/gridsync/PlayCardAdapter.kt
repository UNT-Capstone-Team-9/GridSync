package com.cloud9.gridsync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cloud9.gridsync.network.PlayFormation
import com.cloud9.gridsync.network.PlayMessage
import com.cloud9.gridsync.network.PlayPersonnel
import com.cloud9.gridsync.ui.PlayPreviewView

// Visual play cards for the Play Library and Hurry-Up Package screens. Each card draws the saved
// PlayMessage itself; the screens only decide which actions the card offers.
class PlayCardAdapter(
    private val showEdit: Boolean,
    private val showOrder: Boolean = false,
    private val onOpen: (PlayMessage) -> Unit,
    private val onSend: (PlayMessage) -> Unit,
    private val onEdit: (PlayMessage) -> Unit = {},
    private val onMenu: (View, PlayMessage) -> Unit
) : RecyclerView.Adapter<PlayCardAdapter.CardViewHolder>() {

    private val plays = mutableListOf<PlayMessage>()

    class CardViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_play_card, parent, false)
    ) {
        val preview: PlayPreviewView = itemView.findViewById(R.id.playPreview)
        val typeBadge: TextView = itemView.findViewById(R.id.playTypeBadge)
        val orderBadge: TextView = itemView.findViewById(R.id.orderBadge)
        val nameText: TextView = itemView.findViewById(R.id.playNameText)
        val formationText: TextView = itemView.findViewById(R.id.formationText)
        val personnelText: TextView = itemView.findViewById(R.id.personnelText)
        val sendButton: TextView = itemView.findViewById(R.id.sendButton)
        val editButton: TextView = itemView.findViewById(R.id.editButton)
        val menuButton: TextView = itemView.findViewById(R.id.menuButton)

        init {
            // Clips the diagram to the card's rounded corners.
            itemView.clipToOutline = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = CardViewHolder(parent)

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val play = plays[position]

        holder.preview.setPlay(play)
        holder.nameText.text = play.playName
        holder.formationText.text = formationLabel(play)
        holder.personnelText.text = PlayPersonnel.describe(play).let { info ->
            listOfNotNull(info.label, info.composition).joinToString(" · ")
        }

        when (play.playType) {
            PlayFormation.PLAY_TYPE_RUN -> {
                holder.typeBadge.text = "RUN"
                holder.typeBadge.setBackgroundResource(R.drawable.playbook_badge_run)
            }
            PlayFormation.PLAY_TYPE_PASS -> {
                holder.typeBadge.text = "PASS"
                holder.typeBadge.setBackgroundResource(R.drawable.playbook_badge_pass)
            }
            else -> {
                holder.typeBadge.text = "PLAY"
                holder.typeBadge.setBackgroundResource(R.drawable.playbook_badge_unknown)
            }
        }

        holder.orderBadge.visibility = if (showOrder) View.VISIBLE else View.GONE
        holder.orderBadge.text = "${position + 1}"

        holder.editButton.visibility = if (showEdit) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onOpen(play) }
        holder.sendButton.setOnClickListener { onSend(play) }
        holder.editButton.setOnClickListener { onEdit(play) }
        holder.menuButton.setOnClickListener { onMenu(it, play) }
    }

    override fun getItemCount(): Int = plays.size

    fun replaceAll(newPlays: List<PlayMessage>) {
        plays.clear()
        plays.addAll(newPlays)
        notifyDataSetChanged()
    }

    companion object {
        const val NO_FORMATION = "No formation"

        fun formationLabel(play: PlayMessage): String {
            return play.formationName?.trim()?.takeIf { it.isNotEmpty() } ?: NO_FORMATION
        }
    }
}

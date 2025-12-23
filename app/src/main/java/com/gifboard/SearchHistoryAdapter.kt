package com.gifboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying search history items.
 */
class SearchHistoryAdapter(
    private val onItemClick: (String) -> Unit,
    private val onDismissClick: (String) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.HistoryViewHolder>() {

    private val history = mutableListOf<String>()

    fun setHistory(items: List<String>) {
        history.clear()
        history.addAll(items)
        notifyDataSetChanged()
    }

    fun removeItem(query: String) {
        val index = history.indexOf(query)
        if (index >= 0) {
            history.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun clear() {
        history.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_history_item, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val query = history[position]
        holder.bind(query)
    }

    override fun getItemCount() = history.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val queryText: TextView = itemView.findViewById(R.id.history_query)
        private val dismissButton: ImageButton = itemView.findViewById(R.id.history_dismiss)

        fun bind(query: String) {
            queryText.text = query
            
            itemView.setOnClickListener {
                onItemClick(query)
            }
            
            dismissButton.setOnClickListener {
                onDismissClick(query)
            }
        }
    }
}

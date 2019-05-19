package com.infinitysolutions.notessync.Adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infinitysolutions.notessync.Model.NoteDisplayItem
import com.infinitysolutions.notessync.R
import kotlinx.android.synthetic.main.notes_display_item.view.*

class NotesAdapter(val items: List<NoteDisplayItem>,val context: Context): RecyclerView.Adapter<NotesAdapter.ViewHolder>(){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.notes_display_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.titleTextView.text = items.get(position).noteTitle
        holder.dateModifiedTextView.text = items.get(position).dateModified.toString()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val titleTextView = itemView.title_text
        val dateModifiedTextView = itemView.date_modified_text
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
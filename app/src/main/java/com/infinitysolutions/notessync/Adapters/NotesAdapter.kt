package com.infinitysolutions.notessync.Adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.notes_list_item.view.*

class NotesAdapter(val mainViewModel: MainViewModel, val items: List<Note>, val context: Context): RecyclerView.Adapter<NotesAdapter.ViewHolder>(){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.notes_list_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.titleTextView.text = items.get(position).noteTitle
        holder.contentTextView.text = items.get(position).noteContent
        holder.dateModifiedTextView.text = items.get(position).dateModified.toString()
        holder.itemContainer.setOnClickListener {
            mainViewModel.setSelectedNote(items.get(position))
            mainViewModel.setShouldGoToEditor(true)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val titleTextView = itemView.title_text
        val contentTextView = itemView.content_preview_text
        val dateModifiedTextView = itemView.date_modified_text
        val itemContainer = itemView
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
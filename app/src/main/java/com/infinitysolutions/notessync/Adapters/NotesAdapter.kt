package com.infinitysolutions.notessync.Adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.notes_list_item.view.*
import java.text.SimpleDateFormat
import java.util.*

class NotesAdapter(val mainViewModel: MainViewModel, val items: List<Note>, val context: Context): RecyclerView.Adapter<NotesAdapter.ViewHolder>(){
    private val formatter = SimpleDateFormat("E, MMM d", Locale.ENGLISH)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.notes_list_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.titleTextView.text = items[position].noteTitle
        holder.contentTextView.text = items[position].noteContent
        holder.dateModifiedTextView.text = formatter.format(items[position].dateModified)
        holder.parentView.backgroundTintList = ColorStateList.valueOf(Color.parseColor(items[position].noteColor))

        holder.itemContainer.setOnClickListener {
            mainViewModel.setSelectedNote(items.get(position))
            mainViewModel.setShouldOpenEditor(true)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val titleTextView = itemView.title_text
        val contentTextView = itemView.content_preview_text
        val dateModifiedTextView = itemView.date_modified_text
        val parentView = itemView.parent_view
        val itemContainer = itemView
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
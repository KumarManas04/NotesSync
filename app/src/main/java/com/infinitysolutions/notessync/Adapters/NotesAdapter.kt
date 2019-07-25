package com.infinitysolutions.notessync.Adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.ChecklistGenerator
import com.infinitysolutions.notessync.Util.ColorsUtil
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.notes_list_item.view.*
import java.text.SimpleDateFormat
import java.util.*

class NotesAdapter(private val mainViewModel: MainViewModel, private val items: List<Note>, val context: Context): RecyclerView.Adapter<NotesAdapter.ViewHolder>(){
    private val formatter = SimpleDateFormat("E, MMM d", Locale.ENGLISH)
    private val colorsUtil = ColorsUtil()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.notes_list_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.titleTextView.text = items[position].noteTitle
        holder.dateModifiedTextView.text = formatter.format(items[position].dateModified)
        holder.parentView.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorsUtil.getColor(items[position].noteColor)))

        if (items[position].noteType == LIST_DEFAULT || items[position].noteType == LIST_ARCHIVED){
            holder.indicatorView.setImageResource(R.drawable.todo_indicator)
            val itemsList = ChecklistGenerator.generateList(items[position].noteContent)
            holder.contentTextView.text = itemsList
        }else{
            holder.contentTextView.text = items[position].noteContent
            if (items[position].noteType == NOTE_DEFAULT || items[position].noteType == NOTE_ARCHIVED) {
                holder.indicatorView.setImageResource(R.drawable.note_indicator)
            }
        }

        holder.itemContainer.setOnClickListener{
            mainViewModel.setSelectedNote(items[position])
            mainViewModel.setShouldOpenEditor(true)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val titleTextView: TextView = itemView.title_text
        val contentTextView: TextView = itemView.content_preview_text
        val dateModifiedTextView: TextView = itemView.date_modified_text
        val parentView: ConstraintLayout = itemView.parent_view
        val indicatorView: ImageView = itemView.indicator_view
        val itemContainer = itemView
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
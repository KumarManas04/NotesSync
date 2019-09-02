package com.infinitysolutions.notessync.Adapters

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DELETED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_TRASH
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.ChecklistConverter
import com.infinitysolutions.notessync.Util.ColorsUtil
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.notes_list_item.view.*
import java.text.SimpleDateFormat
import java.util.*

class NotesAdapter(private val mainViewModel: MainViewModel, private val databaseViewModel: DatabaseViewModel, private val items: List<Note>, val context: Context) : RecyclerView.Adapter<NotesAdapter.ViewHolder>() {
    private val formatter = SimpleDateFormat("E, MMM d", Locale.ENGLISH)
    private val colorsUtil = ColorsUtil()
    private val TAG = "NotesAdapter"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.notes_list_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.titleTextView.text = items[position].noteTitle
        holder.dateModifiedTextView.text = formatter.format(items[position].dateModified)
        holder.parentView.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorsUtil.getColor(items[position].noteColor)))

        var noteContent = items[position].noteContent
        if (items[position].noteType == LIST_DEFAULT || items[position].noteType == LIST_ARCHIVED || items[position].noteType == LIST_TRASH) {
            holder.indicatorView.setImageResource(R.drawable.todo_indicator)
            if(noteContent!= null && (noteContent.contains("[ ]") || noteContent.contains("[x]")))
                noteContent = ChecklistConverter.convertList(noteContent)
        } else
            if (items[position].noteType == NOTE_DEFAULT || items[position].noteType == NOTE_ARCHIVED || items[position].noteType == NOTE_TRASH)
                holder.indicatorView.setImageResource(R.drawable.note_indicator)
        holder.contentTextView.text = noteContent

        if (items[position].noteType != NOTE_TRASH && items[position].noteType != LIST_TRASH) {
            holder.itemContainer.setOnClickListener {
                mainViewModel.setSelectedNote(items[position])
                mainViewModel.setShouldOpenEditor(true)
            }
        }

        holder.itemContainer.setOnLongClickListener {
            holder.itemContainer.setOnCreateContextMenuListener { menu, _, _ ->
                if (items[position].noteType != NOTE_TRASH && items[position].noteType != LIST_TRASH) {
                    menu.add("Delete").setOnMenuItemClickListener {
                        if (items[position].noteType == NOTE_DEFAULT || items[position].noteType == NOTE_ARCHIVED)
                            changeNoteType(position, NOTE_TRASH)
                        else
                            changeNoteType(position, LIST_TRASH)
                        Toast.makeText(context, "Moved to trash", LENGTH_SHORT).show()
                        true
                    }
                    if (items[position].noteType == NOTE_DEFAULT || items[position].noteType == LIST_DEFAULT) {
                        menu.add("Archive").setOnMenuItemClickListener {
                            if (items[position].noteType == NOTE_DEFAULT)
                                changeNoteType(position, NOTE_ARCHIVED)
                            else
                                changeNoteType(position, LIST_ARCHIVED)
                            true
                        }
                    } else {
                        menu.add("Unarchive").setOnMenuItemClickListener {
                            if (items[position].noteType == NOTE_ARCHIVED)
                                changeNoteType(position, NOTE_DEFAULT)
                            else
                                changeNoteType(position, LIST_DEFAULT)

                            true
                        }
                    }
                } else {
                    menu.add("Restore").setOnMenuItemClickListener {
                        if (items[position].noteType == NOTE_TRASH)
                            changeNoteType(position, NOTE_DEFAULT)
                        else
                            changeNoteType(position, LIST_DEFAULT)
                        true
                    }
                    menu.add("Delete forever").setOnMenuItemClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Delete forever")
                            .setMessage("Are you sure you want to delete this note forever?")
                            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                                changeNoteType(position, NOTE_DELETED)
                            }
                            .setNegativeButton("No",null)
                            .setCancelable(false)
                            .show()
                        true
                    }
                }
            }
            holder.itemContainer.showContextMenu()
            true
        }
    }

    private fun changeNoteType(position: Int, noteType: Int) {
        databaseViewModel.insert(
            Note(
                items[position].nId,
                items[position].noteTitle,
                items[position].noteContent,
                items[position].dateCreated,
                Calendar.getInstance().timeInMillis,
                items[position].gDriveId,
                noteType,
                items[position].synced,
                items[position].noteColor,
                items[position].reminderTime
            )
        )
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
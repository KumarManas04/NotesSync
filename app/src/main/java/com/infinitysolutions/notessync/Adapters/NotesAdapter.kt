package com.infinitysolutions.notessync.Adapters

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.gson.Gson
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_TRASH
import com.infinitysolutions.notessync.Model.ImageNoteContent
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.ChecklistConverter
import com.infinitysolutions.notessync.Util.ColorsUtil
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.notes_list_item.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class NotesAdapter(private val mainViewModel: MainViewModel, private val databaseViewModel: DatabaseViewModel, private val items: List<Note>, val context: Context) : RecyclerView.Adapter<NotesAdapter.ViewHolder>() {
    private val colorsUtil = ColorsUtil()
    private val pathsMap = SparseArray<String>()
    private val TAG = "NotesAdapter"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.notes_list_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.titleTextView.visibility = if (items[position].noteTitle!!.isEmpty())
            GONE
        else
            VISIBLE

        holder.titleTextView.text = items[position].noteTitle
        holder.parentView.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(colorsUtil.getColor(items[position].noteColor)))

        var noteContent = items[position].noteContent
        if (items[position].noteType == LIST_DEFAULT || items[position].noteType == LIST_ARCHIVED || items[position].noteType == LIST_TRASH) {
            holder.imageView.visibility = GONE
            if (noteContent != null && (noteContent.contains("[ ]") || noteContent.contains("[x]")))
                noteContent = ChecklistConverter.convertList(noteContent)
        } else if (items[position].noteType == IMAGE_DEFAULT || items[position].noteType == IMAGE_ARCHIVED || items[position].noteType == IMAGE_TRASH) {
            holder.imageView.visibility = VISIBLE
            val imageContent = Gson().fromJson(noteContent, ImageNoteContent::class.java)
            var path = pathsMap.get(position)
            if (path != null) {
                setImage(path, holder.imageView)
            } else {
                GlobalScope.launch(Dispatchers.IO) {
                    path = databaseViewModel.getImagePathById(imageContent.idList[0])
                    withContext(Dispatchers.Main) {
                        pathsMap.put(position, path)
                        setImage(path, holder.imageView)
                    }
                }
            }
            noteContent = imageContent.noteContent
        } else {
            holder.imageView.visibility = GONE
        }

        holder.contentTextView.visibility = if (noteContent == null || noteContent.isEmpty()) {
            GONE
        } else {
            holder.contentTextView.text = noteContent
            VISIBLE
        }

        if (items[position].noteType != NOTE_TRASH && items[position].noteType != LIST_TRASH) {
            holder.itemContainer.setOnClickListener {
                mainViewModel.setSelectedNote(items[position])
                mainViewModel.setShouldOpenEditor(true)
            }
        }

        holder.itemContainer.setOnLongClickListener {
            holder.itemContainer.setOnCreateContextMenuListener { menu, _, _ ->
                if (items[position].noteType != NOTE_TRASH && items[position].noteType != LIST_TRASH && items[position].noteType != IMAGE_TRASH) {
                    menu.add("Delete").setOnMenuItemClickListener {
                        if (items[position].noteType == IMAGE_DEFAULT || items[position].noteType == IMAGE_ARCHIVED)
                            changeNoteType(position, IMAGE_TRASH)
                        else if (items[position].noteType == LIST_DEFAULT || items[position].noteType == LIST_ARCHIVED)
                            changeNoteType(position, LIST_TRASH)
                        else
                            changeNoteType(position, NOTE_TRASH)
                        Toast.makeText(context, "Moved to trash", LENGTH_SHORT).show()
                        true
                    }
                    if (items[position].noteType == NOTE_DEFAULT || items[position].noteType == LIST_DEFAULT || items[position].noteType == IMAGE_DEFAULT) {
                        menu.add("Archive").setOnMenuItemClickListener {
                            when (items[position].noteType) {
                                IMAGE_DEFAULT -> changeNoteType(position, IMAGE_ARCHIVED)
                                LIST_DEFAULT -> changeNoteType(position, LIST_ARCHIVED)
                                else -> changeNoteType(position, NOTE_ARCHIVED)
                            }
                            true
                        }
                    } else {
                        menu.add("Unarchive").setOnMenuItemClickListener {
                            if (items[position].noteType == IMAGE_ARCHIVED)
                                changeNoteType(position, IMAGE_DEFAULT)
                            else if (items[position].noteType == LIST_ARCHIVED)
                                changeNoteType(position, LIST_DEFAULT)
                            else
                                changeNoteType(position, NOTE_DEFAULT)
                            true
                        }
                    }
                } else {
                    menu.add("Restore").setOnMenuItemClickListener {
                        when (items[position].noteType) {
                            IMAGE_TRASH -> changeNoteType(position, IMAGE_DEFAULT)
                            LIST_TRASH -> changeNoteType(position, LIST_DEFAULT)
                            else -> changeNoteType(position, NOTE_DEFAULT)
                        }
                        true
                    }
                    menu.add("Delete forever").setOnMenuItemClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Delete forever")
                            .setMessage("Are you sure you want to delete this note forever?")
                            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                                databaseViewModel.deleteNote(items[position])
                            }
                            .setNegativeButton("No", null)
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

    private fun setImage(path: String?, imageView: ImageView){
        if(path == null)
            return
            Glide.with(context).load(File(path)).listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    return false
                }

                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    imageView.clipToOutline = true
                    return false
                }
            }).into(imageView)
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

    fun getList(): List<Note>{
        return items
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.title_text
        val contentTextView: TextView = itemView.content_preview_text
        val parentView: ConstraintLayout = itemView.parent_view
        val imageView: ImageView = itemView.image_view
        val itemContainer = itemView
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
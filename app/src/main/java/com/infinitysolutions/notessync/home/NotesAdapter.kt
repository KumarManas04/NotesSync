package com.infinitysolutions.notessync.home

import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.content.Intent
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
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.gson.Gson
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_ARCHIVED
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_LIST_ARCHIVED
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_LIST_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_LIST_TRASH
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_TRASH
import com.infinitysolutions.notessync.contracts.Contract.Companion.LIST_ARCHIVED
import com.infinitysolutions.notessync.contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.LIST_TRASH
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_ARCHIVED
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_ID_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_TRASH
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_MAX_PREVIEW_LINES
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.model.ImageNoteContent
import com.infinitysolutions.notessync.model.Note
import com.infinitysolutions.notessync.noteedit.NoteEditActivity
import com.infinitysolutions.notessync.util.ChecklistConverter
import com.infinitysolutions.notessync.util.ColorsUtil
import kotlinx.android.synthetic.main.notes_list_item.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class NotesAdapter(
    private val homeViewModel: HomeViewModel,
    private val databaseViewModel: HomeDatabaseViewModel,
    private var items: List<Note>,
    val context: Context
) : RecyclerView.Adapter<NotesAdapter.ViewHolder>() {
    private val colorsUtil = ColorsUtil()
    private val pathsMap = SparseArray<String>()
    private val TAG = "NotesAdapter"
    private val maxLinesCount: Int
    private val selectedPositions = HashSet<Int>()
    private var isMultiSelectEnabled = false

    init {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        maxLinesCount = prefs.getInt(PREF_MAX_PREVIEW_LINES, Integer.MAX_VALUE)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.notes_list_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.contentTextView.maxLines = maxLinesCount
        holder.titleTextView.visibility = if (items[position].noteTitle!!.isEmpty())
            GONE
        else
            VISIBLE

        holder.titleTextView.text = items[position].noteTitle

        var noteContent = items[position].noteContent
        when (items[position].noteType) {
            LIST_DEFAULT, LIST_ARCHIVED, LIST_TRASH -> {
                holder.imageView.visibility = GONE
                if (noteContent != null && (noteContent.contains("[ ]") || noteContent.contains("[x]")))
                    noteContent = ChecklistConverter.convertList(noteContent)
            }
            IMAGE_DEFAULT, IMAGE_ARCHIVED, IMAGE_TRASH, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED, IMAGE_LIST_TRASH -> {
                holder.imageView.visibility = VISIBLE
                val imageContent = Gson().fromJson(noteContent, ImageNoteContent::class.java)
                var path = pathsMap.get(position)
                if (path != null) {
                    setImage(path, holder.imageView)
                } else {
                    if (imageContent.idList.isNotEmpty()) {
                        GlobalScope.launch(Dispatchers.IO) {
                            path = databaseViewModel.getImagePathById(imageContent.idList[0])
                            withContext(Dispatchers.Main) {
                                pathsMap.put(position, path)
                                setImage(path, holder.imageView)
                            }
                        }
                    }
                }
                noteContent = imageContent.noteContent
            }
            else -> {
                holder.imageView.visibility = GONE
            }
        }

        holder.contentTextView.visibility = if (noteContent == null || noteContent.isEmpty()) {
            GONE
        } else {
            holder.contentTextView.text = noteContent
            VISIBLE
        }

        holder.parentView.setPadding(0)
        if (!selectedPositions.contains(position)) {
            holder.parentView.setBackgroundResource(R.drawable.notes_item_round)
            holder.parentView.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor(colorsUtil.getColor(items[position].noteColor)))
        } else {
            holder.parentView.setBackgroundResource(R.drawable.notes_selected_bg)
            holder.parentView.backgroundTintList = null
            if (isImageType(items[position].noteType)) {
                val density = context.resources.displayMetrics.density
                val paddingPixels: Int = (3 * density).toInt()
                holder.parentView.setPadding(paddingPixels, paddingPixels, paddingPixels, 0)
            }
        }

        holder.itemContainer.setOnClickListener {
            if (!isMultiSelectEnabled) {
                // Normal operations
                if (items[position].noteType != NOTE_TRASH && items[position].noteType != LIST_TRASH && items[position].noteType != IMAGE_TRASH && items[position].noteType != IMAGE_LIST_TRASH) {
                    val noteIntent = Intent(context, NoteEditActivity::class.java)
                    noteIntent.putExtra(NOTE_ID_EXTRA, items[position].nId)
                    context.startActivity(noteIntent)
                }
            } else {
                if (selectedPositions.contains(position)) {
                    selectedPositions.remove(position)
                    if (selectedPositions.size <= 0) {
                        isMultiSelectEnabled = false
                    }
                } else
                    selectedPositions.add(position)
                homeViewModel.setMultiSelectCount(selectedPositions.size)
                notifyItemChanged(position)
            }
        }

        holder.itemContainer.setOnLongClickListener {
            if (!isMultiSelectEnabled) {
                homeViewModel.setMultiSelectCount(1)
                selectedPositions.add(position)
                isMultiSelectEnabled = true
                notifyItemChanged(position)
                true
            } else
                false
        }
    }

    fun restoreSelectedNotes() {
        for (position in selectedPositions) {
            when (items[position].noteType) {
                IMAGE_TRASH -> changeNoteType(position, IMAGE_DEFAULT)
                LIST_TRASH -> changeNoteType(position, LIST_DEFAULT)
                IMAGE_LIST_TRASH -> changeNoteType(position, IMAGE_LIST_DEFAULT)
                else -> changeNoteType(position, NOTE_DEFAULT)
            }
        }
    }

    fun changeList(newItems: List<Note>) {
        items = newItems
        selectedPositions.clear()
        notifyDataSetChanged()
    }

    private fun isImageType(type: Int): Boolean {
        return when (type) {
            IMAGE_DEFAULT, IMAGE_ARCHIVED, IMAGE_TRASH, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED, IMAGE_LIST_TRASH -> true
            else -> false
        }
    }

    fun archiveSelectedNotes() {
        for (position in selectedPositions) {
            when (items[position].noteType) {
                IMAGE_DEFAULT -> changeNoteType(position, IMAGE_ARCHIVED)
                LIST_DEFAULT -> changeNoteType(position, LIST_ARCHIVED)
                IMAGE_LIST_DEFAULT -> changeNoteType(position, IMAGE_LIST_ARCHIVED)
                else -> changeNoteType(position, NOTE_ARCHIVED)
            }
        }
    }

    fun unarchiveSelectedNotes() {
        for (position in selectedPositions) {
            when (items[position].noteType) {
                IMAGE_ARCHIVED -> changeNoteType(position, IMAGE_DEFAULT)
                LIST_ARCHIVED -> changeNoteType(position, LIST_DEFAULT)
                IMAGE_LIST_ARCHIVED -> changeNoteType(position, IMAGE_LIST_DEFAULT)
                else -> changeNoteType(position, NOTE_DEFAULT)
            }
        }
    }

    fun deleteSelectedNotes() {
        for (position in selectedPositions) {
            when (items[position].noteType) {
                IMAGE_DEFAULT, IMAGE_ARCHIVED -> changeNoteType(position, IMAGE_TRASH)
                LIST_DEFAULT, LIST_ARCHIVED -> changeNoteType(position, LIST_TRASH)
                IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> changeNoteType(
                    position,
                    IMAGE_LIST_TRASH
                )
                else -> changeNoteType(position, NOTE_TRASH)
            }
        }

        Toast.makeText(context, this.context.getString(R.string.toast_moved_to_trash), LENGTH_SHORT)
            .show()
    }

    fun deleteForeverSelectedNotes() {
        AlertDialog.Builder(context)
            .setTitle(this.context.getString(R.string.action_delete_forever))
            .setMessage(this.context.getString(R.string.question_delete_forever))
            .setPositiveButton(this.context.getString(R.string.yes)) { _: DialogInterface, _: Int ->
                for (position in selectedPositions)
                    databaseViewModel.deleteNote(items[position])
            }
            .setNegativeButton(this.context.getString(R.string.no), null)
            .setCancelable(false)
            .show()
    }

    fun selectAll() {
        for (position in items.indices)
            selectedPositions.add(position)
        homeViewModel.setMultiSelectCount(selectedPositions.size)
        notifyDataSetChanged()
    }

    fun clearAll() {
        selectedPositions.clear()
        homeViewModel.setMultiSelectCount(0)
        isMultiSelectEnabled = false
        notifyDataSetChanged()
    }

    private fun setImage(path: String?, imageView: ImageView) {
        if (path == null)
            return
        Glide.with(context).load(File(path)).listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>?,
                isFirstResource: Boolean
            ): Boolean {
                return false
            }

            override fun onResourceReady(
                resource: Drawable?,
                model: Any?,
                target: Target<Drawable>?,
                dataSource: DataSource?,
                isFirstResource: Boolean
            ): Boolean {
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
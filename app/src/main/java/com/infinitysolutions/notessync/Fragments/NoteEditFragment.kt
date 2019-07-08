package com.infinitysolutions.notessync.Fragments


import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.infinitysolutions.notessync.Adapters.ColorPickerAdapter
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DELETED
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import it.feio.android.checklistview.exceptions.ViewNotSupportedException
import it.feio.android.checklistview.models.ChecklistManager
import kotlinx.android.synthetic.main.bottom_sheet.view.*
import kotlinx.android.synthetic.main.fragment_note_edit.view.*
import java.text.SimpleDateFormat
import java.util.*


class NoteEditFragment : Fragment() {
    private val TAG = "NoteEditFragment"
    private lateinit var databaseViewModel: DatabaseViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var noteTitle: EditText
    private lateinit var noteContent: EditText
    private lateinit var mChecklistManager: ChecklistManager
    private var switchView: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_note_edit, container, false)
        initDataBinding(rootView)

        //Setting up bottom menu
        val menuButton = rootView.open_bottom_menu
        menuButton.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.bottom_sheet, null)
            val dialog = BottomSheetDialog(this@NoteEditFragment.context!!)
            dialogView.delete_button.setOnClickListener {
                deleteNote()
                dialog.hide()
            }

            val selectedNote = mainViewModel.getSelectedNote()
            if (selectedNote != null) {
                if (selectedNote.noteType == NOTE_DEFAULT || selectedNote.noteType == LIST_DEFAULT) {
                    dialogView.archive_button_icon.setImageResource(R.drawable.archive_drawer_item)
                    dialogView.archive_button_text.text = "Archive note"
                } else {
                    dialogView.archive_button_icon.setImageResource(R.drawable.unarchive_menu_item)
                    dialogView.archive_button_text.text = "Unarchive note"
                }
            }

            dialogView.archive_button.setOnClickListener {
                archiveNote()
                dialog.hide()
            }

            val layoutManager = LinearLayoutManager(this@NoteEditFragment.context!!, RecyclerView.HORIZONTAL, false)
            dialogView.color_picker.layoutManager = layoutManager
            dialogView.color_picker.adapter = ColorPickerAdapter(this@NoteEditFragment.context!!, mainViewModel)
            dialog.setContentView(dialogView)
            dialog.show()
        }
        return rootView
    }

    private fun initDataBinding(rootView: View) {
        databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        noteTitle = rootView.note_title
        noteContent = rootView.note_content

        mainViewModel.getSelectedColor().observe(this, androidx.lifecycle.Observer { selectedColor ->
            noteTitle.setTextColor(Color.parseColor(selectedColor))
            rootView.last_edited_text.setTextColor(Color.parseColor(selectedColor))
        })

        val toolbar = rootView.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.note_editor_menu)
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.remind_menu_item -> {
                    //TODO: Implement reminder
                }
            }
            true
        }

        if (mainViewModel.getShouldOpenEditor().value!!) {
            mainViewModel.setShouldOpenEditor(false)
            val selectedNote = mainViewModel.getSelectedNote()
            if (selectedNote != null) {
                if (selectedNote.nId != -1L) {
                    noteTitle.setText(selectedNote.noteTitle)
                    noteContent.setText(selectedNote.noteContent)
                    mainViewModel.setSelectedColor(selectedNote.noteColor)
                    val formatter = SimpleDateFormat("MMM d, YYYY", Locale.ENGLISH)
                    rootView.last_edited_text.text = "Edited  ${formatter.format(Calendar.getInstance().timeInMillis)}"
                }

                if (selectedNote.noteType == LIST_DEFAULT || selectedNote.noteType == LIST_ARCHIVED) {
                    try {
                        mChecklistManager = ChecklistManager(context)
                        switchView = noteContent
                        mChecklistManager.newEntryHint("Add new")
                        mChecklistManager.moveCheckedOnBottom(0)
                        mChecklistManager.showCheckMarks(true)
                        mChecklistManager.keepChecked(true)
                        mChecklistManager.dragEnabled(false)
                        val newView = mChecklistManager.convert(switchView)
                        mChecklistManager.replaceViews(switchView, newView)
                        switchView = newView
                    } catch (e: ViewNotSupportedException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun archiveNote() {
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null && selectedNote.nId != -1L) {
            val noteType: Int = when(selectedNote.noteType){
                NOTE_DEFAULT-> NOTE_ARCHIVED
                LIST_DEFAULT-> LIST_ARCHIVED
                NOTE_ARCHIVED-> NOTE_DEFAULT
                LIST_ARCHIVED-> LIST_DEFAULT
                else -> -1
            }

            if (noteType != -1) {
                databaseViewModel.insert(
                    Note(
                        selectedNote.nId,
                        noteTitle.text.toString(),
                        noteContent.text.toString(),
                        selectedNote.dateCreated,
                        Calendar.getInstance().timeInMillis,
                        selectedNote.gDriveId,
                        noteType,
                        selectedNote.synced,
                        mainViewModel.getSelectedColor().value
                    )
                )
            }
            activity?.onBackPressed()
        }
    }

    private fun saveNote() {
        val timeModified = Calendar.getInstance().timeInMillis
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null) {
            if (selectedNote.nId == -1L) {
                if (noteContent.text.isNotEmpty()) {
                    databaseViewModel.insert(
                        Note(
                            null,
                            noteTitle.text.toString(),
                            noteContent.text.toString(),
                            timeModified,
                            timeModified,
                            "-1",
                            selectedNote.noteType,
                            false,
                            mainViewModel.getSelectedColor().value
                        )
                    )
                }
            } else {
                Log.d("TAG", "GDriveId = ${selectedNote.gDriveId}")
                databaseViewModel.insert(
                    Note(
                        selectedNote.nId,
                        noteTitle.text.toString(),
                        noteContent.text.toString(),
                        selectedNote.dateCreated,
                        timeModified,
                        selectedNote.gDriveId,
                        selectedNote.noteType,
                        selectedNote.synced,
                        mainViewModel.getSelectedColor().value
                    )
                )
            }
        }
    }

    private fun deleteNote() {
        AlertDialog.Builder(context)
            .setTitle("Delete note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Yes") { dialog, which ->
                activity!!.onBackPressed()
                val selectedNote = mainViewModel.getSelectedNote()
                if (selectedNote != null) {
                    databaseViewModel.insert(
                        Note(
                            selectedNote.nId,
                            selectedNote.noteTitle,
                            selectedNote.noteContent,
                            selectedNote.dateCreated,
                            Calendar.getInstance().timeInMillis,
                            selectedNote.gDriveId,
                            NOTE_DELETED,
                            selectedNote.synced,
                            mainViewModel.getSelectedColor().value
                        )
                    )
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroy() {
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null) {
            var noteContentText = ""
            if (selectedNote.noteType == LIST_DEFAULT || selectedNote.noteType == LIST_ARCHIVED) {
                try {
                    noteContentText = (mChecklistManager.convert(switchView) as EditText).text.toString()
                } catch (e: ViewNotSupportedException) {
                    e.printStackTrace()
                    noteContentText = selectedNote.noteContent!!
                }
            } else {
                noteContentText = noteContent.text.toString()
            }

            if ((selectedNote.noteContent != noteContentText)
                || (selectedNote.noteTitle != noteTitle.text.toString())
                || (selectedNote.noteColor != mainViewModel.getSelectedColor().value)
            )
                saveNote()
        }
        super.onDestroy()
    }
}
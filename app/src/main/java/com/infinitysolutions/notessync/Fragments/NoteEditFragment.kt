package com.infinitysolutions.notessync.Fragments


import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.infinitysolutions.checklistview.ChecklistView
import com.infinitysolutions.notessync.Adapters.ColorPickerAdapter
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IS_ROTATED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_TRASH
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.ChecklistConverter
import com.infinitysolutions.notessync.Util.ColorsUtil
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.bottom_sheet.view.*
import kotlinx.android.synthetic.main.fragment_note_edit.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*


class NoteEditFragment : Fragment() {
    private val TAG = "NoteEditFragment"
    private lateinit var databaseViewModel: DatabaseViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var noteTitle: EditText
    private lateinit var noteContent: EditText
    private lateinit var checklistView: ChecklistView
    private val colorsUtil = ColorsUtil()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_note_edit, container, false)
        initDataBinding(rootView, savedInstanceState)

        //Setting up bottom menu
        val menuButton = rootView.open_bottom_menu
        menuButton.setOnClickListener {
            startBottomSheetDialog(container)
        }
        return rootView
    }

    private fun initDataBinding(rootView: View, savedInstanceState: Bundle?) {
        databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        noteTitle = rootView.note_title
        noteContent = rootView.note_content
        checklistView = rootView.checklist_view

        mainViewModel.getSelectedColor().observe(this, androidx.lifecycle.Observer { selectedColor ->
            noteTitle.setTextColor(Color.parseColor(colorsUtil.getColor(selectedColor)))
            rootView.last_edited_text.setTextColor(Color.parseColor(colorsUtil.getColor(selectedColor)))
        })

        val toolbar = rootView.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.note_editor_menu)
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.delete_menu_item -> {
                    deleteNote()
                }
            }
            true
        }

        if (arguments != null) {
            val noteId = arguments?.getLong("NOTE_ID")
            if (noteId != null) {
                GlobalScope.launch(Dispatchers.IO) {
                    val note = databaseViewModel.getNoteById(noteId)
                    withContext(Dispatchers.Main) {
                        mainViewModel.setSelectedNote(note)
                        prepareNoteView(rootView)
                    }
                }
            }
        } else {
            if (mainViewModel.getShouldOpenEditor().value != null) {
                if (mainViewModel.getShouldOpenEditor().value!!) {
                    mainViewModel.setShouldOpenEditor(false)
                    prepareNoteView(rootView)
                }else{
                    if(savedInstanceState != null)
                        if(savedInstanceState.getBoolean(IS_ROTATED)) {
                            if(mainViewModel.getSelectedNote()?.nId == -1L)
                                Toast.makeText(activity, "Note saved. This is a new note", LENGTH_SHORT).show()
                            prepareNoteView(rootView)
                        }
                }
            }
        }
    }

    private fun prepareNoteView(rootView: View) {
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null) {
            noteContent.setText(selectedNote.noteContent)
            if (selectedNote.nId != -1L) {
                noteTitle.setText(selectedNote.noteTitle)
                mainViewModel.setSelectedColor(selectedNote.noteColor)
                val formatter = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
                rootView.last_edited_text.text = getString(R.string.edited_time_stamp, formatter.format(Calendar.getInstance().timeInMillis))
            }

            mainViewModel.reminderTime = selectedNote.reminderTime
            if (selectedNote.noteType == LIST_DEFAULT || selectedNote.noteType == LIST_ARCHIVED) {
                checklistView.visibility = VISIBLE
                noteContent.visibility = GONE
                var content = selectedNote.noteContent
                if(content != null){
                    if(content.contains("[ ]") || content.contains("[x]"))
                        content = ChecklistConverter.convertList(content)
                    checklistView.setList(content)
                }
            }else{
                checklistView.visibility = GONE
                noteContent.visibility = VISIBLE
            }
        }
    }

    private fun startBottomSheetDialog(container: ViewGroup?) {
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet, container, false)
        val dialog = BottomSheetDialog(this@NoteEditFragment.context!!)

        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null) {
            if (selectedNote.noteType == NOTE_DEFAULT || selectedNote.noteType == LIST_DEFAULT) {
                dialogView.archive_button_icon.setImageResource(R.drawable.archive_drawer_item)
                dialogView.archive_button_text.text = getString(R.string.archive_note)
            } else {
                dialogView.archive_button_icon.setImageResource(R.drawable.unarchive_menu_item)
                dialogView.archive_button_text.text = getString(R.string.unarchive_note)
            }

            if (mainViewModel.reminderTime != -1L) {
                dialogView.cancel_reminder_button.visibility = View.VISIBLE
                val formatter = SimpleDateFormat("h:mm a MMM d, YYYY", Locale.ENGLISH)
                dialogView.reminder_text.text = getString(R.string.reminder_set, formatter.format(mainViewModel.reminderTime))
                dialogView.reminder_text.setTextColor(Color.parseColor(colorsUtil.getColor(mainViewModel.getSelectedColor().value)))
                dialogView.cancel_reminder_button.setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle("Cancel reminder")
                        .setMessage("Are you sure you want to cancel the reminder?")
                        .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                            WorkSchedulerHelper().cancelReminderByNoteId(selectedNote.nId)
                            mainViewModel.reminderTime = -1L
                            dialog.hide()
                        }
                        .setNegativeButton("No", null)
                        .show()
                }
                dialogView.cancel_reminder_button.setColorFilter(Color.parseColor(colorsUtil.getColor(mainViewModel.getSelectedColor().value)))
            } else {
                dialogView.cancel_reminder_button.visibility = GONE
                dialogView.reminder_text.text = getString(R.string.set_reminder)
                val typedValue = TypedValue()
                context?.theme?.resolveAttribute(R.attr.mainTextColor, typedValue, true)
                val textColor = typedValue.data
                dialogView.reminder_text.setTextColor(textColor)
            }

            dialogView.reminder_button.setOnClickListener {
                pickReminderTime(selectedNote.nId)
                dialog.hide()
            }

            dialogView.share_button.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_TEXT, "${noteTitle.text}\n${getNoteText(selectedNote)}")
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, noteTitle.text.toString())
                startActivity(Intent.createChooser(shareIntent, "Share..."))
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

    private fun pickReminderTime(noteId: Long?) {
        if (noteId != null) {
            val c = Calendar.getInstance()
            val cal = Calendar.getInstance()

            val timePickerDialog = TimePickerDialog(context, { _, hourOfDay, minute ->
                cal.set(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                    hourOfDay,
                    minute,
                    0
                )
                if (cal.timeInMillis > Calendar.getInstance().timeInMillis) {
                    WorkSchedulerHelper().setReminder(noteId, cal.timeInMillis)
                    mainViewModel.reminderTime = cal.timeInMillis
                } else {
                    Toast.makeText(activity, "Reminder cannot be set before present time", Toast.LENGTH_SHORT).show()
                }
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false)

            val datePickerDialog = DatePickerDialog(context!!, { _, year, month, dayOfMonth ->
                cal.set(year, month, dayOfMonth)
                timePickerDialog.show()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            datePickerDialog.datePicker.minDate = c.timeInMillis
            datePickerDialog.show()
        }
    }

    private fun archiveNote() {
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null && selectedNote.nId != -1L) {
            val noteType: Int = when (selectedNote.noteType) {
                NOTE_DEFAULT -> NOTE_ARCHIVED
                LIST_DEFAULT -> LIST_ARCHIVED
                NOTE_ARCHIVED -> NOTE_DEFAULT
                LIST_ARCHIVED -> LIST_DEFAULT
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
                        mainViewModel.getSelectedColor().value,
                        selectedNote.reminderTime
                    )
                )
            }
            updateWidgets()
            activity?.onBackPressed()
        }
    }

    private fun saveNote(content: String) {
        val timeModified = Calendar.getInstance().timeInMillis
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null) {
            if (selectedNote.nId == -1L) {
                if (content.isNotEmpty() || noteTitle.text.isNotEmpty()) {
                    databaseViewModel.insert(
                        Note(
                            null,
                            noteTitle.text.toString(),
                            content,
                            timeModified,
                            timeModified,
                            "-1",
                            selectedNote.noteType,
                            false,
                            mainViewModel.getSelectedColor().value,
                            -1L
                        )
                    )
                }
            } else {
                Log.d("TAG", "GDriveId = ${selectedNote.gDriveId}")
                databaseViewModel.insert(
                    Note(
                        selectedNote.nId,
                        noteTitle.text.toString(),
                        content,
                        selectedNote.dateCreated,
                        timeModified,
                        selectedNote.gDriveId,
                        selectedNote.noteType,
                        selectedNote.synced,
                        mainViewModel.getSelectedColor().value,
                        mainViewModel.reminderTime
                    )
                )
            }
            updateWidgets()
        }
    }

    private fun updateWidgets() {
        val intent = Intent(activity, NotesWidget::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(activity).getAppWidgetIds(ComponentName(activity!!, NotesWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        activity?.sendBroadcast(intent)
    }

    private fun deleteNote() {
        AlertDialog.Builder(context)
            .setTitle("Delete note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Yes") { _, _ ->
                val selectedNote = mainViewModel.getSelectedNote()
                if (selectedNote?.nId == -1L) {
                    mainViewModel.setSelectedNote(null)
                } else {
                    if (selectedNote != null) {
                        val noteType = if (selectedNote.noteType == NOTE_DEFAULT || selectedNote.noteType == NOTE_ARCHIVED)
                            NOTE_TRASH
                        else
                            LIST_TRASH

                        databaseViewModel.insert(
                            Note(
                                selectedNote.nId,
                                selectedNote.noteTitle,
                                selectedNote.noteContent,
                                selectedNote.dateCreated,
                                Calendar.getInstance().timeInMillis,
                                selectedNote.gDriveId,
                                noteType,
                                selectedNote.synced,
                                mainViewModel.getSelectedColor().value,
                                -1L
                            )
                        )

                        if (selectedNote.reminderTime != -1L) {
                            WorkSchedulerHelper().cancelReminderByNoteId(selectedNote.nId)
                        }
                        updateWidgets()
                    }
                }
                activity?.onBackPressed()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun getNoteText(selectedNote: Note): String {
        return if (selectedNote.noteType == LIST_DEFAULT || selectedNote.noteType == LIST_ARCHIVED)
            checklistView.toString()
        else
            noteContent.text.toString()
    }

    override fun onDestroy() {
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null) {
            val noteContentText = getNoteText(selectedNote)

            if ((selectedNote.noteContent != noteContentText)
                || (selectedNote.noteTitle != noteTitle.text.toString())
                || (selectedNote.noteColor != mainViewModel.getSelectedColor().value)
                || (selectedNote.reminderTime != mainViewModel.reminderTime)
            )
                saveNote(noteContentText)
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(IS_ROTATED, true)
    }
}
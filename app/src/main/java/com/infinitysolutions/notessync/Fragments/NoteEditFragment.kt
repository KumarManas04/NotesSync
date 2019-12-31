package com.infinitysolutions.notessync.Fragments


import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.NavHostFragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.infinitysolutions.checklistview.ChecklistView
import com.infinitysolutions.notessync.Adapters.ColorPickerAdapter
import com.infinitysolutions.notessync.Adapters.ImageListAdapter
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_DEFAULT
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
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.bottom_sheet.view.*
import kotlinx.android.synthetic.main.fragment_note_edit.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class NoteEditFragment : Fragment() {
    private val TAG = "NoteEditFragment"
    private lateinit var databaseViewModel: DatabaseViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var noteTitle: EditText
    private lateinit var noteContent: EditText
    private lateinit var checklistView: ChecklistView
    private lateinit var imageRecyclerView: RecyclerView
    private val colorsUtil = ColorsUtil()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_note_edit, container, false)
        initDataBinding(rootView)

        //Setting up bottom menu
        val menuButton = rootView.open_bottom_menu
        menuButton.setOnClickListener {
            startBottomSheetDialog(container)
        }
        return rootView
    }

    private fun initDataBinding(rootView: View) {
        databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        noteTitle = rootView.note_title
        noteContent = rootView.note_content
        checklistView = rootView.checklist_view
        imageRecyclerView = rootView.images_recycler_view

        mainViewModel.getSelectedColor().observe(this, androidx.lifecycle.Observer { selectedColor ->
                noteTitle.setTextColor(Color.parseColor(colorsUtil.getColor(selectedColor)))
                rootView.last_edited_text.setTextColor(Color.parseColor(colorsUtil.getColor(selectedColor)))
            })

        mainViewModel.getOpenImageView().observe(this, androidx.lifecycle.Observer {
            it.getContentIfNotHandled()?.let { imagePosition ->
                val bundle = bundleOf("currentPosition" to imagePosition)
                findNavController(this).navigate(R.id.action_noteEditFragment_to_imageGalleryFragment, bundle)
            }
        })

        val toolbar = rootView.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.note_editor_menu)
        val selectedNote: Note? = mainViewModel.getSelectedNote()
        toolbar.menu.findItem(R.id.add_image_menu_item).isVisible = selectedNote != null && (selectedNote.noteType == IMAGE_DEFAULT || selectedNote.noteType == IMAGE_ARCHIVED)
        toolbar.setNavigationOnClickListener {
            findNavController(this).navigateUp()
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.delete_menu_item -> {
                    deleteNote()
                }
                R.id.add_image_menu_item -> {
                    rootView.setOnCreateContextMenuListener { menu, _, _ ->
                        if(mainViewModel.getImagesList().isNotEmpty())
                            Log.d(TAG, "Not Empty")
                        else
                            Log.d(TAG, "Is Empty")
                        openNewImageMenu(menu)
                    }
                    rootView.showContextMenu()
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
                if (mainViewModel.getShouldOpenEditor().value!!)
                    mainViewModel.setShouldOpenEditor(false)
                prepareNoteView(rootView)
            }
        }
    }

    private fun prepareNoteView(rootView: View) {
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null) {
            if (selectedNote.nId != -1L) {
                noteTitle.setText(selectedNote.noteTitle)
                mainViewModel.setSelectedColor(selectedNote.noteColor)
                val formatter = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
                rootView.last_edited_text.text = getString(
                    R.string.edited_time_stamp,
                    formatter.format(Calendar.getInstance().timeInMillis)
                )
            }

            mainViewModel.reminderTime = selectedNote.reminderTime
            if (selectedNote.noteType == LIST_DEFAULT || selectedNote.noteType == LIST_ARCHIVED) {
                checklistView.visibility = VISIBLE
                noteContent.visibility = GONE
                imageRecyclerView.visibility = GONE
                var content = selectedNote.noteContent
                if (content != null) {
                    if (content.contains("[ ]") || content.contains("[x]"))
                        content = ChecklistConverter.convertList(content)
                    checklistView.setList(content)
                }
            } else if (selectedNote.noteType == IMAGE_DEFAULT || selectedNote.noteType == IMAGE_ARCHIVED) {
                imageRecyclerView.visibility = VISIBLE
                checklistView.visibility = GONE
                noteContent.visibility = VISIBLE

                val imageData = Gson().fromJson(selectedNote.noteContent, ImageNoteContent::class.java)
                Log.d(TAG, selectedNote.noteContent)
                noteContent.setText(imageData.noteContent)

                imageRecyclerView.layoutManager = LinearLayoutManager(context, HORIZONTAL, false)
                GlobalScope.launch(Dispatchers.IO) {
                    val idList = if(mainViewModel.getImagesList().isNotEmpty()){
                        Log.d(TAG, "imagesList not empty")
                        val idList1 = ArrayList<Long>()
                        for(item in mainViewModel.getImagesList())
                            idList1.add(item.imageId!!)
                        idList1
                    }else{
                        Log.d(TAG, "imagesList is empty")
                        imageData.idList
                    }

                    val list = databaseViewModel.getImagesByIds(idList)
                    withContext(Dispatchers.Main) {
                        if(mainViewModel.getImagesList().isEmpty()) {
                            Log.d(TAG, "Setting: imagesList is empty")
                            mainViewModel.setImagesList(list)
                        }
                        imageRecyclerView.adapter = ImageListAdapter(context!!, list, mainViewModel)
                        imageRecyclerView.isNestedScrollingEnabled = false
                    }
                }
            } else {
                noteContent.setText(selectedNote.noteContent)
                imageRecyclerView.visibility = GONE
                checklistView.visibility = GONE
                noteContent.visibility = VISIBLE
            }
            noteContent.postDelayed({
                noteContent.requestFocus()
                noteContent.setSelection(noteContent.text.length)
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(noteContent, 0)
            }, 50)
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
                dialogView.reminder_text.text =
                    getString(R.string.reminder_set, formatter.format(mainViewModel.reminderTime))
                dialogView.reminder_text.setTextColor(
                    Color.parseColor(
                        colorsUtil.getColor(
                            mainViewModel.getSelectedColor().value
                        )
                    )
                )
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
                dialogView.cancel_reminder_button.setColorFilter(
                    Color.parseColor(
                        colorsUtil.getColor(
                            mainViewModel.getSelectedColor().value
                        )
                    )
                )
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
                shareIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    "${noteTitle.text}\n${getNoteText(selectedNote)}"
                )
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
                    Toast.makeText(
                        activity,
                        "Reminder cannot be set before present time",
                        Toast.LENGTH_SHORT
                    ).show()
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
        }
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
                        val noteType =
                            if (selectedNote.noteType == NOTE_DEFAULT || selectedNote.noteType == NOTE_ARCHIVED)
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
                    }
                }
                activity?.onBackPressed()
            }
            .setNegativeButton("No", null)
            .show()
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = activity?.filesDir
        Log.d("MainActivity", "extDir: $storageDir")
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun openNewImageMenu(menu: Menu) {
        menu.add("Take a photo").setOnMenuItemClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(activity!!.packageManager) != null) {
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                    null
                }
                if (photoFile != null) {
                    mainViewModel.setCurrentPhotoPath(photoFile.absolutePath)
                    val photoUri = FileProvider.getUriForFile(
                        context!!,
                        "com.infinitysolutions.notessync.fileprovider",
                        photoFile
                    )
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    startActivityForResult(intent, Contract.IMAGE_CAPTURE_REQUEST_CODE)
                } else
                    Toast.makeText(
                        context,
                        "Couldn't access file system",
                        Toast.LENGTH_SHORT
                    ).show()
            } else {
                Toast.makeText(context, "No camera app found!", Toast.LENGTH_SHORT).show()
            }
            true
        }
        menu.add("Pick an image").setOnMenuItemClickListener {
            val i = Intent(Intent.ACTION_PICK)
            i.type = "image/*"
            startActivityForResult(i, Contract.IMAGE_PICKER_REQUEST_CODE)
            true
        }
    }

    private fun getNoteText(selectedNote: Note): String {
        return if (selectedNote.noteType == LIST_DEFAULT || selectedNote.noteType == LIST_ARCHIVED)
            checklistView.toString()
        else if (selectedNote.noteType == NOTE_DEFAULT || selectedNote.noteType == NOTE_ARCHIVED)
            noteContent.text.toString()
        else {
            Gson().toJson(ImageNoteContent(
                    noteContent.text.toString(),
                    (imageRecyclerView.adapter as ImageListAdapter).getIdsList()
                )
            )
        }
    }

    override fun onDestroy() {
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null) {
            val noteContentText = getNoteText(selectedNote)
            Log.d(TAG, "Note Content last = $noteContentText")
            if(!activity!!.isChangingConfigurations) {
                mainViewModel.setImagesList(null)
                if ((selectedNote.noteContent != noteContentText)
                            || (selectedNote.noteTitle != noteTitle.text.toString())
                            || (selectedNote.noteColor != mainViewModel.getSelectedColor().value)
                            || (selectedNote.reminderTime != mainViewModel.reminderTime))
                {
                    saveNote(noteContentText)
                    //TODO: Call the sync work for this note here
                }
            }
        }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val databaseViewModel =
                ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
            var bitmap: Bitmap? = null
            if (requestCode == Contract.IMAGE_PICKER_REQUEST_CODE) {
                val uri: Uri? = data?.data
                if (uri != null) {
                    val imageStream = activity!!.contentResolver.openInputStream(uri)
                    bitmap = BitmapFactory.decodeStream(imageStream)
                }
            } else if (requestCode == Contract.IMAGE_CAPTURE_REQUEST_CODE) {
                bitmap = BitmapFactory.decodeFile(mainViewModel.getCurrentPhotoPath())
                if (mainViewModel.getCurrentPhotoPath() != null) {
                    val file = File(mainViewModel.getCurrentPhotoPath())
                    if (file.exists())
                        file.delete()
                }
            }
            if (bitmap != null) {
                val builder = AlertDialog.Builder(context)
                builder.setCancelable(false)
                builder.setView(R.layout.loading_dialog_layout)
                val dialog = builder.create()
                dialog.show()
                GlobalScope.launch(Dispatchers.IO) {
                    val imageData = databaseViewModel.insertImage(activity!!.filesDir.toString(), bitmap)
                    bitmap.recycle()

                    withContext(Dispatchers.Main) {
                        (imageRecyclerView.adapter as ImageListAdapter).addImage(imageData)
                        mainViewModel.addImageToImageList(imageData)
                        dialog.dismiss()
                    }
                }
            }
        }
    }
}
package com.infinitysolutions.notessync.noteedit


import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.NavHostFragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.infinitysolutions.checklistview.ChecklistView
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_ARCHIVED
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_CAPTURE_REQUEST_CODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_LIST_ARCHIVED
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_LIST_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_LIST_TRASH
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_PICKER_REQUEST_CODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_TRASH
import com.infinitysolutions.notessync.contracts.Contract.Companion.LIST_ARCHIVED
import com.infinitysolutions.notessync.contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.LIST_TRASH
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_ARCHIVED
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_TRASH
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_DEFAULT_NOTE_COLOR
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_MOVE_CHECKED_TO_BOTTOM
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.model.ImageData
import com.infinitysolutions.notessync.model.ImageNoteContent
import com.infinitysolutions.notessync.model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.util.ChecklistConverter
import com.infinitysolutions.notessync.util.ColorsUtil
import com.infinitysolutions.notessync.util.WorkSchedulerHelper
import kotlinx.android.synthetic.main.add_bottom_sheet.view.*
import kotlinx.android.synthetic.main.bottom_sheet.view.*
import kotlinx.android.synthetic.main.fragment_note_edit.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class NoteEditFragment : Fragment() {
    private val TAG = "NoteEditFragment"
    private lateinit var noteEditDatabaseViewModel: NoteEditDatabaseViewModel
    private lateinit var noteEditViewModel: NoteEditViewModel
    private lateinit var noteTitle: EditText
    private lateinit var noteContent: EditText
    private lateinit var checklistView: ChecklistView
    private lateinit var imageRecyclerView: RecyclerView
    private var imageListAdapter: ImageListAdapter? = null
    private val colorsUtil = ColorsUtil()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_note_edit, container, false)
        initDataBinding(rootView)

        //Setting up bottom menu
        rootView.open_bottom_menu.setOnClickListener {
            startBottomSheetDialog(container)
        }
        rootView.open_add_bottom_menu.setOnClickListener {
            startAddBottomDialog(container)
        }
        return rootView
    }

    private fun initDataBinding(rootView: View) {
        noteEditDatabaseViewModel = ViewModelProviders.of(activity!!).get(NoteEditDatabaseViewModel::class.java)
        noteEditViewModel = ViewModelProviders.of(activity!!).get(NoteEditViewModel::class.java)

        noteTitle = rootView.note_title
        noteContent = rootView.note_content

        checklistView = rootView.checklist_view
        val prefs = context!!.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        val shouldMoveToBottom = prefs.getBoolean(PREF_MOVE_CHECKED_TO_BOTTOM, true)
        checklistView.moveCheckedToBottom(shouldMoveToBottom)

        imageRecyclerView = rootView.images_recycler_view
        imageRecyclerView.layoutManager = LinearLayoutManager(context, HORIZONTAL, false)

        noteEditViewModel.getSelectedColor()
            .observe(this, { selectedColor ->
                noteTitle.setTextColor(Color.parseColor(colorsUtil.getColor(selectedColor)))
                rootView.last_edited_text.setTextColor(Color.parseColor(colorsUtil.getColor(selectedColor)))
            })

        noteEditViewModel.getOpenImageView().observe(this, {
            it.getContentIfNotHandled()?.let { imagePosition ->
                Log.d(TAG, "Open Image View")
                val bundle = bundleOf("currentPosition" to imagePosition)
                findNavController(this).navigate(R.id.action_noteEditFragment2_to_imageGalleryFragment2, bundle)
            }
        })

        noteEditViewModel.getRefreshImagesList().observe(this, {
            it.getContentIfNotHandled()?.let { shouldRefresh ->
                if (shouldRefresh) {
                    if(imageListAdapter != null) {
                        GlobalScope.launch(Dispatchers.IO) {
                            val newList = noteEditDatabaseViewModel.getImagesByIds(imageListAdapter!!.getIdsList())
                            withContext(Dispatchers.Main){
                                imageListAdapter?.setNewList(newList)
                            }
                        }
                    }
                }
            }
        })

        val toolbar = rootView.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.note_editor_menu)

        toolbar.setNavigationOnClickListener {
            activity?.finish()
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.archive_menu_item, R.id.unarchive_menu_item -> archiveNote()
            }
            true
        }

        prepareNoteView(rootView)
    }

    private fun prepareNoteView(rootView: View) {
        val currentNote = noteEditViewModel.getCurrentNote()
        if (currentNote != null) {
            val noteType: Int = if (noteEditViewModel.noteType != null)
                noteEditViewModel.noteType!!
            else {
                noteEditViewModel.noteType = currentNote.noteType
                currentNote.noteType
            }

            when (noteEditViewModel.noteType) {
                NOTE_ARCHIVED, IMAGE_ARCHIVED, LIST_ARCHIVED, IMAGE_LIST_ARCHIVED -> {
                    rootView.toolbar.menu.findItem(R.id.archive_menu_item).isVisible = false
                    rootView.toolbar.menu.findItem(R.id.unarchive_menu_item).isVisible = true
                }
                else -> {
                    rootView.toolbar.menu.findItem(R.id.archive_menu_item).isVisible = true
                    rootView.toolbar.menu.findItem(R.id.unarchive_menu_item).isVisible = false
                }
            }

            if (currentNote.nId != -1L) {
                noteTitle.setText(currentNote.noteTitle)
                noteEditViewModel.setSelectedColor(currentNote.noteColor)
                val formatter = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
                rootView.last_edited_text.text = getString(
                    R.string.edited_time_stamp,
                    formatter.format(currentNote.dateModified)
                )
            }else{
                val prefs = context!!.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
                noteEditViewModel.setSelectedColor(prefs.getInt(PREF_DEFAULT_NOTE_COLOR, 0))
            }

            noteEditViewModel.reminderTime = currentNote.reminderTime
            Log.d(TAG, "Note type = $noteType")
            when (noteType) {
                LIST_DEFAULT, LIST_ARCHIVED -> {
                    Log.d(TAG, "Note is of List type")
                    checklistView.visibility = VISIBLE
                    noteContent.visibility = GONE
                    imageRecyclerView.visibility = GONE
                    setChecklistContent(currentNote.noteContent)
                }
                IMAGE_DEFAULT, IMAGE_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> {
                    imageRecyclerView.visibility = VISIBLE
                    if (noteType == IMAGE_LIST_DEFAULT || noteType == IMAGE_LIST_ARCHIVED) {
                        checklistView.visibility = VISIBLE
                        noteContent.visibility = GONE
                    } else {
                        checklistView.visibility = GONE
                        noteContent.visibility = VISIBLE
                    }

                    val imageData = Gson().fromJson(currentNote.noteContent, ImageNoteContent::class.java)
                    if (noteType == IMAGE_LIST_DEFAULT || noteType == IMAGE_LIST_ARCHIVED) {
                        setChecklistContent(imageData.noteContent)
                    } else {
                        noteContent.setText(imageData.noteContent)
                    }

                    GlobalScope.launch(Dispatchers.IO) {
                        val idList = if (noteEditViewModel.getImagesList().isNotEmpty()) {
                            val idList1 = ArrayList<Long>()
                            for (item in noteEditViewModel.getImagesList())
                                idList1.add(item.imageId!!)
                            idList1
                        } else {
                            imageData.idList
                        }

                        // Retrieving data on image Ids in note from DB
                        val list = noteEditDatabaseViewModel.getImagesByIds(idList)
                        withContext(Dispatchers.Main) {
                            if (list.isEmpty()) {
                                // All images deleted
                                //TODO: Fix all images deleted and all text removed but note not deleting
                                imageRecyclerView.visibility = GONE
                                noteEditViewModel.noteType = when (noteType) {
                                    IMAGE_ARCHIVED -> NOTE_ARCHIVED
                                    IMAGE_LIST_DEFAULT -> LIST_DEFAULT
                                    IMAGE_LIST_ARCHIVED -> LIST_ARCHIVED
                                    else -> NOTE_DEFAULT
                                }

                                noteEditViewModel.setCurrentNote(
                                    Note(
                                        currentNote.nId,
                                        currentNote.noteTitle,
                                        imageData.noteContent,
                                        currentNote.dateCreated,
                                        currentNote.dateModified,
                                        currentNote.gDriveId,
                                        currentNote.noteType,
                                        currentNote.synced,
                                        currentNote.noteColor,
                                        currentNote.reminderTime
                                    )
                                )
                            } else {
                                imageListAdapter = ImageListAdapter(context!!, list, noteEditViewModel)
                                imageRecyclerView.adapter = imageListAdapter
                                imageRecyclerView.isNestedScrollingEnabled = false
                            }
                            if (noteEditViewModel.getImagesList().isEmpty())
                                noteEditViewModel.setImagesList(list)
                        }
                    }
                }
                else -> {
                    noteContent.setText(currentNote.noteContent)
                    imageRecyclerView.visibility = GONE
                    checklistView.visibility = GONE
                    noteContent.visibility = VISIBLE
                }
            }

            if (currentNote.nId == -1L) {
                noteContent.postDelayed({
                    noteContent.requestFocus()
                    noteContent.setSelection(noteContent.text.length)
                    val imm =
                        context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(noteContent, 0)
                }, 50)
            }
        }
    }

    private fun setChecklistContent(content: String?) {
        if (content != null) {
            val newContent = if (content.contains("[ ]") || content.contains("[x]"))
                ChecklistConverter.convertList(content)
            else
                content
            checklistView.setList(newContent)
        }
    }

    private fun startAddBottomDialog(container: ViewGroup?) {
        val dialogView = layoutInflater.inflate(R.layout.add_bottom_sheet, container, false)
        val dialog = BottomSheetDialog(this@NoteEditFragment.context!!)
        val selectedNote = noteEditViewModel.getCurrentNote()

        if (selectedNote != null) {
            if (noteEditViewModel.reminderTime != -1L) {
                dialogView.cancel_reminder_button.visibility = VISIBLE
                val formatter = SimpleDateFormat("h:mm a MMM d, yyyy", Locale.ENGLISH)
                dialogView.reminder_text.text =
                    getString(R.string.reminder_set, formatter.format(noteEditViewModel.reminderTime))
                dialogView.reminder_text.setTextColor(
                    Color.parseColor(
                        colorsUtil.getColor(
                            noteEditViewModel.getSelectedColor().value
                        )
                    )
                )
                dialogView.cancel_reminder_button.setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle(getString(R.string.cancel_reminder))
                        .setMessage(getString(R.string.cancel_reminder_question))
                        .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                            WorkSchedulerHelper().cancelReminderByNoteId(
                                selectedNote.nId,
                                context!!
                            )
                            noteEditViewModel.reminderTime = -1L
                            dialog.dismiss()
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .show()
                }
                dialogView.cancel_reminder_button.setColorFilter(
                    Color.parseColor(
                        colorsUtil.getColor(
                            noteEditViewModel.getSelectedColor().value
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
                dialog.dismiss()
                pickReminderTime(selectedNote.nId)
            }

            when (noteEditViewModel.noteType) {
                LIST_DEFAULT, LIST_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> {
                    dialogView.checklist_button_text.text = getString(R.string.convert_to_note)
                }
                else -> {
                    dialogView.checklist_button_text.text = getString(R.string.convert_to_checklist)
                }
            }
        }

        dialogView.camera_button.setOnClickListener {
            dialog.dismiss()
            openCamera()
        }

        dialogView.pick_image_button.setOnClickListener {
            dialog.dismiss()
            openPickImage()
        }

        dialogView.checklist_button.setOnClickListener {
            dialog.dismiss()
            convertChecklist()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun convertChecklist() {
        when (noteEditViewModel.noteType) {
            LIST_DEFAULT, LIST_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> {
                // Convert to note
                val listContent = checklistView.toString()
                val str = listContent.replace("□ ", "")
                val newNoteContent = str.replace("✓ ", "")
                noteEditViewModel.noteType = when (noteEditViewModel.noteType) {
                    LIST_ARCHIVED -> NOTE_ARCHIVED
                    IMAGE_LIST_DEFAULT -> IMAGE_DEFAULT
                    IMAGE_LIST_ARCHIVED -> IMAGE_ARCHIVED
                    else -> NOTE_DEFAULT
                }
                checklistView.visibility = GONE
                noteContent.visibility = VISIBLE
                noteContent.setText(newNoteContent)
            }
            else -> {
                // Convert to list
                val noteText = noteContent.text.toString()
                val str = "□ $noteText"
                val newNoteContent = str.replace("\n", "\n□ ")
                noteEditViewModel.noteType = when (noteEditViewModel.noteType) {
                    NOTE_ARCHIVED -> LIST_ARCHIVED
                    IMAGE_DEFAULT -> IMAGE_LIST_DEFAULT
                    IMAGE_ARCHIVED -> IMAGE_LIST_ARCHIVED
                    else -> LIST_DEFAULT
                }
                checklistView.visibility = VISIBLE
                noteContent.visibility = GONE
                checklistView.setList(newNoteContent)
            }
        }
    }

    private fun startBottomSheetDialog(container: ViewGroup?) {
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet, container, false)
        val dialog = BottomSheetDialog(context!!)

        dialogView.share_button.setOnClickListener {
            dialog.dismiss()
            shareNote()
        }

        dialogView.delete_button.setOnClickListener {
            dialog.dismiss()
            deleteNote()
        }

        dialogView.discard_changes_button.setOnClickListener {
            val note: Note? = noteEditViewModel.getCurrentNote()
            if (note != null) {
                AlertDialog.Builder(activity)
                    .setTitle("Discard changes")
                    .setMessage("Discard all changes to the note? Deleted images won't be restored.")
                    .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                        discardChanges(note)
                        dialog.dismiss()
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .setCancelable(true)
                    .show()
            }
        }

        dialogView.make_copy_button.setOnClickListener {
            noteEditDatabaseViewModel.makeCopy(
                noteEditViewModel.getCurrentNote(),
                noteEditViewModel.noteType,
                noteTitle.text.toString(),
                getNoteText()
            )
            dialog.dismiss()
            Toast.makeText(context, getString(R.string.toast_copy_done), LENGTH_SHORT).show()
        }

        val layoutManager = LinearLayoutManager(context!!, RecyclerView.HORIZONTAL, false)
        dialogView.color_picker.layoutManager = layoutManager
        dialogView.color_picker.adapter = ColorPickerAdapter(context!!, noteEditViewModel)
        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun discardChanges(note: Note) {
        noteTitle.setText(note.noteTitle)
        // Getting the content from original note
        val contentText = when (noteEditViewModel.noteType) {
            IMAGE_DEFAULT, IMAGE_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> {
                val imageData = Gson().fromJson(note.noteContent, ImageNoteContent::class.java)
                imageData.noteContent
            }
            else -> note.noteContent
        }

        noteEditViewModel.setSelectedColor(note.noteColor)
        // Changing the views according to original note
        when (note.noteType) {
            LIST_DEFAULT, LIST_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> {
                checklistView.visibility = VISIBLE
                noteContent.visibility = GONE
            }
            else -> {
                checklistView.visibility = GONE
                noteContent.visibility = VISIBLE
            }
        }

        // Converting current note type according to original note type
        when (note.noteType) {
            LIST_DEFAULT, IMAGE_LIST_DEFAULT -> {
                if (isImageType(noteEditViewModel.noteType!!))
                    noteEditViewModel.noteType = IMAGE_LIST_DEFAULT
                else
                    noteEditViewModel.noteType = LIST_DEFAULT
            }
            LIST_ARCHIVED, IMAGE_LIST_ARCHIVED -> {
                if (isImageType(noteEditViewModel.noteType!!))
                    noteEditViewModel.noteType = IMAGE_LIST_ARCHIVED
                else
                    noteEditViewModel.noteType = LIST_ARCHIVED
            }
            NOTE_DEFAULT, IMAGE_DEFAULT -> {
                if (isImageType(noteEditViewModel.noteType!!))
                    noteEditViewModel.noteType = IMAGE_DEFAULT
                else
                    noteEditViewModel.noteType = NOTE_DEFAULT
            }
            NOTE_ARCHIVED, IMAGE_ARCHIVED -> {
                if (isImageType(noteEditViewModel.noteType!!))
                    noteEditViewModel.noteType = IMAGE_ARCHIVED
                else
                    noteEditViewModel.noteType = NOTE_ARCHIVED
            }
        }

        // Setting the original note details
        when (noteEditViewModel.noteType) {
            LIST_DEFAULT, LIST_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> {
                setChecklistContent(contentText)
            }
            else -> noteContent.setText(contentText)
        }
    }

    private fun isImageType(noteType: Int): Boolean {
        return when (noteType) {
            IMAGE_DEFAULT -> true
            IMAGE_ARCHIVED -> true
            IMAGE_TRASH -> true
            IMAGE_LIST_DEFAULT -> true
            IMAGE_LIST_ARCHIVED -> true
            IMAGE_LIST_TRASH -> true
            else -> false
        }
    }

    private fun shareNote() {
        var shareIntent: Intent
        val shareText = when (noteEditViewModel.noteType) {
            LIST_DEFAULT, LIST_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> {
                checklistView.toString()
            }
            else -> {
                noteContent.text.toString()
            }
        }

        // Sharing a note
        if (noteEditViewModel.noteType == IMAGE_DEFAULT || noteEditViewModel.noteType == IMAGE_LIST_DEFAULT || noteEditViewModel.noteType == IMAGE_ARCHIVED || noteEditViewModel.noteType == IMAGE_LIST_ARCHIVED) {
            // Sharing image type note
            shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
            shareIntent.type = "*/*"
            GlobalScope.launch(Dispatchers.IO) {
                val list = getUriList()
                if(list == null){
                    shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "text/plain"
                }else {
                    shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
                }
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, noteTitle.text.toString())
                withContext(Dispatchers.Main) {
                    startActivity(Intent.createChooser(shareIntent, "Share..."))
                }
            }
        } else {
            // Sharing text note
            shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, noteTitle.text.toString())
            startActivity(Intent.createChooser(shareIntent, "Share..."))
        }
    }

    private fun getUriList(): ArrayList<Uri>? {
        val list = imageListAdapter?.list ?: return null
        var bitmap: Bitmap
        val folder = File(activity!!.cacheDir, "images")
        folder.mkdirs()
        val uriList = ArrayList<Uri>()
        for (imageData in list.withIndex()) {
            val file = File(imageData.value.imagePath)
            bitmap = BitmapFactory.decodeFile(file.absolutePath)
            uriList.add(getUriForBitmap(folder, bitmap, imageData.index))
            bitmap?.recycle()
        }
        return uriList
    }

    private fun getUriForBitmap(folder: File, bitmap: Bitmap, index: Int): Uri {
        val file = File(folder, "$index.png")
        if (file.exists())
            file.delete()
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return FileProvider.getUriForFile(context!!, Contract.FILE_PROVIDER_AUTHORITY, file)
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
                    WorkSchedulerHelper().setReminder(noteId, cal.timeInMillis, context!!)
                    noteEditViewModel.reminderTime = cal.timeInMillis
                } else {
                    Toast.makeText(
                        activity,
                        "Reminder cannot be set before present time",
                        LENGTH_SHORT
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
        val selectedNote = noteEditViewModel.getCurrentNote()
        if (selectedNote != null && selectedNote.nId != -1L) {
            noteEditViewModel.noteType = when (noteEditViewModel.noteType) {
                NOTE_DEFAULT -> NOTE_ARCHIVED
                LIST_DEFAULT -> LIST_ARCHIVED
                IMAGE_DEFAULT -> IMAGE_ARCHIVED
                IMAGE_LIST_DEFAULT -> IMAGE_LIST_ARCHIVED
                NOTE_ARCHIVED -> NOTE_DEFAULT
                LIST_ARCHIVED -> LIST_DEFAULT
                IMAGE_ARCHIVED -> IMAGE_DEFAULT
                IMAGE_LIST_ARCHIVED -> IMAGE_LIST_DEFAULT
                else -> -1
            }

            saveNote(getNoteText())
            activity?.finish()
        }
    }

    private fun deleteNote() {
        AlertDialog.Builder(context)
            .setTitle(getString(R.string.delete_note))
            .setMessage(getString(R.string.delete_question))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                val selectedNote = noteEditViewModel.getCurrentNote()
                if (selectedNote?.nId == -1L) {
                    if (isImageType(selectedNote.noteType)) {
                        val idsList = ArrayList<Long>()
                        for (imageData in noteEditViewModel.getImagesList())
                            idsList.add(imageData.imageId!!)
                        noteEditDatabaseViewModel.deleteImagesByIds(idsList)
                        noteEditViewModel.setImagesList(null)
                    }
                } else {
                    if (selectedNote != null) {
                        val noteType = when (noteEditViewModel.noteType) {
                            IMAGE_DEFAULT, IMAGE_ARCHIVED -> {
                                IMAGE_TRASH
                            }
                            LIST_DEFAULT, LIST_ARCHIVED -> {
                                LIST_TRASH
                            }
                            IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> {
                                IMAGE_LIST_TRASH
                            }
                            else -> {
                                NOTE_TRASH
                            }
                        }

                        noteEditDatabaseViewModel.insert(
                            Note(
                                selectedNote.nId,
                                selectedNote.noteTitle,
                                selectedNote.noteContent,
                                selectedNote.dateCreated,
                                Calendar.getInstance().timeInMillis,
                                selectedNote.gDriveId,
                                noteType,
                                selectedNote.synced,
                                noteEditViewModel.getSelectedColor().value,
                                -1L
                            )
                        )

                        if (selectedNote.reminderTime != -1L) {
                            WorkSchedulerHelper().cancelReminderByNoteId(
                                selectedNote.nId,
                                context!!
                            )
                        }
                    }
                }

                activity?.finish()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val path = activity!!.filesDir.toString()
        val time = Calendar.getInstance().timeInMillis
        return File(path, "$time.jpg")
    }

    private fun openCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(
                context!!,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Storage permission required", LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(
                activity!!,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1010
            )
        } else {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(activity!!.packageManager) != null) {
                val photoFile: File? = try {
                    createImageFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                    null
                }
                if (photoFile != null) {
                    noteEditViewModel.setCurrentPhotoPath(photoFile.absolutePath)
                    val photoUri = FileProvider.getUriForFile(
                        context!!,
                        "com.infinitysolutions.notessync.fileprovider",
                        photoFile
                    )
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    startActivityForResult(intent, IMAGE_CAPTURE_REQUEST_CODE)
                } else
                    Toast.makeText(context, "Couldn't access file system", LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, getString(R.string.toast_no_camera_app), LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun openPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(
                context!!,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Storage permission required", LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(
                activity!!,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1010
            )
        } else {
            val i = Intent(Intent.ACTION_PICK)
            i.type = "image/*"
            startActivityForResult(i, IMAGE_PICKER_REQUEST_CODE)
        }
    }

    private fun getNoteText(): String {
        return when (noteEditViewModel.noteType) {
            LIST_DEFAULT, LIST_ARCHIVED -> {
                checklistView.toString()
            }
            IMAGE_DEFAULT, IMAGE_ARCHIVED -> {
                Gson().toJson(
                    ImageNoteContent(
                        noteContent.text.toString(),
                        imageListAdapter?.getIdsList() ?: ArrayList()
                    )
                )
            }
            IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED -> {
                Gson().toJson(
                    ImageNoteContent(
                        checklistView.toString(),
                        imageListAdapter?.getIdsList() ?: ArrayList()
                    )
                )
            }
            else -> {
                noteContent.text.toString()
            }
        }
    }

    private fun loadImage(imageData: ImageData) {
        imageRecyclerView.visibility = VISIBLE

        val selectedNote = noteEditViewModel.getCurrentNote()
        if (selectedNote != null) {
            val selectedNoteContent =
                if (noteEditViewModel.noteType != null && isImageType(noteEditViewModel.noteType!!))
                    Gson().fromJson(
                        selectedNote.noteContent,
                        ImageNoteContent::class.java
                    ).noteContent
                else
                    selectedNote.noteContent

            when (noteEditViewModel.noteType) {
                NOTE_DEFAULT, LIST_DEFAULT, NOTE_ARCHIVED, LIST_ARCHIVED -> {
                    imageListAdapter = ImageListAdapter(context!!, ArrayList(), noteEditViewModel)
                    imageRecyclerView.adapter = imageListAdapter
                }
            }

            noteEditViewModel.noteType = when(noteEditViewModel.noteType){
                NOTE_DEFAULT -> IMAGE_DEFAULT
                LIST_DEFAULT -> IMAGE_LIST_DEFAULT
                NOTE_ARCHIVED -> IMAGE_ARCHIVED
                LIST_ARCHIVED -> IMAGE_LIST_ARCHIVED
                else -> noteEditViewModel.noteType
            }

            val noteText = Gson().toJson(
                ImageNoteContent(
                    selectedNoteContent,
                    arrayListOf(imageData.imageId!!)
                )
            )
            noteEditViewModel.setCurrentNote(
                Note(
                    selectedNote.nId,
                    selectedNote.noteTitle,
                    noteText,
                    selectedNote.dateCreated,
                    selectedNote.dateModified,
                    selectedNote.gDriveId,
                    selectedNote.noteType,
                    selectedNote.synced,
                    selectedNote.noteColor,
                    selectedNote.reminderTime
                )
            )
        }

        imageRecyclerView.isNestedScrollingEnabled = false
        imageListAdapter?.addImage(imageData)
        noteEditViewModel.addImageToImageList(imageData)
    }

    private fun saveBitmap(imageBitmap: Bitmap, filePath: String) {
        val file = File(filePath)
        try {
            val fos = FileOutputStream(file)
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        imageBitmap.recycle()
    }

    private fun exifRotateBitmap(filePath: String?, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(filePath!!)
        val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1F, 1F)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180F)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180F)
                matrix.postScale(-1F, 1F)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90F)
                matrix.postScale(-1F, 1F)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90F)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90F)
                matrix.postScale(-1F, 1F)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90F)
            else -> return bitmap
        }

        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return result
    }

    private fun loadBitmap(uri: Uri?, filePath: String?, destinationPath: String): Boolean {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        try {
            if (uri != null) {
                val imageStream = activity!!.contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(imageStream, null, options)
            } else if (filePath != null) {
                BitmapFactory.decodeFile(filePath, options)
            }
        } catch (e: Exception) {
            return false
        }

        var width = options.outWidth
        var height = options.outHeight

        var inSampleSize = 1
        if (width > 1000 || height > 1000) {
            height /= 2
            width /= 2
            while (height / inSampleSize >= 1000 && width / inSampleSize >= 1000)
                inSampleSize *= 2
        }

        options.inSampleSize = inSampleSize
        options.inJustDecodeBounds = false
        var imageBitmap: Bitmap?
        if (uri != null) {
            // Retrieving the bitmap from given uri
            imageBitmap = Glide.with(context!!)
                .asBitmap()
                .load(uri)
                .submit(width, height)
                .get()
        } else {
            // Retrieving the bitmap from given file path
            imageBitmap = BitmapFactory.decodeFile(filePath, options)
            imageBitmap = exifRotateBitmap(filePath, imageBitmap)
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists())
                    file.delete()
            }
        }

        // Saving the bitmap to given path
        if (imageBitmap != null)
            saveBitmap(imageBitmap, destinationPath)
        return true
    }

    private fun insertImageInDatabase(photoUri: Uri?, filePath: String?) {
        val noteEditDatabaseViewModel = ViewModelProviders.of(activity!!).get(NoteEditDatabaseViewModel::class.java)
        val noteEditViewModel = ViewModelProviders.of(activity!!).get(NoteEditViewModel::class.java)

        GlobalScope.launch(Dispatchers.IO) {
            val imageData = noteEditDatabaseViewModel.insertImage()
            withContext(Dispatchers.Main) {
                loadImage(imageData)
            }
            val isLoadSuccess = loadBitmap(photoUri, filePath, imageData.imagePath)
            // If there is a problem retrieving the image then delete the empty entry
            if (!isLoadSuccess)
                noteEditDatabaseViewModel.deleteImage(imageData.imageId!!, imageData.imagePath)
            // Notify the changes to the view
            withContext(Dispatchers.Main) {
                if(!isLoadSuccess)
                    Toast.makeText(context, "Error in retrieving image", LENGTH_SHORT).show()
                noteEditViewModel.setRefreshImagesList(true)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == IMAGE_PICKER_REQUEST_CODE) {
                val photoUri: Uri? = data?.data
                if (photoUri != null)
                    insertImageInDatabase(photoUri, null)
                else
                    Toast.makeText(context, "Can't access storage", LENGTH_SHORT).show()
            } else if (requestCode == IMAGE_CAPTURE_REQUEST_CODE) {
                if (noteEditViewModel.getCurrentPhotoPath() != null) {
                    val photoFile = File(noteEditViewModel.getCurrentPhotoPath()!!)
                    if (photoFile.exists())
                        insertImageInDatabase(null, photoFile.absolutePath)
                    else
                        Toast.makeText(context, "Error in retrieving image", LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveNote(content: String) {
        val timeModified = Calendar.getInstance().timeInMillis
        val currentNote = noteEditViewModel.getCurrentNote()
        if (currentNote != null) {
            if (currentNote.nId == -1L) {
                Log.d(TAG, "It is a new Note")
                // New Note
                if (content.isNotEmpty() || noteTitle.text.isNotEmpty()) {
                    noteEditDatabaseViewModel.insert(
                        Note(
                            null,
                            noteTitle.text.toString(),
                            content,
                            timeModified,
                            timeModified,
                            "-1",
                            noteEditViewModel.noteType!!,
                            false,
                            noteEditViewModel.getSelectedColor().value,
                            -1L
                        )
                    )
                }
            } else {
                // Old Note
                if (content.isEmpty() && noteTitle.text.isEmpty()) {
                    // If the user removed everything from the note
                    noteEditDatabaseViewModel.deleteNote(currentNote)
                } else {
                    noteEditDatabaseViewModel.insert(
                        Note(
                            currentNote.nId,
                            noteTitle.text.toString(),
                            content,
                            currentNote.dateCreated,
                            timeModified,
                            currentNote.gDriveId,
                            noteEditViewModel.noteType!!,
                            currentNote.synced,
                            noteEditViewModel.getSelectedColor().value,
                            noteEditViewModel.reminderTime
                        )
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (::noteEditViewModel.isInitialized) {
            val currentNote = noteEditViewModel.getCurrentNote()
            if (currentNote != null) {
                val noteContentText = getNoteText()
                if (!activity!!.isChangingConfigurations) {
                    if ((currentNote.noteContent != noteContentText)
                        || (currentNote.noteType != noteEditViewModel.noteType)
                        || (currentNote.noteTitle != noteTitle.text.toString())
                        || (currentNote.noteColor != noteEditViewModel.getSelectedColor().value)
                        || (currentNote.reminderTime != noteEditViewModel.reminderTime)
                    ) {
                        saveNote(noteContentText)
                    }
                }
            }
        }
        super.onDestroy()
    }
}
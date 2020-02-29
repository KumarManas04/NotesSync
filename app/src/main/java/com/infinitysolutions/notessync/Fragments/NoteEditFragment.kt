package com.infinitysolutions.notessync.Fragments


import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
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
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_CAPTURE_REQUEST_CODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_LIST_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_LIST_TRASH
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_PICKER_REQUEST_CODE
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
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
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
        rootView.open_bottom_menu.setOnClickListener {
            startBottomSheetDialog(container)
        }
        rootView.open_add_bottom_menu.setOnClickListener {
            startAddBottomDialog(container)
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
        imageRecyclerView.layoutManager = LinearLayoutManager(context, HORIZONTAL, false)

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

        toolbar.setNavigationOnClickListener {
            findNavController(this).navigateUp()
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.archive_menu_item, R.id.unarchive_menu_item -> archiveNote()
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
            val noteType: Int = if(mainViewModel.noteType != null)
                mainViewModel.noteType!!
            else {
                mainViewModel.noteType = selectedNote.noteType
                selectedNote.noteType
            }

            when(mainViewModel.noteType){
                NOTE_ARCHIVED, IMAGE_ARCHIVED, LIST_ARCHIVED, IMAGE_LIST_ARCHIVED ->{
                    rootView.toolbar.menu.findItem(R.id.archive_menu_item).isVisible = false
                    rootView.toolbar.menu.findItem(R.id.unarchive_menu_item).isVisible = true
                }
                else ->{
                    rootView.toolbar.menu.findItem(R.id.archive_menu_item).isVisible = true
                    rootView.toolbar.menu.findItem(R.id.unarchive_menu_item).isVisible = false
                }
            }

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
            when(noteType) {
                LIST_DEFAULT ,LIST_ARCHIVED ->{
                    checklistView.visibility = VISIBLE
                    noteContent.visibility = GONE
                    imageRecyclerView.visibility = GONE
                    setChecklistContent(selectedNote.noteContent)
                }
                IMAGE_DEFAULT ,IMAGE_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED ->{
                    imageRecyclerView.visibility = VISIBLE
                    if(noteType == IMAGE_LIST_DEFAULT || noteType == IMAGE_LIST_ARCHIVED) {
                        checklistView.visibility = VISIBLE
                        noteContent.visibility = GONE
                    }else {
                        checklistView.visibility = GONE
                        noteContent.visibility = VISIBLE
                    }

                    val imageData = Gson().fromJson(selectedNote.noteContent, ImageNoteContent::class.java)
                    if(noteType == IMAGE_LIST_DEFAULT || noteType == IMAGE_LIST_ARCHIVED) {
                        setChecklistContent(imageData.noteContent)
                    }else{
                        noteContent.setText(imageData.noteContent)
                    }

                    GlobalScope.launch(Dispatchers.IO) {
                        val idList = if (mainViewModel.getImagesList().isNotEmpty()) {
                            val idList1 = ArrayList<Long>()
                            for (item in mainViewModel.getImagesList())
                                idList1.add(item.imageId!!)
                            idList1
                        } else {
                            imageData.idList
                        }

                        val list = databaseViewModel.getImagesByIds(idList)
                        withContext(Dispatchers.Main) {
                            if (list.isEmpty()) {
                                imageRecyclerView.visibility = GONE
                                when (noteType) {
                                    IMAGE_DEFAULT -> mainViewModel.noteType = NOTE_DEFAULT
                                    IMAGE_ARCHIVED -> mainViewModel.noteType = NOTE_ARCHIVED
                                    IMAGE_LIST_DEFAULT -> mainViewModel.noteType = LIST_DEFAULT
                                    IMAGE_LIST_ARCHIVED -> mainViewModel.noteType = LIST_ARCHIVED
                                }
                                mainViewModel.setSelectedNote(Note(
                                    selectedNote.nId,
                                    selectedNote.noteTitle,
                                    imageData.noteContent,
                                    selectedNote.dateCreated,
                                    selectedNote.dateModified,
                                    selectedNote.gDriveId,
                                    selectedNote.noteType,
                                    selectedNote.synced,
                                    selectedNote.noteColor,
                                    selectedNote.reminderTime
                                ))
                            } else {
                                imageRecyclerView.adapter = ImageListAdapter(context!!, list, mainViewModel)
                                imageRecyclerView.isNestedScrollingEnabled = false
                            }
                            if (mainViewModel.getImagesList().isEmpty())
                                mainViewModel.setImagesList(list)
                        }
                    }
                }
                else ->{
                    noteContent.setText(selectedNote.noteContent)
                    imageRecyclerView.visibility = GONE
                    checklistView.visibility = GONE
                    noteContent.visibility = VISIBLE
                }
            }

            if (selectedNote.nId == -1L){
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

    private fun setChecklistContent(content: String?){
        if (content != null) {
            val newContent = if (content.contains("[ ]") || content.contains("[x]"))
                ChecklistConverter.convertList(content)
            else
                content
            checklistView.setList(newContent)
        }
    }

    private fun startAddBottomDialog(container: ViewGroup?){
        val dialogView = layoutInflater.inflate(R.layout.add_bottom_sheet, container, false)
        val dialog = BottomSheetDialog(this@NoteEditFragment.context!!)
        val selectedNote = mainViewModel.getSelectedNote()

        if (selectedNote != null) {
            if (mainViewModel.reminderTime != -1L) {
                dialogView.cancel_reminder_button.visibility = VISIBLE
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
                        .setTitle(getString(R.string.cancel_reminder))
                        .setMessage(getString(R.string.cancel_reminder_question))
                        .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                            WorkSchedulerHelper().cancelReminderByNoteId(selectedNote.nId, context!!)
                            mainViewModel.reminderTime = -1L
                            dialog.hide()
                        }
                        .setNegativeButton(getString(R.string.no), null)
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

            when(mainViewModel.noteType){
                LIST_DEFAULT, LIST_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED ->{
                    dialogView.checklist_button_text.text = "Convert to note"
                }
                else ->{
                    dialogView.checklist_button_text.text = "Convert to checklist"
                }
            }
        }

        dialogView.camera_button.setOnClickListener {
            dialog.hide()
            openCamera()
        }

        dialogView.pick_image_button.setOnClickListener {
            dialog.hide()
            openPickImage()
        }

        dialogView.checklist_button.setOnClickListener {
            convertChecklist()
            dialog.hide()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun convertChecklist(){
        when(mainViewModel.noteType){
            LIST_DEFAULT, LIST_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED ->{
                // Convert to note
                val listContent = checklistView.toString()
                val str = listContent.replace("□ ", "")
                val newNoteContent = str.replace("✓ ", "")
                mainViewModel.noteType = when(mainViewModel.noteType){
                    LIST_ARCHIVED -> NOTE_ARCHIVED
                    IMAGE_LIST_DEFAULT -> IMAGE_DEFAULT
                    IMAGE_LIST_ARCHIVED -> IMAGE_ARCHIVED
                    else -> NOTE_DEFAULT
                }
                checklistView.visibility = GONE
                noteContent.visibility = VISIBLE
                noteContent.setText(newNoteContent)
            }
            else ->{
                // Convert to list
                val noteText = noteContent.text.toString()
                val str = "□ $noteText"
                val newNoteContent = str.replace("\n", "\n□ ")
                mainViewModel.noteType = when(mainViewModel.noteType){
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
        val dialog = BottomSheetDialog(this@NoteEditFragment.context!!)

        dialogView.share_button.setOnClickListener {
            shareNote()
        }

        dialogView.delete_button.setOnClickListener {
            deleteNote()
        }

        dialogView.discard_changes_button.setOnClickListener {
            val note: Note? = mainViewModel.getSelectedNote()
            if(note != null) {
                AlertDialog.Builder(activity)
                    .setTitle("Discard changes")
                    .setMessage("Discard all changes to the note? Deleted images won't be restored.")
                    .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                        discardChanges(note)
                        dialog.hide()
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .setCancelable(true)
                    .show()
            }
        }

        dialogView.make_copy_button.setOnClickListener {
            databaseViewModel.makeCopy(mainViewModel.getSelectedNote(), mainViewModel.noteType, noteTitle.text.toString(), getNoteText())
            dialog.hide()
            Toast.makeText(context, getString(R.string.toast_copy_done), LENGTH_SHORT).show()
        }

        val layoutManager = LinearLayoutManager(this@NoteEditFragment.context!!, RecyclerView.HORIZONTAL, false)
        dialogView.color_picker.layoutManager = layoutManager
        dialogView.color_picker.adapter = ColorPickerAdapter(this@NoteEditFragment.context!!, mainViewModel)
        dialog.setContentView(dialogView)
        dialog.show()
    }

    private fun discardChanges(note: Note){
        noteTitle.setText(note.noteTitle)
        // Getting the content from original note
        val contentText = when(mainViewModel.noteType){
            IMAGE_DEFAULT, IMAGE_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED ->{
                val imageData = Gson().fromJson(note.noteContent, ImageNoteContent::class.java)
                imageData.noteContent
            }
            else -> note.noteContent
        }

        mainViewModel.setSelectedColor(note.noteColor)
        // Changing the views according to original note
        when(note.noteType){
            LIST_DEFAULT, LIST_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED ->{
                checklistView.visibility = VISIBLE
                noteContent.visibility = GONE
            }
            else ->{
                checklistView.visibility = GONE
                noteContent.visibility = VISIBLE
            }
        }

        // Converting current note type according to original note type
        when(note.noteType){
            LIST_DEFAULT, IMAGE_LIST_DEFAULT->{
                if(isImageType(mainViewModel.noteType!!))
                    mainViewModel.noteType = IMAGE_LIST_DEFAULT
                else
                    mainViewModel.noteType = LIST_DEFAULT
            }
            LIST_ARCHIVED, IMAGE_LIST_ARCHIVED ->{
                if(isImageType(mainViewModel.noteType!!))
                    mainViewModel.noteType = IMAGE_LIST_ARCHIVED
                else
                    mainViewModel.noteType = LIST_ARCHIVED
            }
            NOTE_DEFAULT, IMAGE_DEFAULT ->{
                if(isImageType(mainViewModel.noteType!!))
                    mainViewModel.noteType = IMAGE_DEFAULT
                else
                    mainViewModel.noteType = NOTE_DEFAULT
            }
            NOTE_ARCHIVED, IMAGE_ARCHIVED ->{
                if(isImageType(mainViewModel.noteType!!))
                    mainViewModel.noteType = IMAGE_ARCHIVED
                else
                    mainViewModel.noteType = NOTE_ARCHIVED
            }
        }

        // Setting the original note details
        when(mainViewModel.noteType){
            LIST_DEFAULT, LIST_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED ->{
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

    private fun shareNote(){
        val shareIntent: Intent
        val shareText = when(mainViewModel.noteType){
            LIST_DEFAULT, LIST_ARCHIVED, IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED ->{
                checklistView.toString()
            }
            else ->{
                noteContent.text.toString()
            }
        }

        if(mainViewModel.noteType == IMAGE_DEFAULT || mainViewModel.noteType == IMAGE_LIST_DEFAULT || mainViewModel.noteType == IMAGE_ARCHIVED || mainViewModel.noteType == IMAGE_LIST_ARCHIVED){
            shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
            shareIntent.type = "*/*"
            GlobalScope.launch(Dispatchers.IO) {
                val list = getUriList()
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, list)
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, noteTitle.text.toString())
                withContext(Dispatchers.Main){
                    startActivity(Intent.createChooser(shareIntent, "Share..."))
                }
            }
        }else{
            shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, noteTitle.text.toString())
            startActivity(Intent.createChooser(shareIntent, "Share..."))
        }
    }

    private fun getUriList(): ArrayList<Uri>{
        val list = (imageRecyclerView.adapter as ImageListAdapter).list
        var bitmap: Bitmap
        val folder = File(activity!!.cacheDir, "images")
        folder.mkdirs()
        val uriList = ArrayList<Uri>()
        for(imageData in list.withIndex()){
            val file = File(imageData.value.imagePath)
            bitmap = BitmapFactory.decodeFile(file.absolutePath)
            uriList.add(getUriForBitmap(folder, bitmap, imageData.index))
            bitmap?.recycle()
        }
        return uriList
    }

    private fun getUriForBitmap(folder: File, bitmap: Bitmap, index: Int): Uri{
        val file = File(folder, "$index.png")
        if(file.exists())
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
                    mainViewModel.reminderTime = cal.timeInMillis
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
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null && selectedNote.nId != -1L) {
            val noteType: Int = when (mainViewModel.noteType) {
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
                            mainViewModel.noteType!!,
                            false,
                            mainViewModel.getSelectedColor().value,
                            -1L
                        )
                    )
                }
            } else {
                databaseViewModel.insert(
                    Note(
                        selectedNote.nId,
                        noteTitle.text.toString(),
                        content,
                        selectedNote.dateCreated,
                        timeModified,
                        selectedNote.gDriveId,
                        mainViewModel.noteType!!,
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
            .setTitle(getString(R.string.delete_note))
            .setMessage(getString(R.string.delete_question))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                val selectedNote = mainViewModel.getSelectedNote()
                if (selectedNote?.nId == -1L) {
                    mainViewModel.setSelectedNote(null)
                } else {
                    if (selectedNote != null) {
                        val noteType = when(mainViewModel.noteType){
                            IMAGE_DEFAULT, IMAGE_ARCHIVED ->{
                                IMAGE_TRASH
                            }
                            LIST_DEFAULT, LIST_ARCHIVED ->{
                                LIST_TRASH
                            }
                            IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED ->{
                                IMAGE_LIST_TRASH
                            }
                            else ->{
                                NOTE_TRASH
                            }
                        }

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
                            WorkSchedulerHelper().cancelReminderByNoteId(selectedNote.nId, context!!)
                        }
                    }
                }
                findNavController(this).navigateUp()
            }
            .setNegativeButton(getString(R.string.no), null)
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

    private fun openCamera(){
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
                startActivityForResult(intent, IMAGE_CAPTURE_REQUEST_CODE)
            } else
                Toast.makeText(context, "Couldn't access file system", LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, getString(R.string.toast_no_camera_app), LENGTH_SHORT).show()
        }
    }

    private fun openPickImage(){
        val i = Intent(Intent.ACTION_PICK)
        i.type = "image/*"
        startActivityForResult(i, IMAGE_PICKER_REQUEST_CODE)
    }

    private fun getNoteText(): String {
        return when(mainViewModel.noteType){
            LIST_DEFAULT, LIST_ARCHIVED ->{
                checklistView.toString()
            }
            IMAGE_DEFAULT, IMAGE_ARCHIVED ->{
                Gson().toJson(ImageNoteContent(noteContent.text.toString(), (imageRecyclerView.adapter as ImageListAdapter).getIdsList()))
            }
            IMAGE_LIST_DEFAULT, IMAGE_LIST_ARCHIVED ->{
                Gson().toJson(ImageNoteContent(checklistView.toString(), (imageRecyclerView.adapter as ImageListAdapter).getIdsList()))
            }
            else ->{
                noteContent.text.toString()
            }
        }
    }

    override fun onDestroy() {
        if(::mainViewModel.isInitialized) {
            val selectedNote = mainViewModel.getSelectedNote()
            if (selectedNote != null) {
                val noteContentText = getNoteText()
                if (!activity!!.isChangingConfigurations) {
                    if ((selectedNote.noteContent != noteContentText)
                        || (selectedNote.noteType != mainViewModel.noteType)
                        || (selectedNote.noteTitle != noteTitle.text.toString())
                        || (selectedNote.noteColor != mainViewModel.getSelectedColor().value)
                        || (selectedNote.reminderTime != mainViewModel.reminderTime)
                    ) {
                        saveNote(noteContentText)
                    }
                    mainViewModel.setImagesList(null)
                    mainViewModel.noteType = null
                }
            }
        }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
            var bitmap: Bitmap? = null
            if (requestCode == IMAGE_PICKER_REQUEST_CODE) {
                val uri: Uri? = data?.data
                if (uri != null) {
                    val imageStream = activity!!.contentResolver.openInputStream(uri)
                    bitmap = BitmapFactory.decodeStream(imageStream)
                }
            } else if (requestCode == IMAGE_CAPTURE_REQUEST_CODE) {
                bitmap = BitmapFactory.decodeFile(mainViewModel.getCurrentPhotoPath())
                if (mainViewModel.getCurrentPhotoPath() != null) {
                    val file = File(mainViewModel.getCurrentPhotoPath()!!)
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
                    val imageData = databaseViewModel.insertImage(bitmap)
                    bitmap.recycle()

                    withContext(Dispatchers.Main) {
                        imageRecyclerView.visibility = VISIBLE

                        val selectedNote = mainViewModel.getSelectedNote()
                        if(selectedNote != null) {
                            when (mainViewModel.noteType) {
                                NOTE_DEFAULT -> {
                                    mainViewModel.noteType = IMAGE_DEFAULT
                                    imageRecyclerView.adapter = ImageListAdapter(context!!, ArrayList(), mainViewModel)
                                }
                                LIST_DEFAULT -> {
                                    mainViewModel.noteType = IMAGE_LIST_DEFAULT
                                    imageRecyclerView.adapter = ImageListAdapter(context!!, ArrayList(), mainViewModel)
                                }
                                NOTE_ARCHIVED -> {
                                    mainViewModel.noteType = IMAGE_ARCHIVED
                                    imageRecyclerView.adapter = ImageListAdapter(context!!, ArrayList(), mainViewModel)
                                }
                                LIST_ARCHIVED -> {
                                    mainViewModel.noteType = IMAGE_LIST_ARCHIVED
                                    imageRecyclerView.adapter = ImageListAdapter(context!!, ArrayList(), mainViewModel)
                                }
                            }

                            val noteText = Gson().toJson(ImageNoteContent(selectedNote.noteContent, arrayListOf(imageData.imageId!!)))
                            mainViewModel.setSelectedNote(
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
                        (imageRecyclerView.adapter as ImageListAdapter).addImage(imageData)
                        mainViewModel.addImageToImageList(imageData)
                        dialog.dismiss()
                    }
                }
            }
        }
    }
}
package com.infinitysolutions.notessync.Fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.NavHostFragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.gson.Gson
import com.infinitysolutions.notessync.Adapters.NotesAdapter
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_PROVIDER_AUTHORITY
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_CAPTURE_REQUEST_CODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_PICKER_REQUEST_CODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_ID_EXTRA
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_COMPACT_VIEW_MODE_ENABLED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.WIDGET_BUTTON_EXTRA
import com.infinitysolutions.notessync.Contracts.Contract.Companion.WIDGET_NEW_IMAGE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.WIDGET_NEW_LIST
import com.infinitysolutions.notessync.Contracts.Contract.Companion.WIDGET_NEW_NOTE
import com.infinitysolutions.notessync.Model.ImageNoteContent
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.fragment_main.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainFragment : Fragment() {
    private val TAG = "MainFragment"
    private lateinit var mainViewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_main, container, false)
        initDataBinding(rootView)

        rootView.search_button.setOnClickListener {
            findNavController(this).navigate(R.id.action_mainFragment_to_searchFragment)
        }
        return rootView
    }

    private fun initDataBinding(rootView: View) {
        val databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        val toolbar = rootView.toolbar
        toolbar.title = "All"
        toolbar.inflateMenu(R.menu.main_fragment_menu)

        val notesRecyclerView = rootView.notes_recycler_view
        val prefs = activity!!.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs.contains(PREF_COMPACT_VIEW_MODE_ENABLED) && !prefs.getBoolean(PREF_COMPACT_VIEW_MODE_ENABLED, true)) {
            notesRecyclerView.layoutManager = LinearLayoutManager(activity!!, LinearLayoutManager.VERTICAL, false)
            toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = false
            toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = true
        } else {
            val columnCount = resources.getInteger(R.integer.columns_count)
            notesRecyclerView.layoutManager = StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL)
            toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = true
            toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = false
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sync_menu_item -> {
                    syncFiles()
                }
                R.id.compact_view_menu_item -> {
                    val editor = prefs.edit()
                    val columnCount = resources.getInteger(R.integer.columns_count)
                    notesRecyclerView.layoutManager = StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL)
                    editor.putBoolean(PREF_COMPACT_VIEW_MODE_ENABLED, true)
                    notesRecyclerView.adapter?.notifyDataSetChanged()
                    toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = true
                    toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = false
                    editor.apply()
                }
                R.id.simple_view_menu_item -> {
                    val editor = prefs.edit()
                    notesRecyclerView.layoutManager =
                        LinearLayoutManager(activity!!, LinearLayoutManager.VERTICAL, false)
                    editor.putBoolean(PREF_COMPACT_VIEW_MODE_ENABLED, false)
                    notesRecyclerView.adapter?.notifyDataSetChanged()
                    toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = false
                    toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = true
                    editor.apply()
                }
                R.id.empty_trash_menu_item -> {
                    val adapter = notesRecyclerView.adapter as NotesAdapter
                    if (adapter.itemCount > 0) {
                        AlertDialog.Builder(context)
                            .setTitle("Empty trash")
                            .setMessage("Are you sure you want to permanently delete all notes in trash?")
                            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                                val list = adapter.getList()
                                if (list.isNotEmpty()) {
                                    for (note in list)
                                        databaseViewModel.deleteNote(note)
                                }
                            }
                            .setNegativeButton("No", null)
                            .setCancelable(false)
                            .show()
                    }
                }
            }
            true
        }
        mainViewModel.setToolbar(toolbar)

        rootView.new_note_button.setOnClickListener {
            mainViewModel.setShouldOpenEditor(true)
            mainViewModel.setSelectedNote(
                Note(
                    -1L,
                    "",
                    "",
                    0,
                    0,
                    "-1",
                    NOTE_DEFAULT,
                    false,
                    null,
                    -1L
                )
            )
        }

        rootView.new_image_note_btn.setOnClickListener {
            rootView.new_image_note_btn.setOnCreateContextMenuListener { menu, _, _ ->
                openNewImageMenu(menu)
            }
            rootView.new_image_note_btn.showContextMenu()
        }

        rootView.new_list_button.setOnClickListener {
            mainViewModel.setShouldOpenEditor(true)
            mainViewModel.setSelectedNote(
                Note(
                    -1L,
                    "",
                    "",
                    0,
                    0,
                    "-1",
                    LIST_DEFAULT,
                    false,
                    null,
                    -1L
                )
            )
        }

        mainViewModel.getViewMode().observe(this, Observer { mode ->
            if (mode != null) {
                when (mode) {
                    1 -> {
                        toolbar.title = "Notes"
                        toolbar.menu.findItem(R.id.empty_trash_menu_item).isVisible = false
                        databaseViewModel.setViewMode(1)
                        rootView.empty_image.setImageResource(R.drawable.all_empty)
                        rootView.empty_text.text = getString(R.string.all_empty_message)
                    }
                    2 -> {
                        toolbar.title = "Archive"
                        toolbar.menu.findItem(R.id.empty_trash_menu_item).isVisible = false
                        databaseViewModel.setViewMode(2)
                        rootView.empty_image.setImageResource(R.drawable.archive_empty)
                        rootView.empty_text.text = getString(R.string.archived_empty_message)
                    }
                    3 -> {
                        toolbar.title = "Trash"
                        toolbar.menu.findItem(R.id.empty_trash_menu_item).isVisible = true
                        databaseViewModel.setViewMode(3)
                        rootView.empty_image.setImageResource(R.drawable.trash_empty)
                        rootView.empty_text.text = getString(R.string.trash_empty_message)
                    }
                }
            }
        })

        databaseViewModel.viewList.observe(this, Observer { viewList ->
            if (viewList != null && viewList.isNotEmpty()) {
                notesRecyclerView.visibility = VISIBLE
                rootView.empty_items.visibility = GONE
                notesRecyclerView.adapter =
                    NotesAdapter(mainViewModel, databaseViewModel, viewList, context!!)
            } else {
                notesRecyclerView.visibility = GONE
                rootView.empty_items.visibility = VISIBLE
            }
        })

        mainViewModel.getShouldOpenEditor().observe(this, Observer { should ->
            if (should != null) {
                if (should) {
                    // If we don't put the navigation statement in try-catch block then app crashes due to unable to
                    // find navController. This is an issue in the Navigation components in Jetpack
                    try {
                        findNavController(this).navigate(R.id.action_mainFragment_to_noteEditFragment)
                    } catch (e: Exception) {
                    }
                }
            }
        })

        if (mainViewModel.intent == null) {
            val intent = activity?.intent
            if (intent != null && intent.flags != Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) {
                mainViewModel.intent = intent

                if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (text != null) {
                        mainViewModel.setSelectedNote(Note(-1L, "", text, 0, 0, "-1", NOTE_DEFAULT, false, null, -1L))
                        mainViewModel.setShouldOpenEditor(true)
                    }
                }else if(intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true){
                    (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {uri->
                        GlobalScope.launch(Dispatchers.IO) {
                            val bitmap = getBitmapFromUri(uri)
                            withContext(Dispatchers.Main) {
                                createNewNoteWithBitmap(bitmap)
                            }
                        }
                    }
                }else if (intent.hasExtra(WIDGET_BUTTON_EXTRA)) {
                    val text = intent.getStringExtra(WIDGET_BUTTON_EXTRA)
                    if (text != null) {
                        Log.d(TAG, "Reached here")
                        when (text) {
                            WIDGET_NEW_NOTE -> {
                                mainViewModel.setSelectedNote(Note(-1L, "", "", 0, 0, "-1", NOTE_DEFAULT, false, null, -1L))
                                mainViewModel.setShouldOpenEditor(true)
                            }
                            WIDGET_NEW_LIST -> {
                                mainViewModel.setSelectedNote(Note(-1L, "", "", 0, 0, "-1", LIST_DEFAULT, false, null, -1L))
                                mainViewModel.setShouldOpenEditor(true)
                            }
                            WIDGET_NEW_IMAGE -> {
                                Log.d(TAG, "Triggered WIDGET_NEW_IMAGE")
                                rootView.new_image_note_btn.post {
                                    Log.d(TAG, "Triggered onPost")
                                    rootView.new_image_note_btn.setOnCreateContextMenuListener { menu, _, _ ->
                                        openNewImageMenu(menu)
                                    }
                                    rootView.new_image_note_btn.showContextMenu()
                                }
                            }
                        }
                    }
                }else if (intent.hasExtra(NOTE_ID_EXTRA)) {
                    val noteId = intent.getLongExtra(NOTE_ID_EXTRA, -1L)
                    if (noteId != -1L) {
                        val bundle = Bundle()
                        bundle.putLong("NOTE_ID", noteId)
                        findNavController(this).navigate(R.id.noteEditFragment, bundle)
                    }
                }
            }
        }
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
                    val photoUri = FileProvider.getUriForFile(context!!, FILE_PROVIDER_AUTHORITY, photoFile)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    startActivityForResult(intent, IMAGE_CAPTURE_REQUEST_CODE)
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
            startActivityForResult(i, IMAGE_PICKER_REQUEST_CODE)
            true
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = activity?.filesDir
        Log.d("MainActivity", "extDir: $storageDir")
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun syncFiles() {
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        when(getLoginStatus(prefs)){
            CLOUD_GOOGLE_DRIVE -> mainViewModel.setSyncNotes(CLOUD_GOOGLE_DRIVE)
            CLOUD_DROPBOX -> mainViewModel.setSyncNotes(CLOUD_DROPBOX)
            else -> findNavController(this).navigate(R.id.action_mainFragment_to_cloudPickerFragment)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == IMAGE_PICKER_REQUEST_CODE) {
                val uri: Uri? = data?.data
                GlobalScope.launch(Dispatchers.IO) {
                    val bitmap = getBitmapFromUri(uri)
                    withContext(Dispatchers.Main){
                        createNewNoteWithBitmap(bitmap)
                    }
                }
            } else if (requestCode == IMAGE_CAPTURE_REQUEST_CODE) {
                val photoFile = File(mainViewModel.getCurrentPhotoPath())
                val photoUri = FileProvider.getUriForFile(context!!, FILE_PROVIDER_AUTHORITY, photoFile)
                GlobalScope.launch(Dispatchers.IO) {
                    val bitmap = getBitmapFromUri(photoUri)
                    withContext(Dispatchers.Main) {
                        if (mainViewModel.getCurrentPhotoPath() != null) {
                            val file = File(mainViewModel.getCurrentPhotoPath())
                            if (file.exists())
                                file.delete()
                        }
                        createNewNoteWithBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun createNewNoteWithBitmap(bitmap: Bitmap?) {
        val databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        if (bitmap != null) {
            val builder = AlertDialog.Builder(context)
            builder.setCancelable(false)
            builder.setView(R.layout.loading_dialog_layout)
            val dialog = builder.create()
            dialog.show()
            GlobalScope.launch(Dispatchers.IO) {
                val imageData = databaseViewModel.insertImage(bitmap)
                val id: Long = imageData.imageId!!
                bitmap.recycle()

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    mainViewModel.setImagesList(arrayListOf(imageData))
                    mainViewModel.setShouldOpenEditor(true)
                    val imageContent = ImageNoteContent("", arrayListOf(id))
                    val content = Gson().toJson(imageContent)
                    mainViewModel.setSelectedNote(
                        Note(
                            -1L, "", content, 0, 0,
                            "-1", Contract.IMAGE_DEFAULT, false, null, -1L
                        )
                    )
                }
            }
        }
    }

    private fun getBitmapFromUri(uri: Uri?): Bitmap? {
        return if (uri != null) {
            val imageStream = activity!!.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(imageStream, null, options)
            val width = options.outWidth
            val height = options.outHeight
            if(width > 1000 || height > 1000) {
                val ratio = (maxOf(width, height)).toFloat() / 1000.0f
                val newWidth = (width * ratio).toInt()
                val newHeight = (height * ratio).toInt()
                Glide.with(context!!)
                    .asBitmap()
                    .load(uri)
                    .submit(newWidth, newHeight)
                    .get()
            }else {
                Glide.with(context!!)
                    .asBitmap()
                    .load(uri)
                    .submit(width, height)
                    .get()
            }
        } else {
            null
        }
    }

    private fun getLoginStatus(prefs: SharedPreferences?): Int {
        if (prefs != null && prefs.contains(PREF_CLOUD_TYPE)) {
            if (prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE) == CLOUD_DROPBOX) {
                if (prefs.contains(PREF_ACCESS_TOKEN) && prefs.getString(PREF_ACCESS_TOKEN, null) != null)
                    return CLOUD_DROPBOX
            } else {
                if (GoogleSignIn.getLastSignedInAccount(context) != null)
                    return CLOUD_GOOGLE_DRIVE
            }
        }
        return -1
    }
}

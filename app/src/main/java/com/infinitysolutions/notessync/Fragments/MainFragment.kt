package com.infinitysolutions.notessync.Fragments

import android.Manifest
import android.app.Activity
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
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
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
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
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_PROVIDER_AUTHORITY
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_CAPTURE_REQUEST_CODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_DEFAULT
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
import com.infinitysolutions.notessync.Model.ImageData
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
import java.io.FileOutputStream
import java.io.IOException
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
        var recyclerAdapter: NotesAdapter? = null
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
                R.id.sync_menu_item -> syncFiles()
                R.id.compact_view_menu_item -> {
                    val editor = prefs.edit()
                    val columnCount = resources.getInteger(R.integer.columns_count)
                    notesRecyclerView.layoutManager = StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL)
                    editor.putBoolean(PREF_COMPACT_VIEW_MODE_ENABLED, true)
                    recyclerAdapter?.notifyDataSetChanged()
                    toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = true
                    toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = false
                    editor.apply()
                }
                R.id.simple_view_menu_item -> {
                    val editor = prefs.edit()
                    notesRecyclerView.layoutManager =
                        LinearLayoutManager(activity!!, LinearLayoutManager.VERTICAL, false)
                    editor.putBoolean(PREF_COMPACT_VIEW_MODE_ENABLED, false)
                    recyclerAdapter?.notifyDataSetChanged()
                    toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = false
                    toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = true
                    editor.apply()
                }
                R.id.delete_forever_menu_item -> {
                    recyclerAdapter?.deleteForeverSelectedNotes()
                    disableMultiSelect(prefs, toolbar)
                }
                R.id.archive_menu_item -> {
                    recyclerAdapter?.archiveSelectedNotes()
                    disableMultiSelect(prefs, toolbar)
                }
                R.id.unarchive_menu_item -> {
                    recyclerAdapter?.unarchiveSelectedNotes()
                    disableMultiSelect(prefs, toolbar)
                }
                R.id.delete_menu_item -> {
                    recyclerAdapter?.deleteSelectedNotes()
                    disableMultiSelect(prefs, toolbar)
                }
                R.id.restore_menu_item -> {
                    recyclerAdapter?.restoreSelectedNotes()
                    disableMultiSelect(prefs, toolbar)
                }
                R.id.select_all_menu_item -> recyclerAdapter?.selectAll()
            }
            true
        }
        mainViewModel.setToolbar(toolbar)

        mainViewModel.getMultiSelectCount().observe(this, Observer{ count ->
            if(count != null && count > 0){
                toolbar.title = "$count selected"
                if(count == 1)
                    enableMultiSelect(toolbar, recyclerAdapter)
            }else{
                disableMultiSelect(prefs, toolbar)
            }
        })
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
                        toolbar.title = getString(R.string.menu_notes)
                        databaseViewModel.setViewMode(1)
                        rootView.empty_image.setImageResource(R.drawable.all_empty)
                        rootView.empty_text.text = getString(R.string.all_empty_message)
                    }
                    2 -> {
                        toolbar.title = getString(R.string.menu_archive)
                        databaseViewModel.setViewMode(2)
                        rootView.empty_image.setImageResource(R.drawable.archive_empty)
                        rootView.empty_text.text = getString(R.string.archived_empty_message)
                    }
                    3 -> {
                        toolbar.title = getString(R.string.menu_trash)
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
                recyclerAdapter = NotesAdapter(mainViewModel, databaseViewModel, viewList, context!!)
                notesRecyclerView.adapter = recyclerAdapter
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
                        insertImageInDatabase(uri, null)
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

    private fun disableMultiSelect(prefs: SharedPreferences, toolbar: Toolbar){
        //TODO: Disable multi-select mode
        toolbar.navigationIcon = null
        toolbar.setNavigationOnClickListener {}
        mainViewModel.setToolbar(toolbar)
        when(mainViewModel.getViewMode().value){
            1 -> toolbar.title = "Notes"
            2 -> toolbar.title = "Archive"
            3 -> toolbar.title = "Trash"
        }
        toolbar.menu.findItem(R.id.delete_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.delete_forever_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.archive_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.unarchive_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.restore_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.select_all_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.sync_menu_item).isVisible = true
        if (prefs.contains(PREF_COMPACT_VIEW_MODE_ENABLED) && !prefs.getBoolean(PREF_COMPACT_VIEW_MODE_ENABLED, true))
            toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = true
        else
            toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = true
    }

    private fun enableMultiSelect(toolbar: Toolbar, recyclerAdapter: NotesAdapter?){
        //TODO: Enable multi-select mode
        toolbar.setNavigationIcon(R.drawable.cancel_reminder)
        toolbar.setNavigationOnClickListener {
            // Clear all trigger
            recyclerAdapter?.clearAll()
        }
        toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.sync_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.select_all_menu_item).isVisible = true
        when(mainViewModel.getViewMode().value){
            1 -> {
                // Notes
                toolbar.menu.findItem(R.id.archive_menu_item).isVisible = true
                toolbar.menu.findItem(R.id.delete_menu_item).isVisible = true
            }
            2 -> {
                // Archive
                toolbar.menu.findItem(R.id.unarchive_menu_item).isVisible = true
                toolbar.menu.findItem(R.id.delete_menu_item).isVisible = true
            }
            3 -> {
                // Trash
                toolbar.menu.findItem(R.id.restore_menu_item).isVisible = true
                toolbar.menu.findItem(R.id.delete_forever_menu_item).isVisible = true
            }
        }
    }

    private fun openNewImageMenu(menu: Menu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(context!!, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ){
            Toast.makeText(context, "Storage permission required", LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(activity!!, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1010)
            return
        }else{
            menu.add(getString(R.string.take_photo)).setOnMenuItemClickListener {
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
                            LENGTH_SHORT
                        ).show()
                } else {
                    Toast.makeText(context, getString(R.string.toast_no_camera_app), LENGTH_SHORT).show()
                }
                true
            }

            menu.add(getString(R.string.pick_image)).setOnMenuItemClickListener {
                val i = Intent(Intent.ACTION_PICK)
                i.type = "image/*"
                startActivityForResult(i, IMAGE_PICKER_REQUEST_CODE)
                true
            }
        }
    }

    private fun syncFiles() {
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        when(getLoginStatus(prefs)){
            CLOUD_GOOGLE_DRIVE -> mainViewModel.setSyncNotes(CLOUD_GOOGLE_DRIVE)
            CLOUD_DROPBOX -> mainViewModel.setSyncNotes(CLOUD_DROPBOX)
            else -> findNavController(this).navigate(R.id.action_mainFragment_to_cloudPickerFragment)
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

    @Throws(IOException::class)
    private fun createImageFile(): File? {
        val path = activity!!.filesDir.toString()
        val time = Calendar.getInstance().timeInMillis
        return File(path, "$time.jpg")
    }

    private fun saveBitmap(imageBitmap: Bitmap, filePath: String){
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

    private fun exifRotateBitmap(filePath: String?, bitmap: Bitmap): Bitmap{
        val exif = ExifInterface(filePath!!)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        val matrix = Matrix()

        when(orientation){
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

    private fun loadBitmap(uri: Uri?, filePath: String?, destinationPath: String) {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        if(uri != null) {
            val imageStream = activity!!.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(imageStream, null, options)
        }else if(filePath != null){
            BitmapFactory.decodeFile(filePath, options)
        }

        var width = options.outWidth
        var height = options.outHeight

        var inSampleSize = 1
        if(width > 1000 || height > 1000) {
            height /= 2
            width /= 2
            while(height / inSampleSize >= 1000 && width / inSampleSize >= 1000)
                inSampleSize *= 2
        }

        options.inSampleSize = inSampleSize
        options.inJustDecodeBounds = false
        var imageBitmap: Bitmap?
        if(uri != null) {
            // Retrieving the bitmap from given uri
            imageBitmap = Glide.with(context!!)
                .asBitmap()
                .load(uri)
                .submit(width, height)
                .get()
        }else{
            // Retrieving the bitmap from given file path
            imageBitmap = BitmapFactory.decodeFile(filePath, options)
            imageBitmap = exifRotateBitmap(filePath, imageBitmap)
            if(filePath != null) {
                val file = File(filePath)
                if (file.exists())
                    file.delete()
            }
        }

        // Saving the bitmap to given path
        if(imageBitmap != null)
            saveBitmap(imageBitmap, destinationPath)
    }

    private fun createNewNote(imageData: ImageData) {
        val id: Long = imageData.imageId!!
        mainViewModel.setImagesList(arrayListOf(imageData))
        mainViewModel.setShouldOpenEditor(true)
        val imageContent = ImageNoteContent("", arrayListOf(id))
        val content = Gson().toJson(imageContent)
        mainViewModel.setSelectedNote(
            Note(-1L, "", content, 0, 0,
                "-1", IMAGE_DEFAULT, false, null, -1L
            )
        )
    }

    private fun insertImageInDatabase(photoUri: Uri?, filePath: String?){
        val databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        val mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)
        GlobalScope.launch(Dispatchers.IO) {
            val imageData = databaseViewModel.insertImage()
            withContext(Dispatchers.Main){
                createNewNote(imageData)
            }
            loadBitmap(photoUri, filePath, imageData.imagePath)
            withContext(Dispatchers.Main){
                // Notify the changes to the view
                mainViewModel.setRefreshImagesList(true)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == IMAGE_PICKER_REQUEST_CODE) {
                val photoUri: Uri? = data?.data
                if(photoUri != null)
                    insertImageInDatabase(photoUri, null)
                else
                    Toast.makeText(context, "Can't access storage", LENGTH_SHORT).show()
            } else if (requestCode == IMAGE_CAPTURE_REQUEST_CODE) {
                if (mainViewModel.getCurrentPhotoPath() != null) {
                    val photoFile = File(mainViewModel.getCurrentPhotoPath()!!)
                    if (photoFile.exists())
                        insertImageInDatabase(null, photoFile.absolutePath)
                    else
                        Toast.makeText(context, "Error in retrieving image", LENGTH_SHORT).show()
                }
            }
        }
    }
}

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
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.OnBackPressedCallback
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.infinitysolutions.notessync.Adapters.NotesAdapter
import com.infinitysolutions.notessync.login.LoginActivity
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.FILE_PROVIDER_AUTHORITY
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_CAPTURE_REQUEST_CODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.IMAGE_PICKER_REQUEST_CODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_ID_EXTRA
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ORDER_ASC
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ORDER_BY_CREATED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ORDER_BY_TITLE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ORDER_BY_UPDATED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ORDER_DESC
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_COMPACT_VIEW_MODE_ENABLED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ORDER
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ORDER_BY
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.WIDGET_BUTTON_EXTRA
import com.infinitysolutions.notessync.Contracts.Contract.Companion.WIDGET_NEW_IMAGE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.WIDGET_NEW_LIST
import com.infinitysolutions.notessync.Contracts.Contract.Companion.WIDGET_NEW_NOTE
import com.infinitysolutions.notessync.Model.ImageData
import com.infinitysolutions.notessync.Model.ImageNoteContent
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.add_image_dialog.view.*
import kotlinx.android.synthetic.main.fragment_main.view.*
import kotlinx.android.synthetic.main.sort_dialog.view.*
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
    private val LOGIN_REQUEST_CODE = 199
    private lateinit var mainViewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_main, container, false)
        initDataBinding(rootView, container)

        rootView.search_button.setOnClickListener {
            findNavController(this).navigate(R.id.action_mainFragment_to_searchFragment)
        }
        return rootView
    }

    private fun initDataBinding(rootView: View, container: ViewGroup?) {
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
                    notesRecyclerView.layoutManager =
                        StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL)
                    editor.putBoolean(PREF_COMPACT_VIEW_MODE_ENABLED, true)
                    recyclerAdapter?.notifyDataSetChanged()
                    toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = true
                    toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = false
                    editor.apply()
                }
                R.id.simple_view_menu_item -> {
                    val editor = prefs.edit()
                    notesRecyclerView.layoutManager = LinearLayoutManager(activity!!, LinearLayoutManager.VERTICAL, false)
                    editor.putBoolean(PREF_COMPACT_VIEW_MODE_ENABLED, false)
                    recyclerAdapter?.notifyDataSetChanged()
                    toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = false
                    toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = true
                    editor.apply()
                }
                R.id.sort_menu_item->{
                    val sortOrder = when(prefs.getString(PREF_ORDER, ORDER_DESC)){
                        ORDER_DESC -> 1
                        else -> 0
                    }
                    val sortOrderBy = when(prefs.getString(PREF_ORDER_BY, ORDER_BY_UPDATED)){
                        ORDER_BY_TITLE -> 0
                        ORDER_BY_UPDATED -> 1
                        ORDER_BY_CREATED -> 2
                        else -> 4
                    }

                    val dialogView = layoutInflater.inflate(R.layout.sort_dialog, container, false)
                    val dialog = BottomSheetDialog(context!!)
                    val orderGroup = dialogView.order_group
                    val sortByGroup = dialogView.sort_by_group
                    (orderGroup.getChildAt(sortOrder) as RadioButton).isChecked = true
                    (sortByGroup.getChildAt(sortOrderBy) as RadioButton).isChecked = true
                    dialog.setOnDismissListener {
                        val newSortOrder: Int
                        val newSortOrderBy: Int
                        val orderResult = when(orderGroup.checkedRadioButtonId){
                            R.id.asc_btn -> {
                                newSortOrder = 0
                                ORDER_ASC
                            }
                            else -> {
                                newSortOrder = 1
                                ORDER_DESC
                            }
                        }
                        val orderByResult = when(sortByGroup.checkedRadioButtonId){
                            R.id.title_btn -> {
                                newSortOrderBy = 0
                                ORDER_BY_TITLE
                            }
                            R.id.edited_btn -> {
                                newSortOrderBy = 1
                                ORDER_BY_UPDATED
                            }
                            else -> {
                                newSortOrderBy = 2
                                ORDER_BY_CREATED
                            }
                        }

                        if(sortOrder != newSortOrder || sortOrderBy != newSortOrderBy) {
                            databaseViewModel.setOrder(orderResult, orderByResult)
                            val editor = prefs.edit()
                            editor.putString(PREF_ORDER_BY, orderByResult)
                            editor.putString(PREF_ORDER, orderResult)
                            editor.commit()
                        }
                    }
                    dialog.setContentView(dialogView)
                    dialog.show()
                }
                R.id.delete_forever_menu_item -> {
                    recyclerAdapter?.deleteForeverSelectedNotes()
                    disableMultiSelect(prefs, toolbar, rootView.bottom_bar)
                }
                R.id.archive_menu_item -> {
                    recyclerAdapter?.archiveSelectedNotes()
                    disableMultiSelect(prefs, toolbar, rootView.bottom_bar)
                }
                R.id.unarchive_menu_item -> {
                    recyclerAdapter?.unarchiveSelectedNotes()
                    disableMultiSelect(prefs, toolbar, rootView.bottom_bar)
                }
                R.id.delete_menu_item -> {
                    recyclerAdapter?.deleteSelectedNotes()
                    disableMultiSelect(prefs, toolbar, rootView.bottom_bar)
                }
                R.id.restore_menu_item -> {
                    recyclerAdapter?.restoreSelectedNotes()
                    disableMultiSelect(prefs, toolbar, rootView.bottom_bar)
                }
                R.id.select_all_menu_item -> recyclerAdapter?.selectAll()
            }
            true
        }
        mainViewModel.setToolbar(toolbar)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                recyclerAdapter?.clearAll()
            }
        }

        mainViewModel.getMultiSelectCount().observe(this, Observer { count ->
            if (count > 0) {
                toolbar.title = "$count selected"
                if (count == 1) {
                    activity?.onBackPressedDispatcher?.addCallback(this, backCallback)
                    enableMultiSelect(toolbar, recyclerAdapter, rootView.bottom_bar)
                    backCallback.isEnabled = true
                }
            } else {
                disableMultiSelect(prefs, toolbar, rootView.bottom_bar)
                backCallback.isEnabled = false
            }
        })
        mainViewModel.setMultiSelectCount(0)

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
                openNewImageMenu(menu, container)
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
                recyclerAdapter =
                    NotesAdapter(mainViewModel, databaseViewModel, viewList, context!!)
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
                        mainViewModel.setSelectedNote(
                            Note(
                                -1L,
                                "",
                                text,
                                0,
                                0,
                                "-1",
                                NOTE_DEFAULT,
                                false,
                                null,
                                -1L
                            )
                        )
                        mainViewModel.setShouldOpenEditor(true)
                    }
                } else if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
                    (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                        insertImageInDatabase(uri, null)
                    }
                } else if (intent.hasExtra(WIDGET_BUTTON_EXTRA)) {
                    val text = intent.getStringExtra(WIDGET_BUTTON_EXTRA)
                    if (text != null) {
                        Log.d(TAG, "Reached here")
                        when (text) {
                            WIDGET_NEW_NOTE -> {
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
                                mainViewModel.setShouldOpenEditor(true)
                            }
                            WIDGET_NEW_LIST -> {
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
                                mainViewModel.setShouldOpenEditor(true)
                            }
                            WIDGET_NEW_IMAGE -> {
                                Log.d(TAG, "Triggered WIDGET_NEW_IMAGE")
                                rootView.new_image_note_btn.post {
                                    Log.d(TAG, "Triggered onPost")
                                    rootView.new_image_note_btn.setOnCreateContextMenuListener { menu, _, _ ->
                                        openNewImageMenu(menu, container)
                                    }
                                    rootView.new_image_note_btn.showContextMenu()
                                }
                            }
                        }
                    }
                } else if (intent.hasExtra(NOTE_ID_EXTRA)) {
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

    private fun disableMultiSelect(prefs: SharedPreferences, toolbar: Toolbar, bottomBar: LinearLayout) {
        toolbar.navigationIcon = null
        bottomBar.visibility = VISIBLE
        toolbar.setNavigationOnClickListener {}
        mainViewModel.setToolbar(toolbar)
        when (mainViewModel.getViewMode().value) {
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
        toolbar.menu.findItem(R.id.sort_menu_item).isVisible = true
        if (prefs.contains(PREF_COMPACT_VIEW_MODE_ENABLED) && !prefs.getBoolean(
                PREF_COMPACT_VIEW_MODE_ENABLED,
                true
            )
        )
            toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = true
        else
            toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = true
    }

    private fun enableMultiSelect(
        toolbar: Toolbar,
        recyclerAdapter: NotesAdapter?,
        bottomBar: LinearLayout
    ) {
        mainViewModel.setToolbar(null)
        bottomBar.visibility = GONE
        toolbar.setNavigationIcon(R.drawable.clear_all_menu_icon_tinted)
        toolbar.setNavigationOnClickListener {
            // Clear all trigger
            recyclerAdapter?.clearAll()
        }
        toolbar.menu.findItem(R.id.compact_view_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.simple_view_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.sync_menu_item).isVisible = false
        toolbar.menu.findItem(R.id.select_all_menu_item).isVisible = true
        toolbar.menu.findItem(R.id.sort_menu_item).isVisible = false
        when (mainViewModel.getViewMode().value) {
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

    private fun openNewImageMenu(menu: Menu, container: ViewGroup?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(
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
            return
        } else {
            val dialogView = layoutInflater.inflate(R.layout.add_image_dialog, container, false)
            val dialog = BottomSheetDialog(context!!)
            dialogView.camera_button.setOnClickListener{
                dialog.dismiss()
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
                            FILE_PROVIDER_AUTHORITY,
                            photoFile
                        )
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                        startActivityForResult(intent, IMAGE_CAPTURE_REQUEST_CODE)
                    } else
                        Toast.makeText(
                            context,
                            "Couldn't access file system",
                            LENGTH_SHORT
                        ).show()
                } else {
                    Toast.makeText(context, getString(R.string.toast_no_camera_app), LENGTH_SHORT)
                        .show()
                }
            }
            dialogView.pick_image_button.setOnClickListener{
                dialog.dismiss()
                val i = Intent(Intent.ACTION_PICK)
                i.type = "image/*"
                startActivityForResult(i, IMAGE_PICKER_REQUEST_CODE)
            }
            dialog.setContentView(dialogView)
            dialog.show()
        }
    }

    private fun syncFiles() {
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE) ?: return
        when (getLoginStatus(prefs)) {
            CLOUD_GOOGLE_DRIVE, CLOUD_DROPBOX -> syncAll(prefs)
            else -> startActivityForResult(Intent(context, LoginActivity::class.java), LOGIN_REQUEST_CODE)
        }
    }

    private fun syncAll(prefs: SharedPreferences){
        prefs.edit().putStringSet(Contract.PREF_SYNC_QUEUE, hashSetOf("1")).commit()
        WorkSchedulerHelper().syncNotes(true, context!!)
    }

    private fun getLoginStatus(prefs: SharedPreferences?): Int {
        if (prefs != null && prefs.contains(PREF_CLOUD_TYPE)) {
            if (prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE) == CLOUD_DROPBOX) {
                if (prefs.contains(PREF_ACCESS_TOKEN) && prefs.getString(
                        PREF_ACCESS_TOKEN,
                        null
                    ) != null
                )
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

    private fun createNewNote(imageData: ImageData) {
        val id: Long = imageData.imageId!!
        mainViewModel.setImagesList(arrayListOf(imageData))
        mainViewModel.setShouldOpenEditor(true)
        val imageContent = ImageNoteContent("", arrayListOf(id))
        val content = Gson().toJson(imageContent)
        mainViewModel.setSelectedNote(
            Note(
                -1L, "", content, 0, 0,
                "-1", IMAGE_DEFAULT, false, null, -1L
            )
        )
    }

    private fun insertImageInDatabase(photoUri: Uri?, filePath: String?) {
        val databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        val mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)
        GlobalScope.launch(Dispatchers.IO) {
            val imageData = databaseViewModel.insertImage()
            withContext(Dispatchers.Main) {
                createNewNote(imageData)
            }
            val isLoadSuccess = loadBitmap(photoUri, filePath, imageData.imagePath)
            // If there is a problem retrieving the image then delete the empty entry
            if (!isLoadSuccess)
                databaseViewModel.deleteImage(imageData.imageId!!, imageData.imagePath)

            // Notify the changes to the view
            withContext(Dispatchers.Main) {
                if(!isLoadSuccess)
                    Toast.makeText(context, "Error in retrieving image", LENGTH_SHORT).show()
                mainViewModel.setRefreshImagesList(true)
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
                if (mainViewModel.getCurrentPhotoPath() != null) {
                    val photoFile = File(mainViewModel.getCurrentPhotoPath()!!)
                    if (photoFile.exists())
                        insertImageInDatabase(null, photoFile.absolutePath)
                    else
                        Toast.makeText(context, "Error in retrieving image", LENGTH_SHORT).show()
                }
            }else if(requestCode == LOGIN_REQUEST_CODE){
                val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
                if(prefs != null)
                    syncAll(prefs)
            }
        }
    }
}

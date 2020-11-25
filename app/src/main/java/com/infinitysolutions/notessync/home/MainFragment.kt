package com.infinitysolutions.notessync.home

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
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
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.NavHostFragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract
import com.infinitysolutions.notessync.contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.contracts.Contract.Companion.FILE_PATH_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.FILE_PROVIDER_AUTHORITY
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_CAPTURE_REQUEST_CODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_PICKER_REQUEST_CODE
import com.infinitysolutions.notessync.contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_CONTENT_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_ID_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_TYPE_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.ORDER_ASC
import com.infinitysolutions.notessync.contracts.Contract.Companion.ORDER_BY_CREATED
import com.infinitysolutions.notessync.contracts.Contract.Companion.ORDER_BY_TITLE
import com.infinitysolutions.notessync.contracts.Contract.Companion.ORDER_BY_UPDATED
import com.infinitysolutions.notessync.contracts.Contract.Companion.ORDER_DESC
import com.infinitysolutions.notessync.contracts.Contract.Companion.PHOTO_URI_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_COMPACT_VIEW_MODE_ENABLED
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ID
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ORDER
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_ORDER_BY
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.WIDGET_BUTTON_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.WIDGET_NEW_IMAGE
import com.infinitysolutions.notessync.contracts.Contract.Companion.WIDGET_NEW_LIST
import com.infinitysolutions.notessync.contracts.Contract.Companion.WIDGET_NEW_NOTE
import com.infinitysolutions.notessync.login.LoginActivity
import com.infinitysolutions.notessync.model.ImageData
import com.infinitysolutions.notessync.model.ImageNoteContent
import com.infinitysolutions.notessync.noteedit.NoteEditActivity
import com.infinitysolutions.notessync.util.WorkSchedulerHelper
import kotlinx.android.synthetic.main.add_image_dialog.view.*
import kotlinx.android.synthetic.main.fragment_main.view.*
import kotlinx.android.synthetic.main.sort_dialog.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.*

class MainFragment : Fragment() {
    private val TAG = "MainFragment"
    private val LOGIN_REQUEST_CODE = 199
    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_main, container, false)
        initDataBinding(rootView, container)

        rootView.search_button.setOnClickListener {
            findNavController(this).navigate(R.id.action_mainFragment_to_searchFragment)
        }
        return rootView
    }

    private fun initDataBinding(rootView: View, container: ViewGroup?) {
        val homeDatabaseViewModel = ViewModelProviders.of(activity!!).get(HomeDatabaseViewModel::class.java)
        homeViewModel = ViewModelProviders.of(activity!!).get(HomeViewModel::class.java)

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
                            homeDatabaseViewModel.setOrder(orderResult, orderByResult)
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
        homeViewModel.setToolbar(toolbar)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                recyclerAdapter?.clearAll()
            }
        }

        homeViewModel.getMultiSelectCount().observe(this, { count ->
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
        homeViewModel.setMultiSelectCount(0)

        rootView.new_note_button.setOnClickListener {
            openNewNote(NOTE_DEFAULT)
        }

        rootView.new_image_note_btn.setOnClickListener {
            rootView.new_image_note_btn.setOnCreateContextMenuListener { _, _, _ ->
                openNewImageMenu(container)
            }
            rootView.new_image_note_btn.showContextMenu()
        }

        rootView.new_list_button.setOnClickListener {
            openNewNote(LIST_DEFAULT)
        }

        homeViewModel.getViewMode().observe(this, { mode ->
            if (mode != null) {
                when (mode) {
                    1 -> {
                        toolbar.title = getString(R.string.menu_notes)
                        homeDatabaseViewModel.setViewMode(1)
                        rootView.empty_image.setImageResource(R.drawable.all_empty)
                        rootView.empty_text.text = getString(R.string.all_empty_message)
                    }
                    2 -> {
                        toolbar.title = getString(R.string.menu_archive)
                        homeDatabaseViewModel.setViewMode(2)
                        rootView.empty_image.setImageResource(R.drawable.archive_empty)
                        rootView.empty_text.text = getString(R.string.archived_empty_message)
                    }
                    3 -> {
                        toolbar.title = getString(R.string.menu_trash)
                        homeDatabaseViewModel.setViewMode(3)
                        rootView.empty_image.setImageResource(R.drawable.trash_empty)
                        rootView.empty_text.text = getString(R.string.trash_empty_message)
                    }
                }
            }
        })

        homeDatabaseViewModel.viewList.observe(this, { viewList ->
            if (viewList != null && viewList.isNotEmpty()) {
                notesRecyclerView.visibility = VISIBLE
                rootView.empty_items.visibility = GONE
                recyclerAdapter =
                    NotesAdapter(homeViewModel, homeDatabaseViewModel, viewList, activity!!)
                notesRecyclerView.adapter = recyclerAdapter
            } else {
                notesRecyclerView.visibility = GONE
                rootView.empty_items.visibility = VISIBLE
            }
        })

        handleIntent(rootView, container)
    }

    private fun handleIntent(rootView: View, container: ViewGroup?){
        val intent = activity?.intent ?: return

        if(intent.hasExtra(WIDGET_BUTTON_EXTRA)) {
            val text = intent.getStringExtra(WIDGET_BUTTON_EXTRA)
            if (text != null) {
                when (text) {
                    WIDGET_NEW_NOTE -> openNewNote(NOTE_DEFAULT)
                    WIDGET_NEW_LIST -> openNewNote(LIST_DEFAULT)
                    WIDGET_NEW_IMAGE -> {
                        rootView.new_image_note_btn.post {
                            rootView.new_image_note_btn.setOnCreateContextMenuListener { _, _, _ ->
                                openNewImageMenu(container)
                            }
                            rootView.new_image_note_btn.showContextMenu()
                        }
                    }
                }
            }
        }
    }

    private fun openNewNote(noteType: Int){
        val newNoteIntent = Intent(activity, NoteEditActivity::class.java)
        newNoteIntent.putExtra(NOTE_ID_EXTRA, -1L)
        newNoteIntent.putExtra(NOTE_TYPE_EXTRA, noteType)
        startActivityForResult(newNoteIntent, 10101)
    }

    private fun disableMultiSelect(prefs: SharedPreferences, toolbar: Toolbar, bottomBar: LinearLayout) {
        toolbar.navigationIcon = null
        bottomBar.visibility = VISIBLE
        toolbar.setNavigationOnClickListener {}
        homeViewModel.setToolbar(toolbar)
        when (homeViewModel.getViewMode().value) {
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
        homeViewModel.setToolbar(null)
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
        when (homeViewModel.getViewMode().value) {
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

    private fun openNewImageMenu(container: ViewGroup?) {
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
                        homeViewModel.setCurrentPhotoPath(photoFile.absolutePath)
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
    private fun createImageFile(): File {
        val path = activity!!.filesDir.toString()
        val time = Calendar.getInstance().timeInMillis
        return File(path, "$time.jpg")
    }

    private fun createNewNote(photoUri: Uri?, filePath: String?) {
        // Open new note with the image details
        val newNoteIntent = Intent(activity, NoteEditActivity::class.java)
        newNoteIntent.putExtra(NOTE_ID_EXTRA, -1L)
        newNoteIntent.putExtra(NOTE_TYPE_EXTRA, IMAGE_DEFAULT)
        newNoteIntent.putExtra(PHOTO_URI_EXTRA, photoUri)
        newNoteIntent.putExtra(FILE_PATH_EXTRA, filePath)
        startActivityForResult(newNoteIntent, 101010)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            if (requestCode == IMAGE_PICKER_REQUEST_CODE) {
                val photoUri: Uri? = data?.data
                if (photoUri != null)
                    createNewNote(photoUri, null)
                else
                    Toast.makeText(context, "Can't access storage", LENGTH_SHORT).show()
            } else if (requestCode == IMAGE_CAPTURE_REQUEST_CODE) {
                if (homeViewModel.getCurrentPhotoPath() != null) {
                    val photoFile = File(homeViewModel.getCurrentPhotoPath()!!)
                    if (photoFile.exists())
                        createNewNote(null, photoFile.absolutePath)
                    else
                        Toast.makeText(context, "Error in retrieving image", LENGTH_SHORT).show()
                }
            }
        }

        if(requestCode == LOGIN_REQUEST_CODE){
            val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
            if(resultCode == RESULT_OK){
                if(prefs != null)
                    syncAll(prefs)
            }else{
                val editor = prefs?.edit()
                editor?.remove(PREF_ACCESS_TOKEN)
                editor?.remove(PREF_CLOUD_TYPE)
                editor?.remove(PREF_ID)
                editor?.apply()
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                val googleSignInClient = GoogleSignIn.getClient(activity!!, gso)
                googleSignInClient.signOut()
            }
        }
    }
}

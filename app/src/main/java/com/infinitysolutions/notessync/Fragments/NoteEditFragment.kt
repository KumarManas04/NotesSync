package com.infinitysolutions.notessync.Fragments


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.fragment_note_edit.view.*
import java.util.*


class NoteEditFragment : Fragment() {
    private lateinit var databaseViewModel: DatabaseViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var noteTitle: EditText
    private lateinit var noteContent: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_note_edit, container, false)
        initDataBinding(rootView)
        return rootView
    }

    private fun initDataBinding(rootView: View) {
        databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        val toolbar = rootView.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.note_editor_menu)
        toolbar.setNavigationOnClickListener {
            activity!!.onBackPressed()
        }

        noteTitle = rootView.note_title
        noteContent = rootView.note_content

        toolbar.setOnMenuItemClickListener{ item ->
            when(item.itemId){
                R.id.delete_menu_item ->{
                    activity!!.onBackPressed()
                    val selectedNote = mainViewModel.getSelectedNote()
                    if (selectedNote != null) {
                        databaseViewModel.insert(Note(
                            selectedNote.nId,
                            selectedNote.noteTitle,
                            selectedNote.noteContent,
                            selectedNote.dateCreated,
                            Calendar.getInstance().timeInMillis,
                            selectedNote.gDriveId,
                            true,
                            selectedNote.synced
                            ))
                    }
                }
            }
            true
        }

        if (mainViewModel.getShouldOpenEditor().value!!) {
            mainViewModel.setShouldOpenEditor(false)
            val selectedNote = mainViewModel.getSelectedNote()
            if (selectedNote?.nId != -1L) {
                rootView.note_title.setText(selectedNote?.noteTitle)
                rootView.note_content.setText(selectedNote?.noteContent)
            }
        }
    }

    private fun saveNote() {
        val timeModified = Calendar.getInstance().timeInMillis
        val selectedNote = mainViewModel.getSelectedNote()
        if (selectedNote != null) {
            if (selectedNote.nId == -1L) {
                databaseViewModel.insert(
                    Note(
                        null,
                        noteTitle.text.toString(),
                        noteContent.text.toString(),
                        timeModified,
                        timeModified,
                        "-1",
                        false,
                        false
                    )
                )
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
                        selectedNote.deleted,
                        selectedNote.synced
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        if ( (mainViewModel.getSelectedNote()?.noteContent != noteContent.text.toString()) || (mainViewModel.getSelectedNote()?.noteTitle != noteTitle.text.toString()) )
            saveNote()
        super.onDestroy()
    }
}

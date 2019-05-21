package com.infinitysolutions.notessync.Fragments


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.infinitysolutions.notessync.MainActivity
import com.infinitysolutions.notessync.Model.Note

import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.fragment_note_edit.*
import kotlinx.android.synthetic.main.fragment_note_edit.view.*
import java.util.*

class NoteEditFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_note_edit, container, false)
        initDataBinding(rootView)
        return rootView
    }

    private fun initDataBinding(rootView: View){
        val databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        val mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        val toolbar = rootView.toolbar
        toolbar.title = "New note"
        mainViewModel.setToolbar(toolbar)

        if(mainViewModel.getShouldGoToEditor().value!!){
            mainViewModel.setShouldGoToEditor(false)
            val selectedNote = mainViewModel.getSelectedNote()
            if (selectedNote?.nId != -1L){
                rootView.note_title.setText(selectedNote?.noteTitle)
                rootView.note_content.setText(selectedNote?.noteContent)
            }
        }

        rootView.save_note.setOnClickListener{
            val timeModified = Calendar.getInstance().timeInMillis
            val selectedNote = mainViewModel.getSelectedNote()
            if (selectedNote != null) {
                if (selectedNote.nId == -1L) {
                    databaseViewModel.insert(
                        Note(
                            null,
                            rootView.note_title.text.toString(),
                            rootView.note_content.text.toString(),
                            timeModified,
                            timeModified,
                            "-1",
                            "-1"
                        )
                    )
                } else {
                    databaseViewModel.insert(
                        Note(
                            selectedNote.nId,
                            rootView.note_title.text.toString(),
                            rootView.note_content.text.toString(),
                            selectedNote.dateCreated,
                            timeModified,
                            "-1",
                            "-1"
                        )
                    )
                }
            }
        }

        rootView.delete_button.setOnClickListener{
            activity!!.onBackPressed()
            databaseViewModel.deleteNoteById(mainViewModel.getSelectedNote()?.nId)
        }
    }
}

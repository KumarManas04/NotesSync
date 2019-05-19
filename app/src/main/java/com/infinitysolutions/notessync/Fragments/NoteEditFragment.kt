package com.infinitysolutions.notessync.Fragments


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import com.infinitysolutions.notessync.Model.Note

import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.fragment_note_edit.view.*

class NoteEditFragment : Fragment() {
    private lateinit var mainViewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_note_edit, container, false)
        val saveNoteButton = rootView.save_note
        val titleTextView = rootView.note_title
        val contentTextView = rootView.note_content
        val toolbar = rootView.toolbar
        toolbar.title = "New note"

        initDataBinding()
        saveNoteButton.setOnClickListener{
            mainViewModel.insert(Note(null, titleTextView.text.toString(), contentTextView.text.toString(), 101, 102, "-1", "-1"))
        }
        return rootView
    }

    fun initDataBinding() {
        mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)
    }
}

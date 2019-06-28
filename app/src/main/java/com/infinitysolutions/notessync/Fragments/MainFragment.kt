package com.infinitysolutions.notessync.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.infinitysolutions.notessync.Adapters.NotesAdapter
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.fragment_main.view.*

class MainFragment : Fragment() {
    private val TAG = "MainFragment"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_main, container, false)
        initDataBinding(rootView)
        return rootView
    }

    private fun initDataBinding(rootView: View) {
        val databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        val mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        val notesRecyclerView = rootView.notes_recycler_view
        notesRecyclerView.layoutManager = LinearLayoutManager(activity!!)
        val toolbar = rootView.toolbar
        toolbar.title = "Notes"
        toolbar.inflateMenu(R.menu.main_fragment_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when(item.itemId){
                R.id.sync_menu_item ->{
                    mainViewModel.setSyncNotes()
                }
            }
            true
        }
        mainViewModel.setToolbar(toolbar)

        rootView.fab.setOnClickListener {
            mainViewModel.setShouldOpenEditor(true)
            mainViewModel.setSelectedNote(Note(-1L, "", "", 0, 0, "-1", NOTE_DEFAULT, false, null))
        }

        mainViewModel.getViewMode().observe(this, Observer { mode->
            if (mode != null){
                if(mode == 1){
                    toolbar.title = "Notes"
                    databaseViewModel.archivesList.removeObservers(this)
                    databaseViewModel.notesList.observe(this, Observer { notesList ->
                        if (notesList != null) {
                            notesRecyclerView.adapter = NotesAdapter(mainViewModel, notesList, context!!)
                        }
                    })
                }else if (mode == 2){
                    toolbar.title = "Archive"
                    databaseViewModel.notesList.removeObservers(this)
                    databaseViewModel.archivesList.observe(this, Observer { archiveList ->
                        if (archiveList != null) {
                            notesRecyclerView.adapter = NotesAdapter(mainViewModel, archiveList, context!!)
                        }
                    })
                }
            }
        })

        databaseViewModel.notesList.observe(this, Observer { notesList ->
            if (notesList != null) {
                notesRecyclerView.adapter = NotesAdapter(mainViewModel, notesList, context!!)
            }
        })

        mainViewModel.getShouldOpenEditor().observe(this, Observer {should ->
            if(should){
                /*
                If we don't put the navigation statement in try-catch block then app crashes due to unable to
                find navController. This is an issue in the Navigation components in Jetpack
                 */
                try {
                    Navigation.findNavController(rootView).navigate(R.id.action_mainFragment_to_noteEditFragment)
                }catch (e: Exception){
                }
            }
        })
    }
}

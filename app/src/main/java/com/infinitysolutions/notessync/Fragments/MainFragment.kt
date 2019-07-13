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
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_DEFAULT
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

        rootView.search_button.setOnClickListener{
            Navigation.findNavController(rootView).navigate(R.id.action_mainFragment_to_searchFragment)
        }
        return rootView
    }

    private fun initDataBinding(rootView: View) {
        val databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        val mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        val notesRecyclerView = rootView.notes_recycler_view
        notesRecyclerView.layoutManager = LinearLayoutManager(activity!!)
        val toolbar = rootView.toolbar
        toolbar.title = "All"
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

        rootView.new_note_button.setOnClickListener {
            mainViewModel.setShouldOpenEditor(true)
            mainViewModel.setSelectedNote(Note(-1L, "", "", 0, 0, "-1", NOTE_DEFAULT, false, null, -1L))
        }

        rootView.new_list_button.setOnClickListener{
            mainViewModel.setShouldOpenEditor(true)
            mainViewModel.setSelectedNote(Note(-1L, "", "", 0, 0, "-1", LIST_DEFAULT, false, null, -1L))
        }

        mainViewModel.getViewMode().observe(this, Observer { mode->
            if (mode != null){
                when (mode) {
                    1 -> {
                        toolbar.title = "All"
                        databaseViewModel.setViewMode(1)
                    }
                    2 -> {
                        toolbar.title = "Notes"
                        databaseViewModel.setViewMode(2)
                    }
                    3 -> {
                        toolbar.title = "To-do lists"
                        databaseViewModel.setViewMode(3)
                    }
                    4 -> {
                        toolbar.title = "Archive"
                        databaseViewModel.setViewMode(4)
                    }
                }
            }
        })

        databaseViewModel.viewList.observe(this, Observer { viewList ->
            if (viewList != null) {
                notesRecyclerView.adapter = NotesAdapter(mainViewModel, viewList, context!!)
            }
        })

        mainViewModel.getShouldOpenEditor().observe(this, Observer {should ->
            if(should){
                // If we don't put the navigation statement in try-catch block then app crashes due to unable to
                // find navController. This is an issue in the Navigation components in Jetpack
                try {
                    Navigation.findNavController(rootView).navigate(R.id.action_mainFragment_to_noteEditFragment)
                }catch (e: Exception){
                }
            }
        })
    }
}

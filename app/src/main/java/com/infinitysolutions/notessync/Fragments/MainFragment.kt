package com.infinitysolutions.notessync.Fragments

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.infinitysolutions.notessync.Adapters.NotesAdapter
import com.infinitysolutions.notessync.Model.Note

import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.fragment_main.view.*

class MainFragment : Fragment() {
    lateinit var notesRecyclerView: RecyclerView
    private lateinit var mainViewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_main, container, false)
        notesRecyclerView = rootView.notes_recycler_view
        notesRecyclerView.layoutManager = LinearLayoutManager(activity!!)
        initDataBinding()
        val createNoteButton = rootView.fab
        createNoteButton.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_mainFragment_to_noteEditFragment)
        }
        return rootView
    }

    fun initDataBinding() {
        mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        mainViewModel.notesDisplayList.observe(this, Observer { notesList ->
            if (notesList != null) {
                notesRecyclerView.adapter = NotesAdapter(notesList, context!!)
            }else
                Toast.makeText(context, "Empty list", Toast.LENGTH_SHORT).show()

        })
    }
}

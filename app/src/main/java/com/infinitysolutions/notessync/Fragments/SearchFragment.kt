package com.infinitysolutions.notessync.Fragments


import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.NavHostFragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.infinitysolutions.notessync.Adapters.NotesAdapter
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.android.synthetic.main.fragment_main.toolbar
import kotlinx.android.synthetic.main.fragment_main.view.*
import kotlinx.android.synthetic.main.fragment_search.*
import kotlinx.android.synthetic.main.fragment_search.view.*
import kotlinx.android.synthetic.main.fragment_search.view.empty_items
import kotlinx.android.synthetic.main.fragment_search.view.search_bar
import kotlinx.android.synthetic.main.fragment_search.view.toolbar


class SearchFragment : Fragment() {
    private lateinit  var searchRecyclerView: RecyclerView
    private val TAG = "SearchFragment"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_search, container, false)
        searchRecyclerView = rootView.result_recycler_view
        val columnCount = resources.getInteger(R.integer.columns_count)
        searchRecyclerView.layoutManager = StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL)
        rootView.back_button.setOnClickListener{
            findNavController(this).navigateUp()
        }
        initDataBinding(rootView)
        return rootView
    }

    private fun initDataBinding(rootView: View){
        val databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)
        val mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)

        var recyclerAdapter: NotesAdapter? = null
        val toolbar = rootView.toolbar
        toolbar.inflateMenu(R.menu.search_fragment_menu)
        toolbar.setNavigationIcon(R.drawable.clear_all_menu_icon_tinted)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.archive_menu_item -> {
                    recyclerAdapter?.archiveSelectedNotes()
                    disableMultiSelect(toolbar, rootView.search_bar)
                }
                R.id.delete_menu_item -> {
                    recyclerAdapter?.deleteSelectedNotes()
                    disableMultiSelect(toolbar, rootView.search_bar)
                }
                R.id.select_all_menu_item -> recyclerAdapter?.selectAll()
            }
            true
        }

        mainViewModel.getMultiSelectCount().observe(this, Observer{count ->
                if (count > 0) {
                    toolbar.title = "$count selected"
                    if (count == 1)
                        enableMultiSelect(toolbar, recyclerAdapter, search_bar)
                } else {
                    disableMultiSelect(toolbar, rootView.search_bar)
                }
        })
        mainViewModel.setMultiSelectCount(0)

        rootView.search_edit_text.addTextChangedListener {
            databaseViewModel.setSearchQuery(it.toString())
        }

        rootView.search_edit_text.postDelayed({
            rootView.search_edit_text.requestFocus()
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(rootView.search_edit_text, 0)
        }, 50)

        databaseViewModel.searchResultList.observe(this, Observer {resultList->
            if(resultList != null && resultList.isNotEmpty()){
                searchRecyclerView.visibility = VISIBLE
                rootView.empty_items.visibility = GONE
                recyclerAdapter = NotesAdapter(mainViewModel, databaseViewModel, resultList, context!!)
                searchRecyclerView.adapter = recyclerAdapter
            }else{
                searchRecyclerView.visibility = GONE
                rootView.empty_items.visibility = VISIBLE
            }
        })

        mainViewModel.getShouldOpenEditor().observe(this, Observer {should ->
            if(should){
                // If we don't put the navigation statement in try-catch block then app crashes due to unable to
                // find navController. This is an issue in the Navigation components in Jetpack
                try {
                    findNavController(this).navigate(R.id.action_searchFragment_to_noteEditFragment)
                }catch (e: Exception){
                }
            }
        })
    }

    private fun disableMultiSelect(toolbar: Toolbar, searchBar: LinearLayout){
        toolbar.visibility = GONE
        searchBar.visibility = VISIBLE
    }

    private fun enableMultiSelect(toolbar: Toolbar, recyclerAdapter: NotesAdapter?, searchBar: LinearLayout){
        toolbar.visibility = VISIBLE
        searchBar.visibility = GONE
        toolbar.setNavigationOnClickListener {
            recyclerAdapter?.clearAll()
        }
    }
}

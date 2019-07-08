package com.infinitysolutions.notessync.Adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_ARCHIVED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.ChecklistGenerator
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.notes_list_item.view.*
import java.text.SimpleDateFormat
import java.util.*

class NotesAdapter(val mainViewModel: MainViewModel, val items: List<Note>, val context: Context): RecyclerView.Adapter<NotesAdapter.ViewHolder>(){
    private val formatter = SimpleDateFormat("E, MMM d", Locale.ENGLISH)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.notes_list_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.titleTextView.text = items[position].noteTitle
        holder.dateModifiedTextView.text = formatter.format(items[position].dateModified)
        holder.parentView.backgroundTintList = ColorStateList.valueOf(Color.parseColor(items[position].noteColor))

        if (items[position].noteType == LIST_DEFAULT || items[position].noteType == LIST_ARCHIVED){
            holder.contentListView.visibility = VISIBLE
            holder.contentTextView.visibility = GONE
            val itemsList = ChecklistGenerator.generateList(items[position].noteContent)
            holder.contentListView.adapter = ListPreviewAdapter(context, itemsList)
            setListViewHeightBasedOnChildren(holder.contentListView)
            holder.contentListView.setOnItemClickListener { _, view, _, _ ->
                mainViewModel.setSelectedNote(items.get(position))
                mainViewModel.setShouldOpenEditor(true)
            }
        }else{
            holder.contentListView.visibility = GONE
            holder.contentTextView.visibility = VISIBLE
            holder.contentTextView.text = items[position].noteContent
        }

        holder.itemContainer.setOnClickListener{
            mainViewModel.setSelectedNote(items.get(position))
            mainViewModel.setShouldOpenEditor(true)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val titleTextView = itemView.title_text
        val contentTextView = itemView.content_preview_text
        val contentListView= itemView.list_view
        val dateModifiedTextView = itemView.date_modified_text
        val parentView = itemView.parent_view
        val itemContainer = itemView
    }

    private fun setListViewHeightBasedOnChildren(listView: ListView) {
        val listAdapter = listView.adapter ?: return

        val desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.width, View.MeasureSpec.UNSPECIFIED)
        var totalHeight = 0
        var view: View? = null
        for (i in 0 until listAdapter.count) {
            view = listAdapter.getView(i, view, listView)
            if (i == 0)
                view!!.layoutParams = ViewGroup.LayoutParams(desiredWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

            view!!.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED)
            totalHeight += view.measuredHeight
        }
        val params = listView.layoutParams
        params.height = totalHeight + listView.dividerHeight * (listAdapter.count - 1)
        listView.layoutParams = params
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
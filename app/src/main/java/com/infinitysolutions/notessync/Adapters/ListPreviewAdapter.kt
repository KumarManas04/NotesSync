package com.infinitysolutions.notessync.Adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.infinitysolutions.notessync.Model.ChecklistItem
import com.infinitysolutions.notessync.R
import kotlinx.android.synthetic.main.checklist_item.view.*

class ListPreviewAdapter(context: Context, private val itemsList: List<ChecklistItem>): ArrayAdapter<ChecklistItem>(context, 0, itemsList){
    private val layoutInflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var listView = convertView
        if (listView == null)
            listView = layoutInflater.inflate(R.layout.checklist_item, parent, false)

        if (listView != null) {
            listView.list_item_content.text = itemsList[position].content
            if (itemsList[position].isChecked)
                listView.item_checked_image.setImageResource(R.drawable.check_yes)
            else
                listView.item_checked_image.setImageResource(R.drawable.check_no)
        }
        return listView!!
    }

    override fun getCount(): Int {
        return itemsList.size
    }
}
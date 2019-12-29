package com.infinitysolutions.notessync.Adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.infinitysolutions.notessync.Model.ImageData
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import kotlinx.android.synthetic.main.image_list_item.view.image_view
import java.io.File

class GalleryAdapter(private val context: Context, private val list: ArrayList<ImageData>, private val databaseViewModel: DatabaseViewModel): RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.view_page_layout, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Glide.with(context).load(File(list[position].imagePath)).into(holder.imageView)
    }

    fun deleteImage(position: Int){
        databaseViewModel.deleteImage(list[position].imageId!!, list[position].imagePath)
        list.removeAt(position)
        notifyItemRemoved(position)
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val imageView: ImageView = itemView.image_view
    }

    override fun getItemCount(): Int {
        return list.size
    }
}
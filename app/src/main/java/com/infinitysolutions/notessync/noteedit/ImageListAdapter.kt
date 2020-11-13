package com.infinitysolutions.notessync.noteedit

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.infinitysolutions.notessync.model.ImageData
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.image_list_item.view.*
import java.io.File

class ImageListAdapter(val context: Context, val list: ArrayList<ImageData>, private val mainViewModel: MainViewModel): RecyclerView.Adapter<ImageListAdapter.ViewHolder>(){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.image_list_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = File(list[position].imagePath)
        if(file.exists()) {
            Glide.with(context).load(file).into(holder.imageView)
            holder.imageView.setOnClickListener {
                mainViewModel.setImagesList(list)
                mainViewModel.setOpenImageView(position)
            }
        }else{
            Glide.with(context).load(R.drawable.image_placeholder).into(holder.imageView)
        }
    }

    fun addImage(imageData: ImageData){
        list.add(imageData)
        notifyItemInserted(list.size-1)
    }

    fun getIdsList(): ArrayList<Long>{
        val result = ArrayList<Long>()
        for(item in list)
            result.add(item.imageId!!)
        return result
    }

    fun setNewList(newList: List<ImageData>){
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val imageView: ImageView = itemView.image_view
    }

    override fun getItemCount(): Int {
        return list.size
    }
}
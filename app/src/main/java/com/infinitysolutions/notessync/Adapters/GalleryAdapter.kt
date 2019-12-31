package com.infinitysolutions.notessync.Adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.infinitysolutions.notessync.Model.ImageData
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.ortiz.touchview.TouchImageView
import java.io.File

class GalleryAdapter(private val context: Context, private val list: ArrayList<ImageData>, private val databaseViewModel: DatabaseViewModel): RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(TouchImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

            setOnTouchListener { view, event ->
                var result = true
                //can scroll horizontally checks if there's still a part of the image
                //that can be scrolled until you reach the edge
                if (event.pointerCount >= 2 || view.canScrollHorizontally(1) && canScrollHorizontally(-1)) {
                    //multi-touch event
                    result = when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            // Disallow RecyclerView to intercept touch events.
                            parent.requestDisallowInterceptTouchEvent(true)
                            // Disable touch on view
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            // Allow RecyclerView to intercept touch events.
                            parent.requestDisallowInterceptTouchEvent(false)
                            true
                        }
                        else -> true
                    }
                }
                result
            }
        })
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Glide.with(context).asBitmap().load(File(list[position].imagePath)).into(object : CustomTarget<Bitmap>(){
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                holder.imageView.setImageBitmap(resource)
            }
            override fun onLoadCleared(placeholder: Drawable?) {}
        })
    }

    fun deleteImage(position: Int){
        databaseViewModel.deleteImage(list[position].imageId!!, list[position].imagePath)
        list.removeAt(position)
        notifyItemRemoved(position)
    }

    fun getItemAtPosition(pos: Int): ImageData{
        return list[pos]
    }

    class ViewHolder(view: TouchImageView): RecyclerView.ViewHolder(view){
        val imageView = view
    }

    override fun getItemCount(): Int {
        return list.size
    }
}
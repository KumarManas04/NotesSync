package com.infinitysolutions.notessync.adapters

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.util.ColorsUtil
import com.infinitysolutions.notessync.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.color_picker_item.view.*

class ColorPickerAdapter(val context: Context, val mainViewModel: MainViewModel?) : RecyclerView.Adapter<ColorPickerAdapter.ViewHolder>(){
    private var selectedColor = 0
    private val colorsUtil = ColorsUtil()

    init{
        if(mainViewModel != null)
            selectedColor = mainViewModel.getSelectedColor().value!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.color_picker_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(position == selectedColor)
            holder.colorImageView.setImageResource(R.drawable.selected_color)
        else
            holder.colorImageView.setImageResource(0)

        val drawable = ContextCompat.getDrawable(context, R.drawable.round_color)
        drawable?.colorFilter = PorterDuffColorFilter(Color.parseColor(colorsUtil.getColor(position)), PorterDuff.Mode.SRC)
        holder.colorImageView.background = drawable

        holder.colorImageView.setOnClickListener{
            selectedColor = position
            mainViewModel?.setSelectedColor(position)
            notifyDataSetChanged()
        }
    }

    fun getSelectedColor(): Int = selectedColor
    fun setSelectedColor(color: Int){
        selectedColor = color
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val colorImageView = itemView.tick_image_view
    }

    override fun getItemCount(): Int {
        return colorsUtil.getSize()
    }
}
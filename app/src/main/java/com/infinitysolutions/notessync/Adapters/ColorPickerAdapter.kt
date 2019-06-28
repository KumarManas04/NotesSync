package com.infinitysolutions.notessync.Adapters

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
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.color_picker_item.view.*

class ColorPickerAdapter(val context: Context, val mainViewModel: MainViewModel) : RecyclerView.Adapter<ColorPickerAdapter.ViewHolder>(){
    private var selectedColor = 0
    private val colorsList = arrayOfNulls<String>(8)

    init{
        colorsList[0] = "#3d81f4"
        colorsList[1] = "#940044"
        colorsList[2] = "#ff5b3a"
        colorsList[3] = "#ac00ae"
        colorsList[4] = "#5e7c8a"
        colorsList[5] = "#009d88"
        colorsList[6] = "#ff0071"
        colorsList[7] = "#7b5448"

        val color = mainViewModel.getSelectedColor().value
        for (i in 0 until colorsList.size){
            if (color == colorsList[i]){
                selectedColor = i
                break
            }
        }
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
        drawable?.colorFilter = PorterDuffColorFilter(Color.parseColor(colorsList[position]), PorterDuff.Mode.SRC)
        holder.colorImageView.background = drawable

        holder.colorImageView.setOnClickListener{
            selectedColor = position
            mainViewModel.setSelectedColor(colorsList[position])
            notifyDataSetChanged()
        }
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        val colorImageView = itemView.tick_image_view
    }

    override fun getItemCount(): Int {
        return colorsList.size
    }
}
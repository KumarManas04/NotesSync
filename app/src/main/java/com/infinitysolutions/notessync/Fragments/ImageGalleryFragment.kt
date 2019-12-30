package com.infinitysolutions.notessync.Fragments


import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.NavHostFragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.infinitysolutions.notessync.Adapters.GalleryAdapter

import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.ViewModel.DatabaseViewModel
import com.infinitysolutions.notessync.ViewModel.MainViewModel
import kotlinx.android.synthetic.main.fragment_image_gallery.view.*

class ImageGalleryFragment : Fragment() {
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_image_gallery, container, false)
        val mainViewModel = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)
        val databaseViewModel = ViewModelProviders.of(activity!!).get(DatabaseViewModel::class.java)

        rootView.toolbar.setNavigationOnClickListener {
            findNavController(this).navigateUp()
        }
        viewPager = rootView.view_pager
        viewPager.adapter = GalleryAdapter(
            context!!,
            mainViewModel.getImagesList(),
            databaseViewModel
        )
        val position: Int? = arguments?.getInt("currentPosition")
        if (position != null) {
            viewPager.setCurrentItem(position, false)
        }

        rootView.delete_btn.setOnClickListener {
            val adapter = (viewPager.adapter as GalleryAdapter)
            if(adapter.itemCount > 1) {
                AlertDialog.Builder(context)
                    .setTitle("Delete")
                    .setMessage("Are you sure you want to delete this image?")
                    .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                        adapter.deleteImage(viewPager.currentItem)
                    }
                    .setNegativeButton("No", null)
                    .setCancelable(true)
                    .show()
            }else{
                Toast.makeText(context, "At least one image is required", Toast.LENGTH_SHORT).show()
            }
        }
        return rootView
    }
}
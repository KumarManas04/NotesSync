package com.infinitysolutions.notessync.noteedit


import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.NavHostFragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.infinitysolutions.notessync.contracts.Contract.Companion.FILE_PROVIDER_AUTHORITY
import com.infinitysolutions.notessync.R
import kotlinx.android.synthetic.main.fragment_image_gallery.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ImageGalleryFragment : Fragment() {
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_image_gallery, container, false)
        val noteEditViewModel = ViewModelProviders.of(activity!!).get(NoteEditViewModel::class.java)
        val noteEditDatabaseViewModel =
            ViewModelProviders.of(activity!!).get(NoteEditDatabaseViewModel::class.java)

        viewPager = rootView.view_pager
        val galleryAdapter = GalleryAdapter(
            context!!,
            noteEditViewModel.getImagesList(),
            noteEditDatabaseViewModel
        )
        viewPager.adapter = galleryAdapter

        val toolbar = rootView.toolbar
        toolbar.setNavigationOnClickListener {
            findNavController(this).navigateUp()
        }

        toolbar.inflateMenu(R.menu.gallery_view_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.delete_image_menu_item -> {
                    AlertDialog.Builder(context)
                        .setTitle(getString(R.string.delete_image))
                        .setMessage(getString(R.string.delete_image_question))
                        .setPositiveButton(getString(R.string.yes)) { _: DialogInterface, _: Int ->
                            if (galleryAdapter.itemCount > 1) {
                                galleryAdapter.deleteImage(viewPager.currentItem)
                            } else {
                                galleryAdapter.deleteImage(viewPager.currentItem)
                                findNavController(this).navigateUp()
                            }
                        }
                        .setNegativeButton(getString(R.string.no), null)
                        .setCancelable(true)
                        .show()
                }
                R.id.share_image_menu_item -> {
                    val imageData = galleryAdapter.getItemAtPosition(viewPager.currentItem)
                    Glide.with(context!!).asBitmap().load(imageData.imagePath)
                        .into(object : CustomTarget<Bitmap>() {
                            override fun onResourceReady(
                                resource: Bitmap,
                                transition: Transition<in Bitmap>?
                            ) {
                                sendBitmap(resource)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {}
                        })
                }
            }
            true
        }

        val position: Int? = arguments?.getInt("currentPosition")
        if (position != null) {
            viewPager.setCurrentItem(position, false)
        }

        return rootView
    }

    private fun sendBitmap(bitmap: Bitmap) {
        GlobalScope.launch(Dispatchers.IO) {
            val folder = File(activity!!.cacheDir, "images")
            try {
                folder.mkdirs()
                val file = File(folder, "1.png")
                if (file.exists())
                    file.delete()
                val outputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
                val uri = FileProvider.getUriForFile(context!!, FILE_PROVIDER_AUTHORITY, file)
                withContext(Dispatchers.Main) {
                    if (uri != null) {
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.putExtra(Intent.EXTRA_STREAM, uri)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        intent.type = "image/png"
                        startActivity(Intent.createChooser(intent, "Share"))
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
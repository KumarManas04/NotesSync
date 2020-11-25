package com.infinitysolutions.notessync.noteedit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.contracts.Contract.Companion.APP_LOCK_STATE
import com.infinitysolutions.notessync.contracts.Contract.Companion.FILE_PATH_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.IMAGE_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_CONTENT_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_DEFAULT
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_ID_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.NOTE_TYPE_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.PHOTO_URI_EXTRA
import com.infinitysolutions.notessync.contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.contracts.Contract.Companion.STATE_NOTE_EDIT
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_AMOLED
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DARK
import com.infinitysolutions.notessync.contracts.Contract.Companion.THEME_DEFAULT
import com.infinitysolutions.notessync.model.ImageData
import com.infinitysolutions.notessync.model.ImageNoteContent
import com.infinitysolutions.notessync.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class NoteEditActivity : AppCompatActivity() {
    private val TAG = "NoteEditActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs.contains(PREF_THEME)) {
            when (prefs.getInt(PREF_THEME, THEME_DEFAULT)) {
                THEME_DEFAULT -> setTheme(R.style.AppTheme)
                THEME_DARK -> setTheme(R.style.AppThemeDark)
                THEME_AMOLED -> setTheme(R.style.AppThemeAmoled)
            }
        }

        setContentView(R.layout.activity_note_edit)
        initializeNote()
    }

    private fun initializeNote() {
        val noteEditViewModel = ViewModelProviders.of(this)[NoteEditViewModel::class.java]
        val noteEditDatabaseViewModel = ViewModelProviders.of(this)[NoteEditDatabaseViewModel::class.java]

        val noteId = intent.getLongExtra(NOTE_ID_EXTRA, -1)
        val noteType = intent.getIntExtra(NOTE_TYPE_EXTRA, NOTE_DEFAULT)
        var noteContent = intent.getStringExtra(NOTE_CONTENT_EXTRA)
        val photoUri = intent.getParcelableExtra<Uri?>(PHOTO_URI_EXTRA)
        val filePath = intent.getStringExtra(FILE_PATH_EXTRA)

        GlobalScope.launch(Dispatchers.IO) {
            if (noteId == -1L && noteType == IMAGE_DEFAULT) {
                val imageData = noteEditDatabaseViewModel.insertImage()
                noteContent = Gson().toJson(
                    ImageNoteContent(
                        noteContent,
                        arrayListOf(imageData.imageId!!)
                    )
                )

                withContext(Dispatchers.Main){
                    insertImage(noteEditViewModel, noteEditDatabaseViewModel, imageData, photoUri, filePath)
                }
            }

            val note = if (noteId == -1L)
                Note(noteId, "", noteContent, 0, 0, "-1", noteType, false, null, -1L)
            else
                noteEditDatabaseViewModel.getNoteById(noteId)
            noteEditViewModel.setCurrentNote(note)

            withContext(Dispatchers.Main) {
                val bundle = Bundle()
                bundle.putInt(APP_LOCK_STATE, STATE_NOTE_EDIT)
                findNavController(R.id.nav_host_fragment).setGraph(
                    R.navigation.note_edit_nav_graph,
                    bundle
                )
            }
        }
    }

    private fun insertImage(noteEditViewModel: NoteEditViewModel, noteEditDatabaseViewModel: NoteEditDatabaseViewModel, imageData: ImageData, photoUri: Uri?, filePath: String?) {
        GlobalScope.launch (Dispatchers.IO) {
            val isLoadSuccess = loadBitmap(photoUri, filePath, imageData.imagePath)
            // If there is a problem retrieving the image then delete the empty entry
            if (!isLoadSuccess)
                noteEditDatabaseViewModel.deleteImage(imageData.imageId!!, imageData.imagePath)

            // Notify the changes to the view
            withContext(Dispatchers.Main) {
                if (!isLoadSuccess)
                    Toast.makeText(this@NoteEditActivity, "Error in retrieving image", LENGTH_SHORT)
                        .show()
                noteEditViewModel.setRefreshImagesList(true)
            }
        }
    }

    private fun loadBitmap(uri: Uri?, filePath: String?, destinationPath: String): Boolean {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        try {
            if (uri != null) {
                val imageStream = contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(imageStream, null, options)
            } else if (filePath != null) {
                BitmapFactory.decodeFile(filePath, options)
            }
        } catch (e: Exception) {
            return false
        }

        var width = options.outWidth
        var height = options.outHeight

        var inSampleSize = 1
        if (width > 1000 || height > 1000) {
            height /= 2
            width /= 2
            while (height / inSampleSize >= 1000 && width / inSampleSize >= 1000)
                inSampleSize *= 2
        }

        options.inSampleSize = inSampleSize
        options.inJustDecodeBounds = false
        var imageBitmap: Bitmap?
        if (uri != null) {
            // Retrieving the bitmap from given uri
            imageBitmap = Glide.with(this)
                .asBitmap()
                .load(uri)
                .submit(width, height)
                .get()
        } else {
            // Retrieving the bitmap from given file path
            imageBitmap = BitmapFactory.decodeFile(filePath, options)
            imageBitmap = exifRotateBitmap(filePath, imageBitmap)
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists())
                    file.delete()
            }
        }

        // Saving the bitmap to given path
        if (imageBitmap != null)
            saveBitmap(imageBitmap, destinationPath)
        return true
    }

    private fun saveBitmap(imageBitmap: Bitmap, filePath: String) {
        val file = File(filePath)
        try {
            val fos = FileOutputStream(file)
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        imageBitmap.recycle()
    }

    private fun exifRotateBitmap(filePath: String?, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(filePath!!)
        val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1F, 1F)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180F)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180F)
                matrix.postScale(-1F, 1F)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90F)
                matrix.postScale(-1F, 1F)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90F)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90F)
                matrix.postScale(-1F, 1F)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90F)
            else -> return bitmap
        }

        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return result
    }
}

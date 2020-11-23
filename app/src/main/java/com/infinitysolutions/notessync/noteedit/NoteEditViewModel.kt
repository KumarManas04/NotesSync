package com.infinitysolutions.notessync.noteedit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infinitysolutions.notessync.model.ImageData
import com.infinitysolutions.notessync.model.Note
import com.infinitysolutions.notessync.util.Event

class NoteEditViewModel: ViewModel(){
    private var currentNote: Note? = null
    var noteType: Int? = null
    var reminderTime = -1L
    private var currentPhotoPath: String? = null
    private val selectedColor = MutableLiveData<Int>()
    private val openImageView = MutableLiveData<Event<Int>>()
    private val refreshImagesList = MutableLiveData<Event<Boolean>>()
    private val imagesList = ArrayList<ImageData>()

    fun setOpenImageView(imageId: Int){
        openImageView.value = Event(imageId)
    }

    fun setSelectedColor(color: Int?){
        if (color == null)
            selectedColor.value = 0
        else
            selectedColor.value = color
    }

    fun setCurrentNote(note: Note?){
        currentNote = note
    }

    fun setRefreshImagesList(value: Boolean){
        refreshImagesList.value = Event(value)
    }

    fun setImagesList(list: ArrayList<ImageData>?){
        imagesList.clear()
        if(list != null)
            for(item in list)
                imagesList.add(item)
    }

    fun addImageToImageList(image: ImageData){
        imagesList.add(image)
    }

    fun getSelectedColor() : LiveData<Int>{
        if (selectedColor.value == null)
            selectedColor.value = 0
        return selectedColor
    }

    fun setCurrentPhotoPath(photoPath: String){
        currentPhotoPath = photoPath
    }

    fun getCurrentNote(): Note? = currentNote
    fun getCurrentPhotoPath(): String? = currentPhotoPath
    fun getRefreshImagesList(): LiveData<Event<Boolean>> = refreshImagesList
    fun getOpenImageView(): LiveData<Event<Int>> = openImageView
    fun getImagesList(): ArrayList<ImageData> = imagesList
}
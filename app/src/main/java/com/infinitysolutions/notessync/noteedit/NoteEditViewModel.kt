package com.infinitysolutions.notessync.noteedit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infinitysolutions.notessync.model.ImageData
import com.infinitysolutions.notessync.model.Note
import com.infinitysolutions.notessync.util.Event

class NoteEditViewModel: ViewModel(){
    private var currentNote: Note? = null
    private val refreshImagesList = MutableLiveData<Event<Boolean>>()
    private val imagesList = ArrayList<ImageData>()

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

    fun getCurrentNote(): Note? = currentNote
    fun getRefreshImagesList(): LiveData<Event<Boolean>> = refreshImagesList
    fun getImagesList(): ArrayList<ImageData> = imagesList
}
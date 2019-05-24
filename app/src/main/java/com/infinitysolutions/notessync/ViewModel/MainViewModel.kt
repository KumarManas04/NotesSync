package com.infinitysolutions.notessync.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infinitysolutions.notessync.Model.Note

class MainViewModel: ViewModel(){
    private var selectedNote: Note? = null
    private val shouldOpenEditor = MutableLiveData<Boolean>()

    fun setSelectedNote(note: Note?){
        selectedNote = note
    }

    fun getSelectedNote(): Note?{
        return selectedNote
    }

    fun setShouldOpenEditor(shouldOpen: Boolean){
        shouldOpenEditor.value = shouldOpen
    }

    fun getShouldOpenEditor(): LiveData<Boolean>{
        return shouldOpenEditor
    }
}
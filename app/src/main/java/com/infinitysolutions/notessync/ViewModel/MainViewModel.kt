package com.infinitysolutions.notessync.ViewModel

import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Util.Event

class MainViewModel: ViewModel(){
    private var selectedNote: Note? = null
    private val selectedColor = MutableLiveData<Int>()
    private val shouldOpenEditor = MutableLiveData<Boolean>()
    private val syncNotes = MutableLiveData<Event<Int>>()
    private val mToolbar = MutableLiveData<Toolbar>()
    private val viewMode = MutableLiveData<Int>()
    var reminderTime = -1L

    init{
        viewMode.value = 1
    }

    fun setViewMode(mode: Int){
        viewMode.value = mode
    }

    fun getViewMode(): LiveData<Int>{
        return viewMode
    }

    fun setToolbar(toolbar: Toolbar){
        mToolbar.value = toolbar
    }

    fun getToolbar(): LiveData<Toolbar>{
        return mToolbar
    }

    fun setSelectedNote(note: Note?){
        selectedNote = note
    }

    fun getSelectedNote(): Note?{
        return selectedNote
    }

    fun setSelectedColor(color: Int?){
        if (color == null)
            selectedColor.value = 0
        else
            selectedColor.value = color
    }

    fun getSelectedColor() : LiveData<Int>{
        if (selectedColor.value == null)
            selectedColor.value = 0
        return selectedColor
    }

    fun setShouldOpenEditor(shouldOpen: Boolean){
        shouldOpenEditor.value = shouldOpen
    }

    fun getShouldOpenEditor(): LiveData<Boolean>{
        return shouldOpenEditor
    }

    fun setSyncNotes(noteType: Int){
        syncNotes.value = Event(noteType)
    }

    fun getSyncNotes(): LiveData<Event<Int>>{
        return syncNotes
    }
}
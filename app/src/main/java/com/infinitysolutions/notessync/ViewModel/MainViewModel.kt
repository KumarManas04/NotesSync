package com.infinitysolutions.notessync.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Util.Event

class MainViewModel: ViewModel(){
    private var selectedNote: Note? = null
    private val shouldOpenEditor = MutableLiveData<Boolean>()
    private val syncNotes = MutableLiveData<Event<Boolean>>()

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

    fun setSyncNotes(){
        syncNotes.value = Event(true)
    }

    fun getSyncNotes(): LiveData<Event<Boolean>>{
        return syncNotes
    }
}
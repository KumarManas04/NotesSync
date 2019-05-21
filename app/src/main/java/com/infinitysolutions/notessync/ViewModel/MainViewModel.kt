package com.infinitysolutions.notessync.ViewModel

import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infinitysolutions.notessync.Model.Note

class MainViewModel: ViewModel(){
    private val mToolbar = MutableLiveData<Toolbar>()
    private var selectedNote: Note? = null
    private val shouldGoToEditor = MutableLiveData<Boolean>()

    fun setSelectedNote(note: Note?){
        selectedNote = note
    }

    fun getSelectedNote(): Note?{
        return selectedNote
    }

    fun setShouldGoToEditor(shouldGoTo: Boolean){
        shouldGoToEditor.value = shouldGoTo
    }

    fun getShouldGoToEditor(): LiveData<Boolean>{
        return shouldGoToEditor
    }

    fun setToolbar(toolbar: Toolbar){
        mToolbar.value = toolbar
    }

    fun getToolbar():LiveData<Toolbar>{
        return mToolbar
    }
}
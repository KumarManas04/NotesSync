package com.infinitysolutions.notessync.home

import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel: ViewModel() {
    private val multiSelectCount = MutableLiveData<Int>()
    private val mToolbar = MutableLiveData<Toolbar?>()
    private val viewMode = MutableLiveData<Int>()
    private var currentPhotoPath: String? = null

    init{
        viewMode.value = 1
    }

    fun setCurrentPhotoPath(photoPath: String){
        currentPhotoPath = photoPath
    }

    fun setViewMode(mode: Int){
        viewMode.value = mode
    }

    fun setMultiSelectCount(value: Int){
        multiSelectCount.value = value
    }

    fun setToolbar(toolbar: Toolbar?){
        mToolbar.value = toolbar
    }

    fun getCurrentPhotoPath(): String? = currentPhotoPath
    fun getViewMode(): LiveData<Int> = viewMode
    fun getToolbar(): LiveData<Toolbar?> = mToolbar
    fun getMultiSelectCount(): LiveData<Int> = multiSelectCount
}
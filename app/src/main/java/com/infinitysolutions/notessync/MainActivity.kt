package com.infinitysolutions.notessync

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.infinitysolutions.notessync.ViewModel.MainViewModel

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initDataBinding()
    }

    fun initDataBinding(){
        val mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        mainViewModel.getToolbar().observe(this, Observer { toolbar->
            Log.d("TAG", "Set toolbar is triggered")
            if (toolbar != null){
                setSupportActionBar(toolbar)
            }
        })
    }
}

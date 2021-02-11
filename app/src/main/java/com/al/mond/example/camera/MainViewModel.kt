package com.al.mond.example.camera

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    val uri : MutableLiveData<Uri> = MutableLiveData()
}
package com.al.mond.example.camera

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import com.al.mond.example.camera.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.button.setOnClickListener {
            doCapture()
        }

        viewModel.uri.observe(this, {
            Glide.with(this).load(it).into(binding.capturedImage)
        })
    }

    private fun getMustRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P)
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else {
            arrayOf()
        }
    }

    private fun getNotGrantedPermissions(permissionList: Array<String>): ArrayList<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val requestPermission = ArrayList<String>()
            for (permission in permissionList) {
                if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                    continue
                } else {
                    requestPermission.add(permission)
                }
            }
            return requestPermission
        }
        return arrayListOf()
    }

    private fun shouldShowRequestPermissionRationale(permissionList: Array<String>): Boolean {
        for (permission in permissionList) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true
            } else {
                continue
            }
        }
        return false
    }

    private var extraOutputFile: File? = null
    private fun requestCapture() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(this.packageManager) != null) {
            val dateTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            extraOutputFile = File(cacheDir, "${dateTime}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", extraOutputFile!!)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            startActivityForResult(takePictureIntent, RequestCode.Capture.ordinal)
        }
    }


    private fun doCapture() {
        val permissionList: Array<String> = getMustRequiredPermissions()
        when {
            shouldShowRequestPermissionRationale(permissionList) -> {
                Toast.makeText(this, "must permission(s)", Toast.LENGTH_SHORT).show()
            }
            getNotGrantedPermissions(permissionList).isNotEmpty() -> {
                ActivityCompat.requestPermissions(this, permissionList, RequestCode.CapturePermissions.ordinal)
            }
            else -> {
                requestCapture()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                RequestCode.Capture.ordinal -> {
                    extraOutputFile?.let { originalCapturedImageFile ->
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.TITLE, originalCapturedImageFile.name)
                            put(MediaStore.Images.Media.SIZE, originalCapturedImageFile.length())
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 1)
                            val contentUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            if (contentUri != null) {
                                contentResolver.openFileDescriptor(contentUri, "w")?.use { pfd ->
                                    val outputStream = FileOutputStream(pfd.fileDescriptor)
                                    copyFromTo(FileInputStream(extraOutputFile), outputStream)
                                    contentValues.clear()
                                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                                    contentResolver.update(contentUri, contentValues, null, null)
                                }
                                viewModel.uri.value = contentUri
                            }
                        } else {
                            val externalFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), originalCapturedImageFile.name)
                            copyFromTo(FileInputStream(extraOutputFile), FileOutputStream(externalFile))
                            contentValues.put(MediaStore.Images.Media.DATA, externalFile.absolutePath)
                            val contentUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            if (contentUri != null) {
                                viewModel.uri.value = contentUri
                            }
                        }
                        if (originalCapturedImageFile.delete())
                            Toast.makeText(this, "Removed the garbage file. ", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun copyFromTo(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val data = inputStream.read(buffer)
            if (data == -1) {
                break
            }
            outputStream.write(buffer)
        }
        inputStream.close()
        outputStream.close()
    }
}

enum class RequestCode {
    Capture, CapturePermissions
}
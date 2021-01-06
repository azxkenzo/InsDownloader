package org.sei.insdownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.sei.insdownloader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    val viewBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val impl = MainActivityImpl(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        setSupportActionBar(viewBinding.toolbar)

        impl.onCreate()

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 1
            )
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    }

    override fun onStart() {
        super.onStart()
        impl.onStart()
    }

    override fun onResume() {
        super.onResume()
        impl.onResume()
    }

    override fun onPause() {
        super.onPause()
        impl.onPause()
    }

    override fun onStop() {
        super.onStop()
        impl.onStop()
    }

    override fun onDestroy() {
        impl.onDestroy()
        super.onDestroy()
    }


}
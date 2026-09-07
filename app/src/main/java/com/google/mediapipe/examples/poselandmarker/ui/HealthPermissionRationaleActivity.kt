package com.google.mediapipe.examples.poselandmarker.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityHealthPermissionsRationaleBinding

class HealthPermissionRationaleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityHealthPermissionsRationaleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnCloseHealthRationale.setOnClickListener { finish() }
    }
}

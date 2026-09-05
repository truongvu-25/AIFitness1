/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.poselandmarker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityMainBinding
import com.google.mediapipe.examples.poselandmarker.notification.NotificationHelper
import com.google.mediapipe.examples.poselandmarker.service.StepCounterService
import com.google.mediapipe.examples.poselandmarker.utils.LocaleHelper
import com.google.mediapipe.examples.poselandmarker.viewmodel.MainViewModel


class MainActivity : AppCompatActivity() {

    private lateinit var activityMainBinding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()
    private var requestedRuntimePermissions = false

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (StepCounterService.hasActivityRecognitionPermission(this)) {
            StepCounterService.startService(this)
        }
    }


    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            LocaleHelper.onAttach(newBase)
        )
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityMainBinding =
            ActivityMainBinding.inflate(layoutInflater)

        setContentView(activityMainBinding.root)


        // =========================================================
        // NOTIFICATION
        // =========================================================

        NotificationHelper.scheduleDailyReminder(this)


        // =========================================================
        // NAVIGATION CONTROLLER
        // =========================================================

        val navHostFragment =
            supportFragmentManager.findFragmentById(
                R.id.fragment_container
            ) as NavHostFragment

        val navController =
            navHostFragment.navController


        // =========================================================
        // BOTTOM NAVIGATION
        // =========================================================

        activityMainBinding.navigation
            .setupWithNavController(navController)


        // =========================================================
        // APP CHROME VISIBILITY
        //
        // Header + BottomNavigation chỉ xuất hiện
        // ở các màn chính:
        //
        // Home
        // Workout Calendar
        // Library
        // Profile
        //
        // Các flow fullscreen/onboarding phải ẩn.
        // =========================================================

        navController.addOnDestinationChangedListener {
                _,
                destination,
                _ ->


            val showMainChrome = destination.id in setOf(
                R.id.home_fragment,
                R.id.workout_calendar_fragment,
                R.id.library_fragment,
                R.id.profile_fragment
            )

            if (!showMainChrome) {

                activityMainBinding
                    .headerBrandBar
                    .visibility = View.GONE

                activityMainBinding
                    .bottomNavCard
                    .visibility = View.GONE

            } else {

                activityMainBinding
                    .headerBrandBar
                    .visibility = View.VISIBLE

                activityMainBinding
                    .bottomNavCard
                    .visibility = View.VISIBLE

                requestRuntimePermissionsOnce()
            }
        }
    }

    private fun requestRuntimePermissionsOnce() {
        if (requestedRuntimePermissions) return
        requestedRuntimePermissions = true

        val missingPermissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missingPermissions.isEmpty()) {
            StepCounterService.startService(this)
        } else {
            runtimePermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}

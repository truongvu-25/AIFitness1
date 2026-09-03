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

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityMainBinding
import com.google.mediapipe.examples.poselandmarker.notification.NotificationHelper
import com.google.mediapipe.examples.poselandmarker.utils.LocaleHelper
import com.google.mediapipe.examples.poselandmarker.viewmodel.MainViewModel


class MainActivity : AppCompatActivity() {

    private lateinit var activityMainBinding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()


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


        // Ignore re-selecting the current BottomNavigation item.
        activityMainBinding.navigation
            .setOnNavigationItemReselectedListener {
                // Intentionally empty.
            }


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


            val hideMainChrome =
                when (destination.id) {

                    // ---------------------------------------------
                    // ONBOARDING / AUTH
                    // ---------------------------------------------

                    R.id.welcome_fragment,
                    R.id.login_fragment,
                    R.id.register_fragment,
                    R.id.user_info_fragment,


                        // ---------------------------------------------
                        // CAMERA / MEDIA
                        // ---------------------------------------------

                    R.id.camera_fragment,
                    R.id.permissions_fragment,
                    R.id.gallery_fragment,


                        // ---------------------------------------------
                        // FULLSCREEN UTILITY
                        // ---------------------------------------------

                    R.id.update_bmi_fragment,
                    R.id.create_custom_plan_fragment -> true


                    // ---------------------------------------------
                    // MAIN APP DESTINATIONS
                    // ---------------------------------------------

                    else -> false
                }


            if (hideMainChrome) {

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
            }
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {

        val navHostFragment =
            supportFragmentManager.findFragmentById(
                R.id.fragment_container
            ) as NavHostFragment

        val navController =
            navHostFragment.navController


        if (!navController.navigateUp()) {

            super.onBackPressed()
        }
    }
}
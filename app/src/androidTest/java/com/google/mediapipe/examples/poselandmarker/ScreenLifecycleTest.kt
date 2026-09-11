package com.google.mediapipe.examples.poselandmarker

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises real view creation/teardown without submitting any profile/workout writes. */
@RunWith(AndroidJUnit4::class)
class ScreenLifecycleTest {
    @Test fun mainScreensAndCameraSurviveRepeatedNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.CAMERA}").close()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val destinations = listOf(R.id.home_fragment, R.id.library_fragment,
                R.id.create_custom_plan_fragment, R.id.profile_fragment,
                R.id.progress_fragment, R.id.gallery_fragment, R.id.update_bmi_fragment)
            for (id in destinations) {
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
                    host.navController.navigate(id)
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(250)
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
                    assertEquals(id, host.navController.currentDestination!!.id)
                }
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                val directory = File(context.getExternalFilesDir(null), "audit-screens").apply { mkdirs() }
                File(directory, "${context.resources.getResourceEntryName(id)}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            repeat(8) {
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
                    host.navController.navigate(R.id.camera_fragment, Bundle().apply {
                        putString("exerciseId", "squat")
                        putString("exerciseName", "Squat")
                        putInt("targetCount", 10)
                        putInt("dayIndex", 1)
                    })
                }
                SystemClock.sleep(1500)
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                SystemClock.sleep(1000)
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
                    assertEquals(10, host.navController.currentBackStackEntry!!.arguments!!.getInt("targetCount"))
                    host.navController.popBackStack()
                }
            }
        }
    }
}

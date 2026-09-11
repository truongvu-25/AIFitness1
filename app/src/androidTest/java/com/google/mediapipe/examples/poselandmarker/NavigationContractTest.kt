package com.google.mediapipe.examples.poselandmarker

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationContractTest {
    @Navigator.Name("fragment")
    class StubFragmentNavigator : Navigator<NavDestination>() {
        override fun createDestination() = NavDestination(this)
        override fun navigate(destination: NavDestination, args: Bundle?, navOptions: NavOptions?, navigatorExtras: Extras?) = destination
        override fun popBackStack() = true
    }

    private fun withController(block: (NavController) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val controller = NavController(ApplicationProvider.getApplicationContext())
            controller.navigatorProvider.addNavigator(StubFragmentNavigator())
            controller.setGraph(R.navigation.nav_graph)
            block(controller)
        }
    }

    @Test fun bmiUpdateReturnsToCalendar() = withController { nav ->
        nav.navigate(R.id.action_welcome_to_update_bmi)
        nav.navigate(R.id.action_update_bmi_to_workout_calendar)
        assertEquals(R.id.workout_calendar_fragment, nav.currentDestination!!.id)
    }

    @Test fun cameraPermissionRoundTripPreservesWorkoutArguments() = withController { nav ->
        val arguments = Bundle().apply {
            putString("exerciseId", "plank")
            putString("exerciseName", "Plank")
            putInt("targetCount", 30)
            putInt("dayIndex", 7)
        }
        nav.navigate(R.id.camera_fragment, arguments)
        nav.navigate(R.id.action_camera_to_permissions, nav.currentBackStackEntry!!.arguments)
        nav.navigate(R.id.action_permissions_to_camera, nav.currentBackStackEntry!!.arguments)
        assertEquals("plank", nav.currentBackStackEntry!!.arguments!!.getString("exerciseId"))
        assertEquals(30, nav.currentBackStackEntry!!.arguments!!.getInt("targetCount"))
        assertEquals(7, nav.currentBackStackEntry!!.arguments!!.getInt("dayIndex"))
    }

    @Test fun logoutClearsAuthenticatedBackStack() = withController { nav ->
        nav.navigate(R.id.home_fragment)
        nav.navigate(R.id.profile_fragment)
        nav.navigate(R.id.action_profile_to_login)
        assertEquals(R.id.login_fragment, nav.currentDestination!!.id)
        nav.popBackStack()
        assertEquals(null, nav.currentDestination)
    }
}

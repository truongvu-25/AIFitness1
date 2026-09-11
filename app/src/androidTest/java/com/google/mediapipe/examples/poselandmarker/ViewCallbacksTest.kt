package com.google.mediapipe.examples.poselandmarker

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.mediapipe.examples.poselandmarker.utils.addOnViewSuccessListener
import com.google.mediapipe.examples.poselandmarker.utils.addOnViewFailureListener
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewCallbacksTest {
    private class ViewOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override fun getLifecycle(): LifecycleRegistry = registry
    }

    @Test fun backgroundResultIsDeliveredOnceAfterResume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var owner: ViewOwner
        val source = TaskCompletionSource<String>()
        var deliveries = 0
        instrumentation.runOnMainSync {
            owner = ViewOwner()
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            source.task.addOnViewSuccessListener(owner) { deliveries++ }
            source.setResult("ready")
        }
        instrumentation.waitForIdleSync()
        assertEquals(0, deliveries)
        instrumentation.runOnMainSync { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        instrumentation.waitForIdleSync()
        assertEquals(1, deliveries)
        instrumentation.runOnMainSync {
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        }
        assertEquals(1, deliveries)
    }

    @Test fun destroyedViewDiscardsLateSuccessAndFailure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val success = TaskCompletionSource<String>()
        val failure = TaskCompletionSource<String>()
        var deliveries = 0
        instrumentation.runOnMainSync {
            val owner = ViewOwner()
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            success.task.addOnViewSuccessListener(owner) { deliveries++ }
            failure.task.addOnViewFailureListener(owner) { deliveries++ }
            owner.lifecycle.currentState = Lifecycle.State.DESTROYED
            success.setResult("late")
            failure.setException(IllegalStateException("late"))
        }
        instrumentation.waitForIdleSync()
        assertEquals(0, deliveries)
    }
}

package com.google.mediapipe.examples.poselandmarker.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.launch

/** Deliver a one-shot result on resume, or discard it when its original view is destroyed. */
fun LifecycleOwner.deliverWhenResumed(block: () -> Unit) {
    lifecycleScope.launch {
        lifecycle.withResumed(block)
    }
}

fun <T> Task<T>.addOnViewSuccessListener(owner: LifecycleOwner, block: (T) -> Unit): Task<T> =
    addOnSuccessListener { result -> owner.deliverWhenResumed { block(result) } }

fun <T> Task<T>.addOnViewFailureListener(owner: LifecycleOwner, block: (Exception) -> Unit): Task<T> =
    addOnFailureListener { error -> owner.deliverWhenResumed { block(error) } }

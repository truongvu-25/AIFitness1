package com.google.mediapipe.examples.poselandmarker.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyStepCounterTest {
    @Test fun firstReadingDoesNotCountStepsFromPreviousDays() {
        assertEquals(0, DailyStepCounter("today").record("today", 8000))
    }
    @Test fun countsOnlyNewSteps() {
        val counter = DailyStepCounter("today", 20, 8000)
        assertEquals(25, counter.record("today", 8005))
        assertEquals(25, counter.record("today", 8005))
    }
    @Test fun midnightStartsNewDailyTotal() {
        val counter = DailyStepCounter("yesterday", 500, 8000)
        assertEquals(0, counter.record("today", 8004))
        assertEquals(3, counter.record("today", 8007))
    }
    @Test fun hardwareResetPreservesAlreadyRecordedSteps() {
        assertEquals(507, DailyStepCounter("today", 500, 8000).record("today", 7))
    }
    @Test fun restoredServiceContinuesSameDay() {
        assertEquals(520, DailyStepCounter("today", 500, 8000).record("today", 8020))
    }
    @Test fun firstReadingAfterKnownRebootKeepsSavedTotal() {
        val counter = DailyStepCounter("today", 500)
        assertEquals(500, counter.record("today", 10))
        assertEquals(504, counter.record("today", 14))
    }
    @Test fun invalidReadingDoesNotResetCounter() {
        val counter = DailyStepCounter("today", 500, 8000)
        assertEquals(500, counter.record("today", -1))
        assertEquals(505, counter.record("today", 8005))
    }
}

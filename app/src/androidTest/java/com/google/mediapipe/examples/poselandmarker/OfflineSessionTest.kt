package com.google.mediapipe.examples.poselandmarker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.poselandmarker.data.local.TriForceDatabase
import com.google.mediapipe.examples.poselandmarker.data.local.toLocalEntity
import com.google.mediapipe.examples.poselandmarker.data.local.toModel
import com.google.mediapipe.examples.poselandmarker.model.WorkoutSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineSessionTest {
    private lateinit var database: TriForceDatabase

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TriForceDatabase::class.java).build()
    }

    @After fun closeDatabase() = database.close()

    @Test fun feedbackEditedDuringUploadRemainsPending() = runBlocking {
        val dao = database.workoutSessionDao()
        dao.upsert(WorkoutSession(id = "session", exerciseId = "squat", difficulty = "easy").toLocalEntity("alice"))
        dao.updateDifficulty("session", "hard")
        dao.markFirestoreSynced("session", "easy")
        assertFalse(dao.getById("session")!!.firestoreSynced)
        dao.markFirestoreSynced("session", "hard")
        assertTrue(dao.getById("session")!!.firestoreSynced)
    }

    @Test fun cloudCacheDoesNotOverwritePendingFeedback() = runBlocking {
        val dao = database.workoutSessionDao()
        val original = WorkoutSession(id = "session", exerciseId = "squat", difficulty = "hard")
        dao.upsert(original.toLocalEntity("alice"))
        dao.insertIfAbsent(original.copy(difficulty = "easy").toLocalEntity("alice", firestoreSynced = true))
        assertEquals("hard", dao.getById("session")!!.difficulty)
        assertFalse(dao.getById("session")!!.firestoreSynced)
    }

    @Test fun pendingQueriesAreIsolatedByAccount() = runBlocking {
        val dao = database.workoutSessionDao()
        dao.upsert(WorkoutSession(id = "a").toLocalEntity("alice"))
        dao.upsert(WorkoutSession(id = "b").toLocalEntity("bob"))
        assertEquals(listOf("a"), dao.getPending("alice").map { it.id })
        dao.markFirestoreSynced("a", "")
        dao.markHealthSynced("a")
        assertTrue(dao.getPending("alice").isEmpty())
        assertEquals(listOf("b"), dao.getPending("bob").map { it.id })
    }

    @Test fun sessionRoundTripPreservesWorkoutEvidence() = runBlocking {
        val session = WorkoutSession(id = "session", exerciseId = "plank", actualCount = 24,
            targetCount = 30, durationSeconds = 40, formScore = 80,
            formIssues = listOf("Giữ thẳng người", "Hạ hông"), completedAt = 123456L)
        database.workoutSessionDao().upsert(session.toLocalEntity("alice"))
        assertEquals(session, database.workoutSessionDao().getById("session")!!.toModel())
    }
}

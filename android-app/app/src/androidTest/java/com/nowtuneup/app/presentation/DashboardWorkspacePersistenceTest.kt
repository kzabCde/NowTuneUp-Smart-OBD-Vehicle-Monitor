package com.nowtuneup.app.presentation

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nowtuneup.app.data.dashboard.DashboardDefaults
import com.nowtuneup.app.data.dashboard.DashboardRepository
import com.nowtuneup.app.data.local.database.NtuDatabase
import com.nowtuneup.app.data.local.entity.TripEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DashboardWorkspacePersistenceTest {
    @Test fun dashboardCrudSurvivesDatabaseReopenAndNeverDeletesTrips(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "dashboard-workspace-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, NtuDatabase::class.java, name).build()
        var database = open()
        try {
            var repository = DashboardRepository(database.dao())
            val original = DashboardDefaults.newProfile(id = "original", name = "Daily")
            repository.save(original)
            repository.save(original.copy(name = "Renamed daily"))
            database.close()
            database = open()
            repository = DashboardRepository(database.dao())
            assertEquals("Renamed daily", repository.workspace.first().dashboards.single().name)
            repository.save(original.copy(id = "duplicate", name = "Daily copy"))
            val trip = database.dao().startTrip(TripEntity(startTime = 123L))
            assertTrue(trip > 0)
            assertEquals(2, repository.workspace.first().dashboards.size)
            repository.delete("original")
            database.close()
            database = open()
            repository = DashboardRepository(database.dao())
            val restored = repository.workspace.first().dashboards
            assertEquals("duplicate", restored.single().id)
            assertEquals("Daily copy", restored.single().name)
            repository.delete("duplicate")
            database.close()
            database = open()
            repository = DashboardRepository(database.dao())
            assertTrue(repository.workspace.first().dashboards.isEmpty())
            assertEquals(1, database.dao().trips().first().size)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}

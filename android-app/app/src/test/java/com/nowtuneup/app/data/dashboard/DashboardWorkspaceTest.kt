package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.data.local.entity.DashboardProfileEntity
import com.nowtuneup.app.presentation.dashboard.initialDashboardId
import com.nowtuneup.app.presentation.dashboard.orderedDashboards
import org.junit.Assert.*
import org.junit.Test

class DashboardWorkspaceTest {
    @Test fun storageIdentityOwnsProfilesEvenWhenLegacyJsonIdsCollide() {
        val config = DashboardDefaults.newProfile(id = "imported-id", name = "Saved")
        val json = DashboardCodec.export(config)
        val snapshot = decodeDashboardWorkspace(listOf(
            DashboardProfileEntity(name = "room-key-a", widgetsJson = json),
            DashboardProfileEntity(name = "room-key-b", widgetsJson = json),
        ))
        assertEquals(listOf("room-key-a", "room-key-b"), snapshot.dashboards.map { it.id })
        assertEquals(2, snapshot.issues.size)
        assertTrue(snapshot.issues.none { it.unreadable })
    }

    @Test fun malformedLayoutRemainsVisibleWithoutDeletingOrReinitializingRows() {
        val raw = DashboardProfileEntity(name = "damaged", widgetsJson = "{ not valid JSON")
        val snapshot = decodeDashboardWorkspace(listOf(raw))
        assertEquals("damaged", snapshot.dashboards.single().id)
        assertTrue(snapshot.dashboards.single().portrait.widgets.isEmpty())
        assertTrue(snapshot.issues.single().unreadable)
        assertEquals("{ not valid JSON", raw.widgetsJson)
        assertTrue(decodeDashboardWorkspace(emptyList()).dashboards.isEmpty())
    }

    @Test fun orderIgnoresOrphansAndNeverDuplicatesDashboards() {
        val a = DashboardDefaults.newProfile(id = "a", name = "A")
        val b = DashboardDefaults.newProfile(id = "b", name = "B")
        val c = DashboardDefaults.newProfile(id = "c", name = "C")
        assertEquals(listOf("b", "a", "c"), orderedDashboards(listOf(c, a, b), listOf("deleted", "b", "b")).map { it.id })
        assertEquals(listOf("a", "b"), orderedDashboards(listOf(a, a, b), emptyList()).map { it.id })
    }

    @Test fun preferredLandingFallsBackToSavedSelectionThenExistingProfile() {
        val a = DashboardDefaults.newProfile(id = "a")
        val b = DashboardDefaults.newProfile(id = "b")
        assertEquals("b", initialDashboardId(listOf(a, b), "b", "a"))
        assertEquals("a", initialDashboardId(listOf(a, b), "deleted", "a"))
        assertEquals("a", initialDashboardId(listOf(a), "deleted", "also-deleted"))
        assertEquals("", initialDashboardId(emptyList(), "deleted", "also-deleted"))
    }
}

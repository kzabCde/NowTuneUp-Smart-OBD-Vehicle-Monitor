package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.entity.DashboardProfileEntity
import com.nowtuneup.app.domain.model.DashboardConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class DashboardStorageIssue(val id: String, val message: String, val unreadable: Boolean = false)
data class DashboardWorkspaceSnapshot(
    val dashboards: List<DashboardConfig> = emptyList(),
    val issues: List<DashboardStorageIssue> = emptyList(),
)

/** No templates are inserted into Room implicitly, including after deleting the final profile. */
class DashboardRepository @Inject constructor(private val dao: NtuDao) {
    val workspace: Flow<DashboardWorkspaceSnapshot> = dao.profiles().map(::decodeDashboardWorkspace)
    val dashboards: Flow<List<DashboardConfig>> = workspace.map { it.dashboards }

    suspend fun save(config: DashboardConfig) {
        val serialized = DashboardCodec.export(config.copy(isDefault = false))
        DashboardCodec.import(serialized).getOrThrow()
        dao.saveProfile(DashboardProfileEntity(name = config.id, widgetsJson = serialized))
    }

    /** Deletes the single dashboard row only; trips, readings, vehicles and runs are unrelated. */
    suspend fun delete(id: String) = dao.deleteProfile(id)
}

/** The Room key owns identity. Imported JSON cannot alias another saved profile or create duplicates. */
internal fun decodeDashboardWorkspace(saved: List<DashboardProfileEntity>): DashboardWorkspaceSnapshot {
    val issues = mutableListOf<DashboardStorageIssue>()
    val profiles = saved.distinctBy { it.name }.map { entity ->
        val imported = DashboardCodec.import(entity.widgetsJson)
        val config = imported.getOrNull()
        if (config == null) {
            issues += DashboardStorageIssue(entity.name, "Saved layout could not be read. Its original data is retained until you explicitly reset or delete it.", unreadable = true)
            DashboardDefaults.newProfile(id = entity.name, name = "Recovered dashboard")
        } else {
            if (config.id != entity.name) issues += DashboardStorageIssue(entity.name, "A legacy identity was repaired using its saved storage key.")
            config.copy(id = entity.name, isDefault = false)
        }
    }
    return DashboardWorkspaceSnapshot(profiles, issues)
}

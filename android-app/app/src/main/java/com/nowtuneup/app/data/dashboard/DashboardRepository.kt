package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.entity.DashboardProfileEntity
import com.nowtuneup.app.domain.model.DashboardConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DashboardRepository @Inject constructor(private val dao: NtuDao) {
    /**
     * Only profiles that exist in local storage are exposed in 1.6.2. Legacy built-in profiles that
     * users actually saved are converted to normal editable profiles while keeping the same id, so
     * selection, replacement and deletion continue to address the original Room row correctly.
     */
    val dashboards: Flow<List<DashboardConfig>> = dao.profiles().map { saved ->
        saved.mapNotNull { DashboardCodec.import(it.widgetsJson).getOrNull() }
            .map(::asUserProfile)
            .distinctBy { it.id }
    }

    suspend fun save(config: DashboardConfig) = dao.saveProfile(
        DashboardProfileEntity(
            name = config.id,
            widgetsJson = DashboardCodec.export(config.copy(isDefault = false)),
        ),
    )

    suspend fun delete(id: String) = dao.deleteProfile(id)

    private fun asUserProfile(config: DashboardConfig): DashboardConfig {
        if (!config.isDefault && config.id !in DashboardDefaults.legacyPresetIds) {
            return config
        }
        return config.copy(
            name = "${config.name} ที่บันทึกไว้",
            isDefault = false,
        )
    }
}

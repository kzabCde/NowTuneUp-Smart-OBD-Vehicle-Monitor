package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.entity.DashboardProfileEntity
import com.nowtuneup.app.domain.model.DashboardConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DashboardRepository @Inject constructor(private val dao: NtuDao) {
    val dashboards: Flow<List<DashboardConfig>> = dao.profiles().map { saved ->
        val custom = saved.mapNotNull { DashboardCodec.import(it.widgetsJson).getOrNull() }
        DashboardDefaults.presets.map { preset -> custom.firstOrNull { it.id == preset.id } ?: preset } +
            custom.filter { candidate -> DashboardDefaults.presets.none { it.id == candidate.id } }
    }

    suspend fun save(config: DashboardConfig) = dao.saveProfile(
        DashboardProfileEntity(name = config.id, widgetsJson = DashboardCodec.export(config)),
    )

    suspend fun delete(id: String) = dao.deleteProfile(id)
}

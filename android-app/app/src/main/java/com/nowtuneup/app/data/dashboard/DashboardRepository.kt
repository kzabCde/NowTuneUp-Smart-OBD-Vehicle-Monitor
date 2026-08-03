package com.nowtuneup.app.data.dashboard

import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.entity.DashboardProfileEntity
import com.nowtuneup.app.domain.model.DashboardConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DashboardRepository @Inject constructor(private val dao: NtuDao) {
    /**
     * Version 1.6.2 exposes only profiles that actually exist in local storage. No bundled layout is
     * injected when storage is empty. A previously saved built-in profile keeps its original id and
     * name, but is treated as a normal editable user profile from this point onward.
     */
    val dashboards: Flow<List<DashboardConfig>> = dao.profiles().map { saved ->
        saved.mapNotNull { DashboardCodec.import(it.widgetsJson).getOrNull() }
            .map { it.copy(isDefault = false) }
            .distinctBy { it.id }
    }

    suspend fun save(config: DashboardConfig) = dao.saveProfile(
        DashboardProfileEntity(
            name = config.id,
            widgetsJson = DashboardCodec.export(config.copy(isDefault = false)),
        ),
    )

    suspend fun delete(id: String) = dao.deleteProfile(id)
}

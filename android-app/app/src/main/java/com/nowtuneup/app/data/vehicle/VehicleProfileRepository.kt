package com.nowtuneup.app.data.vehicle

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nowtuneup.app.domain.model.VehicleProfileRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleProfileRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<VehicleProfileRecord>>() {}.type

    fun list(): List<VehicleProfileRecord> = runCatching {
        val json = preferences.getString(KEY_PROFILES, null) ?: return emptyList()
        gson.fromJson<List<VehicleProfileRecord>>(json, type).orEmpty()
            .filter { it.vin.isNotBlank() }
            .sortedByDescending { it.lastSeenAtMillis }
    }.getOrDefault(emptyList())

    fun find(vin: String?): VehicleProfileRecord? {
        val normalized = vin?.trim()?.uppercase()?.takeIf { it.length == 17 } ?: return null
        return list().firstOrNull { it.vin == normalized }
    }

    fun save(profile: VehicleProfileRecord) {
        val normalizedVin = profile.vin.trim().uppercase()
        if (normalizedVin.length != 17) return
        val updated = (list().filterNot { it.vin == normalizedVin } + profile.copy(vin = normalizedVin))
            .sortedByDescending { it.lastSeenAtMillis }
            .take(MAX_PROFILES)
        preferences.edit().putString(KEY_PROFILES, gson.toJson(updated, type)).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "ntu_vehicle_profiles"
        private const val KEY_PROFILES = "profiles_v1"
        private const val MAX_PROFILES = 20
    }
}

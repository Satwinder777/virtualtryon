package com.shergill.tryon.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shergill.tryon.domain.AccessoryType
import com.shergill.tryon.domain.CalibrationOffsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calibrationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tryon_calibration_v3",
)

class CalibrationRepository(private val context: Context) {

    fun observe(type: AccessoryType): Flow<CalibrationOffsets> =
        context.calibrationDataStore.data.map { prefs ->
            CalibrationOffsets(
                offsetX = prefs[key(type, "x")] ?: 0f,
                offsetY = prefs[key(type, "y")] ?: 0f,
                offsetZ = prefs[key(type, "z")] ?: 0f,
                scale = prefs[key(type, "scale")] ?: 1f,
                rotationYawDeg = prefs[key(type, "yaw")] ?: 0f,
                rotationPitchDeg = prefs[key(type, "pitch")] ?: 0f,
                rotationRollDeg = prefs[key(type, "roll")] ?: 0f,
            )
        }

    suspend fun save(type: AccessoryType, offsets: CalibrationOffsets) {
        context.calibrationDataStore.edit { prefs ->
            prefs[key(type, "x")] = offsets.offsetX
            prefs[key(type, "y")] = offsets.offsetY
            prefs[key(type, "z")] = offsets.offsetZ
            prefs[key(type, "scale")] = offsets.scale
            prefs[key(type, "yaw")] = offsets.rotationYawDeg
            prefs[key(type, "pitch")] = offsets.rotationPitchDeg
            prefs[key(type, "roll")] = offsets.rotationRollDeg
        }
    }

    private fun key(type: AccessoryType, field: String) =
        floatPreferencesKey("${type.name.lowercase()}_$field")
}

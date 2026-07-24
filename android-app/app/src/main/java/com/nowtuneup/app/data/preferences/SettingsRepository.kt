package com.nowtuneup.app.data.preferences
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
private val Context.dataStore by preferencesDataStore("ntu_settings")
data class AppSettings(val imperial:Boolean=false,val fahrenheit:Boolean=false,val keepAwake:Boolean=true,val autoReconnect:Boolean=true,val landscape:Boolean=true,val debug:Boolean=false,val recordingSeconds:Int=1)
class SettingsRepository @Inject constructor(@ApplicationContext private val context:Context){
 val settings=context.dataStore.data.map{AppSettings(it[booleanPreferencesKey("imperial")]?:false,it[booleanPreferencesKey("fahrenheit")]?:false,it[booleanPreferencesKey("awake")]?:true,it[booleanPreferencesKey("reconnect")]?:true,it[booleanPreferencesKey("landscape")]?:true,it[booleanPreferencesKey("debug")]?:false,it[intPreferencesKey("recording_seconds")]?:1)}
 suspend fun setBoolean(key:String,value:Boolean)=context.dataStore.edit{it[booleanPreferencesKey(key)]=value}
}

package com.nowtuneup.app.util
import com.nowtuneup.app.data.local.entity.SampleEntity
import java.io.Writer
object CsvExporter { const val HEADER="timestamp,trip_id,rpm,speed_kmh,coolant_temp_c,voltage_v,engine_load_percent,throttle_percent"
 fun write(writer:Writer,samples:List<SampleEntity>){writer.appendLine(HEADER);samples.forEach{writer.appendLine(listOf(it.timestamp,it.tripId,it.rpm?:"",it.speedKmh?:"",it.coolantTempC?:"",it.voltageV?:"",it.engineLoadPercent?:"",it.throttlePercent?:"").joinToString(","))}}
}

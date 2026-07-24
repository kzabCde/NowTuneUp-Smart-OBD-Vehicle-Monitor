package com.nowtuneup.app.data.obd.parser
import com.nowtuneup.app.domain.model.*

data class NormalizedResponse(val raw:String,val frames:List<List<Int>>,val informational:List<String>)
object ObdResponseParser {
 private val errors=listOf("NO DATA","STOPPED","UNABLE TO CONNECT","BUS INIT ERROR","CAN ERROR","ERROR","?")
 fun normalize(raw:String,command:String?=null):Result<NormalizedResponse> = runCatching {
  val lines=raw.replace(">","").lines().map{it.trim()}.filter{it.isNotEmpty() && !it.equals(command,true)}
  lines.firstOrNull{it.uppercase() in errors}?.let { if(it.equals("NO DATA",true)) throw ObdException(ObdError.NoData) else throw ObdException(ObdError.InvalidResponse(raw)) }
  val info=lines.filter{it.uppercase().startsWith("SEARCHING")}
  val frames=lines.filterNot{it in info}.map { line ->
   val cleaned=line.replace(" ",""); require(cleaned.length%2==0 && cleaned.matches(Regex("[0-9A-Fa-f]+"))){"Invalid hexadecimal response"}
   cleaned.chunked(2).map{it.toInt(16)}
  }
  require(frames.isNotEmpty()){ "No data frames" }; NormalizedResponse(raw,frames,info)
 }
 fun parseMode1(raw:String,pid:Int,command:String?=null):Result<Double> = normalize(raw,command).mapCatching { n ->
  val frame=n.frames.firstOrNull{it.size>=2 && it[0]==0x41 && it[1]==pid} ?: error("Response mode/PID mismatch")
  com.nowtuneup.app.data.obd.pid.StandardPids.parse(pid,frame.drop(2))
 }
}
class ObdException(val error:ObdError):Exception(error.toString())
object DtcParser {
 private val descriptions=mapOf("P0300" to "Random/multiple cylinder misfire detected","P0420" to "Catalyst system efficiency below threshold")
 fun parse(raw:String):List<Dtc> { val normalized=ObdResponseParser.normalize(raw,"03").getOrElse{return emptyList()}; return normalized.frames.flatMap { f ->
  if(f.firstOrNull()!=0x43) emptyList() else f.drop(1).chunked(2).filter{it.size==2 && (it[0]!=0||it[1]!=0)}.map { b ->
   val prefix="PCBU"[(b[0] shr 6) and 3]; val code = buildString { append(prefix); append((b[0] shr 4) and 3); append((b[0] and 15).toString(16)); append(((b[1] shr 4) and 15).toString(16)); append((b[1] and 15).toString(16)) }.uppercase(); Dtc(code,prefix.toString(),description=descriptions[code],raw=raw)
  }
 } }
}

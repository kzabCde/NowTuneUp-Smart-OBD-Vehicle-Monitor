package com.nowtuneup.app.data.obd.pid

data class ObdPidDefinition(val mode:Int,val pid:Int,val name:String,val unit:String,val minimum:Double?,val maximum:Double?,val bytes:Int,val parse:(List<Int>)->Double)
object StandardPids {
 private fun pct(a:List<Int>)=a[0]*100.0/255
 val all=listOf(
  ObdPidDefinition(1,0x04,"Engine load","%",0.0,100.0,1,::pct),
  ObdPidDefinition(1,0x05,"Coolant temperature","°C",-40.0,215.0,1,{it[0]-40.0}),
  ObdPidDefinition(1,0x0C,"Engine RPM","rpm",0.0,16383.75,2,{(it[0]*256+it[1])/4.0}),
  ObdPidDefinition(1,0x0D,"Vehicle speed","km/h",0.0,255.0,1,{it[0].toDouble()}),
  ObdPidDefinition(1,0x0F,"Intake air temperature","°C",-40.0,215.0,1,{it[0]-40.0}),
  ObdPidDefinition(1,0x10,"Mass air flow","g/s",0.0,655.35,2,{(it[0]*256+it[1])/100.0}),
  ObdPidDefinition(1,0x11,"Throttle position","%",0.0,100.0,1,::pct),
  ObdPidDefinition(1,0x2F,"Fuel level","%",0.0,100.0,1,::pct),
  ObdPidDefinition(1,0x42,"Control module voltage","V",0.0,65.535,2,{(it[0]*256+it[1])/1000.0})
 )
 fun find(pid:Int)=all.firstOrNull{it.pid==pid}
 fun parse(pid:Int,data:List<Int>):Double { val d=find(pid)?:error("Unsupported PID"); require(data.size>=d.bytes){"Insufficient PID data"}; return d.parse(data) }
}
object SupportedPidParser {
 fun parse(base:Int, bytes:List<Int>):Set<Int> { require(bytes.size>=4); val mask=bytes.take(4).fold(0L){a,b->(a shl 8) or (b and 0xff).toLong()}; return (1..32).filterTo(mutableSetOf()){ mask and (1L shl (32-it)) != 0L }.mapTo(mutableSetOf()){base+it} }
}

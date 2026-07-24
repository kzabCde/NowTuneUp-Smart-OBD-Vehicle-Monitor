package com.nowtuneup.app.domain.model

enum class ConnectionState { DISCONNECTED, DEVICE_DETECTED, REQUESTING_PERMISSION, CONNECTING, INITIALIZING, CONNECTED, ERROR }
sealed interface ObdError {
    data object UsbPermissionDenied: ObdError; data object DeviceNotFound: ObdError; data object PortOpenFailed: ObdError
    data class InitializationFailed(val step:String): ObdError; data object EcuNotResponding: ObdError; data object NoData: ObdError
    data object Timeout: ObdError; data object DeviceDisconnected: ObdError; data class InvalidResponse(val raw:String): ObdError
    data class Unknown(val message:String): ObdError
}
data class UsbDeviceInfo(val id:Int,val name:String,val vendorId:Int,val productId:Int,val supported:Boolean)
data class VehicleReading(val pid:Int,val name:String,val value:Double?,val unit:String,val supported:Boolean=true,val updatedAt:Long=System.currentTimeMillis(),val minimum:Double?=null,val maximum:Double?=null)
data class Dtc(val code:String,val category:String,val status:String="Stored",val description:String?,val raw:String,val readAt:Long=System.currentTimeMillis())
data class DashboardWidget(val pid:Int,val type:WidgetType=WidgetType.CARD,val size:WidgetSize=WidgetSize.MEDIUM,val history:Boolean=false)
enum class WidgetType { GAUGE, DIGITAL, CARD, METER, CHART }
enum class WidgetSize { SMALL, MEDIUM, LARGE }
data class DashboardProfile(val id:Long=0,val name:String,val widgets:List<DashboardWidget>)

package com.nowtuneup.app.domain.model

enum class ConnectionState {
    DISCONNECTED,
    DEVICE_DETECTED,
    REQUESTING_PERMISSION,
    CONNECTING,
    INITIALIZING,
    CONNECTED,
    ERROR,
}

enum class ObdTransportType { USB, BLUETOOTH_CLASSIC, BLE_EXPERIMENTAL, MOCK }

enum class ConnectionPhase {
    BLUETOOTH_UNAVAILABLE,
    BLUETOOTH_DISABLED,
    PERMISSION_REQUIRED,
    SCANNING,
    DEVICE_SELECTION,
    CONNECTING,
    INITIALIZING_ADAPTER,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    CONNECTION_FAILED,
    UNSUPPORTED_ADAPTER,
}

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val bonded: Boolean = true,
)

data class AdapterInitializationStatus(
    val currentCommand: String? = null,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val adapterIdentity: String? = null,
    val bluetoothConnected: Boolean = false,
    val adapterInitialized: Boolean = false,
    val ecuConnected: Boolean = false,
)

data class ConnectionUiState(
    val transportType: ObdTransportType = ObdTransportType.USB,
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val bluetoothSupported: Boolean = true,
    val bluetoothEnabled: Boolean = false,
    val permissionGranted: Boolean = false,
    val pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val selectedDevice: BluetoothDeviceInfo? = null,
    val initialization: AdapterInitializationStatus = AdapterInitializationStatus(),
    val lastErrorThai: String? = null,
    val technicalError: String? = null,
    val reconnectAttempt: Int = 0,
)

sealed interface ObdError {
    data object UsbPermissionDenied : ObdError
    data object BluetoothUnavailable : ObdError
    data object BluetoothDisabled : ObdError
    data object BluetoothPermissionDenied : ObdError
    data object DeviceNotFound : ObdError
    data object DeviceNotPaired : ObdError
    data object PortOpenFailed : ObdError
    data object ConnectionTimeout : ObdError
    data class InitializationFailed(val step: String) : ObdError
    data object UnsupportedAdapter : ObdError
    data object EcuNotResponding : ObdError
    data object NoData : ObdError
    data object Timeout : ObdError
    data object DeviceDisconnected : ObdError
    data object BufferFull : ObdError
    data class InvalidResponse(val raw: String) : ObdError
    data class Unknown(val message: String) : ObdError
}

data class UsbDeviceInfo(val id: Int, val name: String, val vendorId: Int, val productId: Int, val supported: Boolean)

data class VehicleReading(
    val pid: Int,
    val name: String,
    val value: Double?,
    val unit: String,
    val supported: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val minimum: Double? = null,
    val maximum: Double? = null,
)

data class Dtc(
    val code: String,
    val category: String,
    val status: String = "Stored",
    val description: String?,
    val raw: String,
    val readAt: Long = System.currentTimeMillis(),
)

data class DashboardWidget(
    val pid: Int,
    val type: WidgetType = WidgetType.CARD,
    val size: WidgetSize = WidgetSize.MEDIUM,
    val history: Boolean = false,
)

enum class WidgetType { GAUGE, DIGITAL, CARD, METER, CHART }
enum class WidgetSize { SMALL, MEDIUM, LARGE }
data class DashboardProfile(val id: Long = 0, val name: String, val widgets: List<DashboardWidget>)

enum class DashboardMode { DIGITAL, ANALOG, HYBRID }
enum class DashboardWidgetType { DIGITAL, DIGITAL_RING, ANALOG, MINI_GAUGE, PROGRESS, DTC_CARD }
enum class GaugeStyle { CLASSIC, SPORT, MINIMAL, NEON, OEM }
enum class DigitalRingColorPreset { AMBER, CYAN, GREEN, RED, PURPLE, WHITE, CUSTOM }
enum class DisplayUnit { RPM, KMH, MPH, CELSIUS, FAHRENHEIT, VOLT, PERCENT, KPA, BAR, PSI, LITER, GALLON, NONE }
enum class RefreshRate(val intervalMillis: Long) { LOW(1_000), BALANCED(500), FAST(200) }
enum class DataFreshness { LIVE, DELAYED, STALE, NO_DATA, UNSUPPORTED, RECONNECTING }
enum class AlertSeverity { NORMAL, WARNING, CRITICAL }
enum class AdaptiveLayoutProfile { AUTO, PHONE, TABLET, HEAD_UNIT }
enum class HudColorPreset { GREEN, AMBER, CYAN, WHITE, RED }

data class WarningThreshold(
    val warningLow: Double? = null,
    val warningHigh: Double? = null,
    val criticalLow: Double? = null,
    val criticalHigh: Double? = null,
)

data class ColorConfig(
    val value: Long = 0xFFFFFFFF,
    val label: Long = 0xFFCFD8DC,
    val background: Long = 0xFF121923,
    val border: Long = 0xFF334155,
    val warning: Long = 0xFFFFB300,
    val critical: Long = 0xFFFF5252,
)

data class DigitalRingConfig(
    val preset: DigitalRingColorPreset = DigitalRingColorPreset.AMBER,
    val segmentCount: Int = 36,
    val digitColor: Long = 0xFFFFC400,
    val activeSegmentColor: Long = 0xFFFFC400,
    val inactiveSegmentColor: Long = 0xFF3A3000,
    val scaleColor: Long = 0xFFFFC400,
    val titleColor: Long = 0xFFFFC400,
    val bezelColor: Long = 0xFF30343B,
    val showScaleLabels: Boolean = true,
)

fun digitalRingPreset(preset: DigitalRingColorPreset, segmentCount: Int = 36): DigitalRingConfig {
    val active = when (preset) {
        DigitalRingColorPreset.AMBER -> 0xFFFFC400
        DigitalRingColorPreset.CYAN -> 0xFF00C8FF
        DigitalRingColorPreset.GREEN -> 0xFF2CFF35
        DigitalRingColorPreset.RED -> 0xFFFF3045
        DigitalRingColorPreset.PURPLE -> 0xFFB45CFF
        DigitalRingColorPreset.WHITE -> 0xFFF5F7FA
        DigitalRingColorPreset.CUSTOM -> 0xFFFFC400
    }
    val inactive = when (preset) {
        DigitalRingColorPreset.AMBER -> 0xFF3A3000
        DigitalRingColorPreset.CYAN -> 0xFF003647
        DigitalRingColorPreset.GREEN -> 0xFF073A0A
        DigitalRingColorPreset.RED -> 0xFF430812
        DigitalRingColorPreset.PURPLE -> 0xFF2E1647
        DigitalRingColorPreset.WHITE -> 0xFF35383C
        DigitalRingColorPreset.CUSTOM -> 0xFF3A3000
    }
    return DigitalRingConfig(
        preset = preset,
        segmentCount = segmentCount.coerceIn(12, 72),
        digitColor = active,
        activeSegmentColor = active,
        inactiveSegmentColor = inactive,
        scaleColor = active,
        titleColor = active,
        bezelColor = 0xFF30343B,
    )
}

data class DashboardWidgetConfig(
    val id: String,
    val pid: Int,
    val type: DashboardWidgetType,
    val title: String,
    val unit: DisplayUnit,
    val decimals: Int = 1,
    val valueSize: Int = 34,
    val column: Int = 0,
    val row: Int = 0,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val gaugeStyle: GaugeStyle = GaugeStyle.CLASSIC,
    val colors: ColorConfig = ColorConfig(),
    val threshold: WarningThreshold = WarningThreshold(),
    val digitalRing: DigitalRingConfig? = null,
)

data class DashboardLayout(val columns: Int = 2, val widgets: List<DashboardWidgetConfig> = emptyList())

data class DashboardConfig(
    val id: String,
    val name: String,
    val mode: DashboardMode,
    val portrait: DashboardLayout,
    val landscape: DashboardLayout = portrait.copy(columns = 3),
    val isDefault: Boolean = false,
)

data class ThemeConfig(
    val name: String = "Dark",
    val primary: Long = 0xFF00E5FF,
    val secondary: Long = 0xFFFFB300,
    val accent: Long = 0xFF00E5FF,
    val background: Long = 0xFF090D12,
    val card: Long = 0xFF121923,
    val text: Long = 0xFFEAF7FA,
    val gaugeNeedle: Long = 0xFF00E5FF,
    val gaugeTick: Long = 0xFF90A4AE,
    val warning: Long = 0xFFFFB300,
    val critical: Long = 0xFFFF5252,
    val success: Long = 0xFF4CAF50,
    val border: Long = 0xFF334155,
)

data class ReadingStats(
    val minimum: Double? = null,
    val maximum: Double? = null,
    val peak: Double? = null,
    val updatedAt: Long = 0L,
)

data class DashboardAlert(
    val widgetId: String,
    val title: String,
    val value: Double,
    val unit: DisplayUnit,
    val severity: AlertSeverity,
    val timestamp: Long = System.currentTimeMillis(),
)

data class DashboardPreferences(
    val selectedDashboardId: String = "daily",
    val theme: ThemeConfig = ThemeConfig(),
    val reduceMotion: Boolean = false,
    val refreshRate: RefreshRate = RefreshRate.BALANCED,
    val drivingMode: Boolean = false,
    val focusMode: Boolean = false,
    val autoFocusOnConnect: Boolean = false,
    val controlsAutoHideSeconds: Int = 4,
    val touchLock: Boolean = false,
    val keepScreenOn: Boolean = true,
    val swipePages: Boolean = true,
    val resumeFocusMode: Boolean = true,
    val showPeakHold: Boolean = true,
    val showMinMax: Boolean = true,
    val alertSound: Boolean = true,
    val alertVibration: Boolean = true,
    val muteAlerts: Boolean = false,
    val alertCooldownSeconds: Int = 15,
    val hysteresis: Double = 2.0,
    val delayedAfterMillis: Long = 1_200L,
    val staleAfterMillis: Long = 3_000L,
    val autoReconnect: Boolean = true,
    val reconnectIntervalSeconds: Int = 3,
    val reconnectAttempts: Int = 5,
    val reconnectMaxDelaySeconds: Int = 30,
    val hudMode: Boolean = false,
    val hudMirror: Boolean = true,
    val hudBurnInProtection: Boolean = true,
    val hudBrightnessPercent: Int = 100,
    val hudColorPreset: HudColorPreset = HudColorPreset.GREEN,
    val adaptiveLayoutProfile: AdaptiveLayoutProfile = AdaptiveLayoutProfile.AUTO,
    val headUnitImmersive: Boolean = true,
    val preferredTransport: ObdTransportType = ObdTransportType.USB,
    val lastBluetoothAddress: String? = null,
    val autoConnectLastAdapter: Boolean = false,
    val continuousMonitoring: Boolean = false,
    val diagnosticLogging: Boolean = true,
)
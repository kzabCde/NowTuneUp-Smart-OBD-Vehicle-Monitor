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
enum class GaugeStyle { CLASSIC_METAL, SPORT_RED, NEO_CYAN, RACING_AMBER, OEM_BLUE, HUD_GREEN, CUSTOM }
enum class BezelFinish { BRUSHED_STEEL, BLACK_CHROME, TITANIUM_DARK }
enum class GaugeSmoothing { FAST, BALANCED, SMOOTH }
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
    val face: Long? = null,
    val bezel: Long? = null,
    val tick: Long? = null,
    val needle: Long? = null,
    val needleHighlight: Long? = null,
    val glow: Long? = null,
)

data class GaugeStylePreset(
    val style: GaugeStyle,
    val bezelFinish: BezelFinish,
    val face: Long,
    val bezel: Long,
    val tick: Long,
    val label: Long,
    val value: Long,
    val needle: Long,
    val needleHighlight: Long,
    val warning: Long,
    val critical: Long,
    val glow: Long,
)

val userVisibleGaugeStyles: List<GaugeStyle> = listOf(
    GaugeStyle.CLASSIC_METAL,
    GaugeStyle.SPORT_RED,
    GaugeStyle.NEO_CYAN,
    GaugeStyle.RACING_AMBER,
    GaugeStyle.OEM_BLUE,
    GaugeStyle.HUD_GREEN,
    GaugeStyle.CUSTOM,
)

fun premiumGaugePreset(style: GaugeStyle): GaugeStylePreset = when (style) {
    GaugeStyle.CLASSIC_METAL -> GaugeStylePreset(
        style, BezelFinish.BRUSHED_STEEL, 0xFF080B0E, 0xFF8A9198, 0xFFF3EBDD,
        0xFFF3EBDD, 0xFFFFFFFF, 0xFFFF334D, 0xFFFFB3BD, 0xFFFFB300, 0xFFFF3D4D, 0x66FFFFFF,
    )
    GaugeStyle.SPORT_RED -> GaugeStylePreset(
        style, BezelFinish.BLACK_CHROME, 0xFF08080A, 0xFF25262A, 0xFFFFE8E8,
        0xFFFFF5F5, 0xFFFF4A5E, 0xFFFF1744, 0xFFFFA0AF, 0xFFFF9800, 0xFFFF1744, 0x88FF1744,
    )
    GaugeStyle.NEO_CYAN -> GaugeStylePreset(
        style, BezelFinish.TITANIUM_DARK, 0xFF061017, 0xFF24343D, 0xFF34DFFF,
        0xFFDDFBFF, 0xFF35E6FF, 0xFFF5FCFF, 0xFF35E6FF, 0xFFFFB300, 0xFFFF4056, 0x8835E6FF,
    )
    GaugeStyle.RACING_AMBER -> GaugeStylePreset(
        style, BezelFinish.BLACK_CHROME, 0xFF100C04, 0xFF29251D, 0xFFFFC247,
        0xFFFFF0C7, 0xFFFFC247, 0xFFFFF5D8, 0xFFFFD97A, 0xFFFF8F00, 0xFFFF3D3D, 0x88FFC247,
    )
    GaugeStyle.OEM_BLUE -> GaugeStylePreset(
        style, BezelFinish.BRUSHED_STEEL, 0xFF07101C, 0xFF596775, 0xFF6BB7FF,
        0xFFE9F4FF, 0xFF5AAEFF, 0xFFFF334D, 0xFFFFA5B2, 0xFFFFB300, 0xFFFF3D4D, 0x775AAEFF,
    )
    GaugeStyle.HUD_GREEN -> GaugeStylePreset(
        style, BezelFinish.BLACK_CHROME, 0xFF020906, 0xFF1B2822, 0xFF48FF8A,
        0xFFD9FFE7, 0xFF48FF8A, 0xFFFFFFFF, 0xFF8AFFB0, 0xFFFFD600, 0xFFFF3D4D, 0x8848FF8A,
    )
    GaugeStyle.CUSTOM -> GaugeStylePreset(
        style, BezelFinish.TITANIUM_DARK, 0xFF080D12, 0xFF37424C, 0xFF00D9FF,
        0xFFE8F7FA, 0xFF00E5FF, 0xFFFF334D, 0xFFFFA5B2, 0xFFFFB300, 0xFFFF5252, 0x7700E5FF,
    )
}

fun DashboardWidgetConfig.resolvedGaugePreset(): GaugeStylePreset {
    val base = premiumGaugePreset(gaugeStyle)
    return base.copy(
        bezelFinish = bezelFinish ?: base.bezelFinish,
        face = colors.face ?: colors.background,
        bezel = colors.bezel ?: colors.border.takeIf { gaugeStyle == GaugeStyle.CUSTOM } ?: base.bezel,
        tick = colors.tick ?: colors.label.takeIf { gaugeStyle == GaugeStyle.CUSTOM } ?: base.tick,
        label = colors.label.takeIf { gaugeStyle == GaugeStyle.CUSTOM } ?: base.label,
        value = colors.value.takeIf { gaugeStyle == GaugeStyle.CUSTOM } ?: base.value,
        needle = colors.needle ?: colors.value.takeIf { gaugeStyle == GaugeStyle.CUSTOM } ?: base.needle,
        needleHighlight = colors.needleHighlight ?: base.needleHighlight,
        warning = colors.warning,
        critical = colors.critical,
        glow = colors.glow ?: base.glow,
    )
}

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
    val gaugeStyle: GaugeStyle = GaugeStyle.CLASSIC_METAL,
    val bezelFinish: BezelFinish? = null,
    val gaugeSmoothing: GaugeSmoothing? = null,
    val showPeakMarker: Boolean = true,
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
    val name: String = "Dark OEM",
    val primary: Long = 0xFF00E5FF,
    val secondary: Long = 0xFFFFB300,
    val accent: Long = 0xFF00E5FF,
    val background: Long = 0xFF070A0E,
    val card: Long = 0xFF10161D,
    val text: Long = 0xFFEAF7FA,
    val gaugeNeedle: Long = 0xFF00E5FF,
    val gaugeTick: Long = 0xFF90A4AE,
    val warning: Long = 0xFFFFB300,
    val critical: Long = 0xFFFF5252,
    val success: Long = 0xFF4CAF50,
    val border: Long = 0xFF2A3540,
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
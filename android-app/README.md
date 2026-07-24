# NTU — Smart OBD Vehicle Monitor

NTU is a local-first, read-only Android OBD-II dashboard for ELM327-compatible USB adapters. It uses Kotlin, Jetpack Compose/Material 3, coroutines/StateFlow, Hilt, Room, DataStore, Android USB Host, and usb-serial-for-android. Core vehicle monitoring requires no account, backend, location, or internet permission.

> **Safety:** NTU reads standardized emissions diagnostics only. It does not clear DTCs, write/coding/flash ECUs, modify immobilizers, airbags, or odometers. Do not interact with the app while driving. A reading alone is not a mechanical diagnosis.

## Features

- USB serial discovery/opening and permission entry point for ELM327 adapters; prompt-bounded reads and cable failure handling.
- Sequential `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0` initialization and a serialized, cancellable, bounded-retry command queue.
- Robust echo/prompt normalization, informational/error recognition, multiple-ECU frames, PID/mode validation, supported-PID mask parsing, and stored Mode 03 DTC parsing.
- Customizable Digital, Analog, and Hybrid dashboards with six built-in presets, portrait/landscape layouts, five gauge styles, threshold colors, accessible themes, Reduce Motion, and Driving Mode.
- Debug-only mock transport with changing RPM/speed and representative values. Release defaults to physical USB.
- Room schema for indexed trips/samples, diagnostic scans, and dashboard profiles; DataStore settings; CSV writer compatible with Storage Access Framework destinations.
- Foreground connected-device monitoring service with persistent status and stop action.

## Architecture

```text
Jetpack Compose UI
        ↓
ViewModel / StateFlow
        ↓
ObdSessionManager
        ↓
Serialized Command Queue
        ↓
ObdTransport interface
       ↙ ↘
Mock       UsbObdTransport → ELM327 → Vehicle ECU
        ↓
Room / DataStore (local only)
```

Transport, protocol/parser/PID definitions, session orchestration, local persistence, and presentation are separate packages. A Bluetooth or Wi-Fi transport can implement `ObdTransport` without changing parser or UI code.

## Supported standard PIDs

| Command | Parameter | Unit |
|---|---|---|
| 0104 | Calculated engine load | % |
| 0105 | Coolant temperature | °C |
| 010C | Engine RPM | rpm |
| 010D | Vehicle speed | km/h |
| 010F | Intake air temperature | °C |
| 0110 | Mass air flow | g/s |
| 0111 | Throttle position | % |
| 012F | Fuel level input | % |
| 0142 | Control-module voltage | V |

Supported ranges `0100`, `0120`, `0140`, and `0160` use the standard 32-bit mask parser. A production poll schedule must use the discovered set; unsupported UI values are shown as **Not supported**, never zero.

## Hardware

- Android 8.0+ (API 26), USB Host-capable phone/tablet, and USB OTG cable/adapter.
- ELM327-compatible USB OBD-II adapter. The serial library probes CDC ACM plus FTDI FT232, WCH CH340/CH341, Silicon Labs CP210x, and Prolific PL2303 families. Clone quality and ECU compatibility vary.
- Vehicle ignition generally must be ON. Verify safe mounting before driving.

## Setup and builds

Install Android Studio with Android SDK 35 and JDK 17, then set `sdk.dir` in uncommitted `local.properties` or `ANDROID_HOME`. The committed `gradlew` is a text-only launcher: on first use it downloads the pinned Gradle 8.14.4 distribution into `GRADLE_USER_HOME`; no binary wrapper JAR is stored in this repository.

```bash
./gradlew clean
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
```

Outputs:

- `app/build/outputs/apk/debug/NowTuneUp-debug.apk`
- `app/build/outputs/apk/release/NowTuneUp-v1.1.0-release.apk`

The repository never stores signing secrets. Copy `signing.properties.example` locally and wire credentials through environment/local Gradle configuration for distributable signing. Without that setup, release output is unsigned.

## Mock mode

Debug builds inject `MockObdTransport` and can exercise connection, changing gauges, live data, and example DTCs without a vehicle. Release builds inject USB transport. The build flag is defined per variant and mock mode is never on by default in release.

## Testing

`./gradlew test` covers RPM, speed, temperature, voltage, supported masks, malformed/invalid hex, echoes, prompts, `NO DATA`, informational lines, multiple ECU responses, DTCs, response mismatch, and command timeout. Instrumented Compose tests cover dashboard values and unsupported display; execute them on an emulator/device using `./gradlew connectedDebugAndroidTest`.

## CSV export contract

The CSV writer emits:

```text
timestamp,trip_id,rpm,speed_kmh,coolant_temp_c,voltage_v,engine_load_percent,throttle_percent
```

A UI destination should be obtained with `ACTION_CREATE_DOCUMENT`; no broad storage permission is declared.

## Troubleshooting

| Problem | Check |
|---|---|
| Adapter not detected | Confirm USB Host support, OTG cable, adapter LEDs, and chipset support. |
| USB permission denied | Disconnect/reconnect, tap Connect, and approve Android’s device prompt. |
| Port cannot open | Close other serial apps; reconnect; inspect cable and chipset driver support. |
| ELM327 does not respond | Verify baud rate (default 38400), ignition, power, and adapter quality. |
| `UNABLE TO CONNECT` | Ignition may be off or the adapter cannot negotiate the vehicle protocol. |
| `NO DATA` | The ECU may not expose that PID; this is not a zero reading. |
| Cable removed | Polling must stop; reconnect the cable and initiate connection again. |
| Incorrect readings | Low-quality ELM327 clones can return malformed/stale data; compare trusted equipment. |

## Known limitations and hardware verification

Automated JVM tests and debug mock flow verify protocol formulas, response robustness, DTC decoding, bounded timeout behavior, and value rendering. Physical USB permission broadcasts, detach behavior across OEM Android builds, serial baud compatibility, ECU protocol negotiation, long-duration foreground operation, and readings **require verification with a real vehicle and adapter**. DTC descriptions are intentionally small and generic; manufacturer-specific service information remains authoritative. Release signing is owner-provided.

## Project map

```text
app/src/main/java/com/ntu/obd/
├── data/{local,obd,preferences,transport}
├── di/
├── domain/model/
├── presentation/{dashboard,theme}/
├── service/
├── util/
├── MainActivity.kt
└── NtuApplication.kt
app/src/{test,androidTest}/
docs/  scripts/  gradle/
```

## Recommended next steps

1. Complete a chipset/vehicle test matrix and tune baud/protocol fallbacks.
2. Gate polling strictly from per-ECU supported masks and add adaptive scheduling metrics.
3. Connect trip buffering and SAF activity result UX end-to-end; add aggregate queries/profile editor persistence.
4. Add USB attach/detach integration tests, notification permission UX, migrations for every schema revision, and a licensed expanded DTC dataset.
5. Configure CI with an Android SDK image, emulator UI tests, signed release provenance, and dependency/security scanning.

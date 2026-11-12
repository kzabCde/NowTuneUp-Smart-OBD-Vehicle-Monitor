# Motorsport suite audit and verification

## Baseline audit

The existing app is Kotlin/Compose/Material 3 with Hilt, ViewModel/StateFlow,
Room dashboard profiles and DataStore preferences. It supports API 26, local-first
vehicle data, USB/Bluetooth transport abstractions, and light/dark themes.

| Area | Existing behavior | Work needed |
| --- | --- | --- |
| Custom HUD Studio | Add/remove, drag, resize, z-order, duplicate, lock, properties, undo/redo, JSON transfer, independent orientation layouts | Optional factory templates, shift indicator, stricter import validation |
| Driving dashboard | Fullscreen/focus, touch lock, connection badge, warnings, freshness, min/max, unit conversion | Disconnected values could remain visible; prevent non-finite/stale statistics |
| Live data | PID search, adapter health, bounded summary history | Records repeated cached readings, no raw samples, time-axis graphs, cursor inspection, CSV or individual deletion |
| Time Slip | Standing/rolling starts, km/h targets, distance targets, source timestamps, interpolation rules, bounded history, quality checks | Add explicit 60 mph target and history search/filter/sort; improve report metadata |
| Time Slip charts | Speed, estimated acceleration/distance, gaps, cursor, compatible previous-run overlay with dashed style | Guard comparison at chart boundary; RPM/throttle only if source samples are recorded |
| Persistence | Existing Room/DataStore plus legacy local summary/Time Slip records | Preserve all legacy record formats; use separate versioned raw telemetry storage |

## Sequence

1. Dashboard: fix stale display, add optional templates and configured-only shift
   lights, validate configuration, and test before proceeding.
2. Telemetry: bounded source-sample collection independent of rendering; save raw
   recording details, show separate axes per signal, inspect measured samples,
   history/delete/CSV, and test duplicate/delayed/invalid samples.
3. Performance: preserve the timing engine, add explicit mph target, history
   controls, comparison guards, source/quality/rollout context and tests.
4. Integration: unit tests, lint, debug build and available Compose checks.

Factory templates must be copied explicitly, never overwrite user dashboards or
silently create vehicle profiles. Shift thresholds are driver-supplied values,
not manufacturer advice. No ECU-writing functionality is added by this work.

## Release gates

Real vehicle/adapter tests remain mandatory for USB permission/detach,
Bluetooth reconnect, polling frequency/latency and long-duration monitoring.
Do not label simulated data as vehicle measurements. Verify 320 dp width,
portrait/landscape, large fonts, light/dark, reduced motion, safe fullscreen exit,
touch lock, import/export and data survival across upgrade on Android hardware
or emulator before release. Performance estimates are OBD-derived and are not
GPS timing-device certification. Do not sign, tag, deploy, or publish from this
branch.

## Phase 1 implementation

- `displayableReading` gates disconnected/unsupported/stale/non-finite values;
  shift stages additionally require LIVE freshness, actual RPM and valid settings.
- New `ShiftLightConfig` is nullable in old widget records. Stages/redline are
  never inferred, and the factory Performance template leaves them disabled.
- Optional Daily Drive, Performance and Diagnostics templates appear only when
  editing an empty profile. Existing profiles and the empty-profile default remain
  intact. Factory definitions return independent copies on every call.
- Back exits fullscreen even when controls are hidden or touch is locked.
- Future-schema imports are rejected; modern non-default canvas columns survive.
- Full validation commands/results are recorded in the delivery section below.

## Phase 2 implementation

`VehicleReading.sourceTimeNanos` carries the response's original monotonic clock
from `ObdSessionManager.publish`. Old readings remain compatible (nullable field).
`TelemetryBuffer` rejects duplicates, out-of-order, stale and future responses;
invalid/disconnected events have no numerical value. Live memory retains at most
1,200 source events. Raw recordings stop at 20,000 events or one hour and retain
at most 20 saved sessions. The recorder runs in the ViewModel across navigation;
UI drawing does not create samples or control recording cadence.

`TelemetryRecording` and `TelemetrySample` persist source wall/monotonic times,
parameter, unit, quality, session identity and source label. File writes, reads,
CSV export and history commits run on IO dispatchers. Existing summary-only
history is retained and explicitly identified as having no raw measurements.
Each selected signal has its own labelled amplitude scale; gaps/invalid events
break the curve. The cursor selects measured samples, not interpolated values.
History supports reopen, SAF CSV export and confirmed individual deletion.
MOCK sources are explicitly labelled simulated, including reopened recordings.

## Phase 3 implementation

The timing engine and its existing recorded samples/calculations are preserved.
A 0–60 mph preset uses exactly 96.56064 km/h internally; it is never interchangeable
with 0–60 km/h. Local history now searches mode, target, vehicle, quality and date,
filters standing/rolling starts, groups by target and sorts by newest or valid
elapsed time. Legacy unassigned records remain visible but cannot be compared to
records of an unidentified vehicle.

Historical results support choosing any compatible comparison (previous run is
the default). The chart itself also checks compatibility, so callers cannot draw
an incompatible overlay. Curves retain solid/dashed distinctions, recorded-run
identities and available split deltas. Results/shares identify vehicle, timestamp,
source, rollout and estimated OBD timing limitations. New runs also retain available RPM/throttle observations with their original source
timestamps. Optional per-signal performance curves select actual recorded samples,
break at gaps and use compatible-run overlays. Repeated cached source observations
are deduplicated. Legacy speed-only records never gain reconstructed auxiliary curves.

## Integration and data models

- Digital readings fit their actual canvas bounds and use monospace numerical
  typography. Existing gauge styles and light theme remain available.
- The editor groups implemented renderers, allows changing telemetry source and
  restricts units to real conversions. Scale/warning fields convert when the unit
  changes. Imported incompatible unit choices show unavailable rather than a
  relabelled number. BAR/PSI conversions now work in both directions.
- Dashboard sparklines use measured source times and break across gaps. MOCK
  dashboards carry a simulation label; Time Slip cannot arm against a MOCK source.
- Diagnostics templates include the last DTC scan. A missing scan is nullable and
  says “Not scanned”; zero is used only for an actual empty scan.
- `DashboardWidgetConfig.shiftLight` and `VehicleReading.sourceTimeNanos` are new
  nullable fields. `ShiftLightConfig`, `TelemetrySample`, `TelemetryRecording`,
  `SampleQuality`, `TimeSlipSignalSample`, and `HistorySort` are new models.
  `TimeSlipTelemetrySample` gains nullable RPM/throttle source observations. Legacy `LiveSessionSummary`
  gains optional recording identity and stop reason. Room schemas and DataStore
  preferences are unchanged; no destructive migration is used.

## Executed validation

All commands run at the repository root unless noted. Current-instance tooling
is outside the checkout under `/workspace/android-tools`.

```sh
source /workspace/android-tools/env.sh
/workspace/android-tools/gradle-8.14.4/bin/gradle -p android-app \
  -I /workspace/android-tools/local-usb.init.gradle \
  testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest \
  --no-daemon --max-workers=4
```

Result: **116 JVM unit tests passed, zero failed/skipped**; Android lint passed
with existing warnings; debug application and instrumentation APKs assembled.
The suite includes dashboard compatibility, unavailable/stale data, shift stages,
raw buffer bounds, duplicate/delayed/invalid samples, JSON/CSV integrity, unit
conversion, mph targets, history filtering and the existing Time Slip/OBD tests.
A standalone Kotlin/JUnit run of 16 dashboard/codec tests also passed during
initial toolchain setup.

The standard build was attempted first and failed fetching the USB serial
artifact because this environment blocks `jitpack.io`. The local init script
supplies that one dependency built from the official GitHub `v3.9.0` source tag,
commit `e1018ab31c118b7d2e15fc7c2e9c5b19f33eb1f1`. This is not a claim that its
binary is byte-identical to the published JitPack AAR. No repository dependency
or CI declaration was changed. A normal CI build using the declared artifact is
still required. The user's authorized domain addition could not be saved because
the environment draft reader could not verify the current draft; no unknown
network list was replaced and no environment version was published.

Initial Android home/cache and SDK setup errors were resolved. Initial lint
findings in two multiline conditions were corrected without suppressions. An
intermediate overlapping validation invocation produced a missing generated
lint result; subsequent complete sequential runs passed.

```sh
/workspace/android-tools/gradle-8.14.4/bin/gradle -p android-app \
  -I /workspace/android-tools/local-usb.init.gradle \
  connectedDebugAndroidTest --no-daemon --max-workers=2
```

The software-only API 30 emulator booted, but adb property discovery repeatedly
timed out. Gradle reported **Unknown API Level / no compatible device** and the
connected test task failed before executing application tests. This is a runner
failure, not a passing Compose run. Compose coverage was compiled, including
cached-reading availability, template editing, graph themes/large text/comparison,
legacy empty states and isolated telemetry persistence. Screenshots and device UI
acceptance remain pending unless the direct adb fallback below succeeds.

## Remaining release checks

- Run the normal Gradle commands with access to `jitpack.io`, then connected tests
  on an accelerated emulator or device. Capture the new HUD/telemetry screens.
- Exercise long-duration real-adapter monitoring, memory/recomposition profiles,
  sustained recording/CSV export, screen rotation, 320 dp and large font settings.
- Verify vehicle/adapter compatibility and USB/Bluetooth disconnect/reconnect on
  physical hardware. No physical vehicle measurements were performed here.
- Verify optional RPM/throttle capture with real adapter polling profiles. Missing
  source readings leave those curves hidden; speed sampling priority is preserved.
  Do not imply auxiliary curves exist in speed-only legacy performance records.
- Finish review before merge/release. No signing credentials, release tags,
  production deployment or published APK are part of this change.

## Files created or modified

- `.gitignore`
- `android-app/app/src/androidTest/java/com/nowtuneup/app/presentation/DashboardUiTest.kt`
- `android-app/app/src/androidTest/java/com/nowtuneup/app/presentation/PerformanceChartsUiTest.kt`
- `android-app/app/src/androidTest/java/com/nowtuneup/app/presentation/TelemetryPersistenceTest.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/MainActivity.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/data/dashboard/DashboardCodec.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/data/dashboard/DashboardDefaults.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/data/obd/session/ObdSessionManager.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/domain/model/Models.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/domain/model/ShiftLightConfig.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/feature/live/LiveSessionRepository.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/feature/live/TelemetryBuffer.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/feature/live/TelemetryPanel.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/feature/timeslip/PerformanceHistory.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/feature/timeslip/TimeSlipAnalysis.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/feature/timeslip/TimeSlipCharts.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/feature/timeslip/TimeSlipRepository.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/feature/timeslip/TimeSlipScreen.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/presentation/dashboard/MainViewModel.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/ui/dashboard/DashboardScreen.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/ui/dashboard/components/DashboardWidgets.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/ui/dashboard/components/DrivingDashboardWidget.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/ui/dashboard/components/ShiftLightWidget.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/ui/dashboard/editor/DashboardEditor.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/ui/screens/NtuScreens.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/util/DisplayReadingAdapter.kt`
- `android-app/app/src/main/java/com/nowtuneup/app/util/UnitConverter.kt`
- `android-app/app/src/test/java/com/nowtuneup/app/data/dashboard/MotorsportDashboardTest.kt`
- `android-app/app/src/test/java/com/nowtuneup/app/feature/live/TelemetryBufferTest.kt`
- `android-app/app/src/test/java/com/nowtuneup/app/feature/timeslip/PerformanceHistoryTest.kt`
- `android-app/app/src/test/java/com/nowtuneup/app/util/DisplayUnitIntegrityTest.kt`
- `docs/development/motorsport-suite.md`

## Review branch and pull request

Implementation commits are pushed to
[`feat/motorsport-performance-suite`](https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/tree/feat/motorsport-performance-suite).
`main` is unchanged. Draft PR creation was attempted with `gh pr create --draft`
and failed with `Post "https://api.github.com/graphql": Forbidden`.
No PR was created. The prepared description is retained locally at
`/workspace/android-tools/draft-pr.md`; it can be submitted once API access works,
or used when opening a draft PR from the branch on GitHub.

## Direct adb fallback outcome

Both the debug application and instrumentation APK installed successfully via
`adb install -r -t`. Direct invocation of
`adb shell am instrument -w -r com.nowtuneup.app.debug.test/androidx.test.runner.AndroidJUnitRunner`
produced no application-test outcome. Emulator crash logs show Android system
processes (SystemUI, phone, Bluetooth and network stack) failing with
`DeadSystemException` before this app test attempt. The software-only emulator
was stopped after the unresponsive instrumentation attempt; no Compose tests are
claimed passed and no application UI screenshots were obtained. Use an
accelerated API 26+ Android device/emulator to complete these release gates.

The final readiness adjustment also rechecks supported/fresh speed after the
countdown before arming; countdown presentation cannot arm a stale run.

The direct runner discovered 9 application tests and began the first test, but no
individual test completed before the unstable emulator was stopped. Its adb
command exited 255; this is not counted as a passing test run.

New auxiliary files: `android-app/app/src/main/java/com/nowtuneup/app/feature/timeslip/AuxiliaryTimeSlipCharts.kt` and `android-app/app/src/test/java/com/nowtuneup/app/feature/timeslip/AuxiliaryTimeSlipTest.kt`.

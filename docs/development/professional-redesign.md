# Professional Automotive Technology redesign

This development branch redesigns the native Android interface. It does not tag,
publish, sign for production or change the published website/release metadata.
The supplied Dragy, social-navigation and activity-card images inform hierarchy,
spacing and readability only; no third-party branding or image assets are copied.

## Audit and migration plan

| Existing surface | Decision | Result |
| --- | --- | --- |
| OBD USB/Bluetooth transports, queue, PID parsing and sessions | KEEP | Existing collection and command behavior retained. |
| Dashboard and Custom HUD Studio | REDESIGN | Home workspace; manage screens separately from editing widgets. |
| Six primary navigation destinations | MERGE | Five stable destinations: Home, Performance, History, Garage, Settings. |
| Connection, live telemetry and diagnostics tabs | MERGE | Nested Home/Garage actions; connection remains reachable from app chrome. |
| Vehicle selection startup gate | REDESIGN | Garage manages vehicles; Home/Settings remain useful before vehicle creation. |
| Time Slip engine, source timestamps, estimates and comparison compatibility | KEEP | No measurement or interpolation algorithms replaced. |
| Time Slip history hidden inside Performance | REDESIGN | Dedicated History with vehicle, test, date and text filters. |
| Analog, mini, digital, ring and progress gauge variants | DEPRECATE | Exactly two primary presentations: DialGauge and DigitalGauge. |
| Charts, last-scan DTC and configured shift indicators | KEEP | Separate utility widgets; not additional gauge presentations. |
| Decorative gauge chrome, navigation zoom and panel press scaling | DEPRECATE | Flat borders, smaller radii, restrained crossfades, reduced-motion support. |
| Room profiles/trips/scans and DataStore settings | KEEP | No database schema change or destructive data migration. |
| DTC-clear UI affordance | DEPRECATE | Removed from read-only diagnostics UI; existing engine code unchanged. |
| Local text sharing | REDESIGN | Local PNG performance report plus text fallback through Android Sharesheet. |

### Current persistent data

- Room `NtuDatabase`, schema version 1: trips, samples, diagnostic scans and
  dashboard profiles. Dashboard row `name` is its stable storage key.
- DataStore `ntu_settings`: existing general/display/connection/alert preferences
  and serialized dashboard preferences are retained. New versioned keys
  `dashboard_workspace_order_v1` and `dashboard_workspace_landing_v1` store order
  and preferred landing independently of layout data.
- Time Slip `records_v1` and telemetry summary/raw-recording files are retained.
  Navigation and dashboard deletion never remove these records or vehicle profiles.

## Design system

Dark Graphite uses #0B0D10 background, #171A20 surfaces, high-contrast text and
#4F8EF7 as the restrained selection/data accent. Daylight uses white surfaces and
an accessible darker blue for text/actions. A 4 dp spacing grid and 4/8/10/12/16 dp
shape scale replace oversized corners. Numerical readings use monospace/tabular
alignment; other text uses Android's language-aware system typography.

Known old stock Graphite/Light green palettes map to the new app chrome at render
time. Custom palettes and serialized gauge-color fields are retained. The profile
background control changes the background; gauge chrome uses the shared readable
system. No new decorative glow, textures or navigation zoom are introduced.

Shared pieces: `NtuPanel`, `NtuScreenHeader`, `NtuSectionTitle`, `NtuMetricValue`,
`NtuSpacing`, the two gauges, connection-health panel and adaptive navigation.
Dark/light/compact/landscape/large-type previews are included in debug sources.
Alert text preserves configured colors when readable and otherwise selects an
accessible semantic color. Warning/critical states are also announced by
accessibility descriptions; utility-chart labels follow the effective theme.

## Navigation and state integrity

Primary routes have stable string keys independent of translated titles or list
order. Compact devices use a consistent five-item bottom bar; tablets and landscape
use a rail. Nested connection/diagnostics/live/vehicle screens preserve back actions.
The app handles system insets and cutouts through edge-to-edge + Material scaffolds.
The shell consumes its padding before nested screens so system bars are accounted
for once.
On API 26–28 the opaque system-navigation scrim follows the effective app palette,
so a dark app inside a light Android system theme retains visible navigation keys.
There were no existing URL deep links to remap; USB attach intent handling remains.

Active measurement/countdown disables primary navigation and connection/profile
switching. Reconnect auto-focus cannot eject an active Performance screen. The
AnimatedContent route uses its own target snapshot, avoiding duplicate Live Data
scopes whose disposal could silently remove active polling demand.

The Home shell remains after deleting the final dashboard. Startup waits for the
persisted landing decision and actual preferences before constructing the pager.
Preference writes are atomic DataStore updates; dashboard mutations read fresh
Room/DataStore snapshots under a mutex instead of stale reactive display state.
Editor closes only after successful persistence. Unreadable layouts go through
explicit reset/delete controls, so failed saves do not silently discard edits.

## Dashboard and gauge compatibility

- Existing dashboard and widget IDs/geometry survive import/export. UUIDs are used
  for new profiles, duplicates and widgets. Room's key repairs mismatching legacy
  JSON identities without creating duplicate profiles.
- Serialized legacy gauge enum/style/ring fields remain decode-only compatibility
  data. Analog/mini resolve to DialGauge; digital/ring/progress resolve to
  DigitalGauge. Obsolete renderer implementations were removed, while both new
  gauges use the same `TelemetryGaugeBinding` for values, units, range, thresholds,
  freshness and finite-data checks.
- Small widgets retain their configured label alongside the value. A DialGauge
  that cannot fit its dial uses the same compact labelled readout until resized;
  there is no additional persisted presentation type.
- Unsupported, disconnected, stale and missing values are unavailable, never
  silently zero. Dial animation changes only drawing; recorded values stay source
  observations. Real zero remains valid.
- Add/remove, drag, bounded resize, layer order, lock, duplicate, undo/redo,
  import/export and independent orientation layouts remain available. Destructive
  widget clear/delete and preset reset ask for confirmation.
- Daily, Engine and Performance templates are explicit copies. No defaults are
  inserted when Room is empty. New templates use their intended canvas in each orientation;
  existing saved user geometry is never repacked for appearance.
- Rename, duplicate, delete, reorder, preferred landing and confirmed template reset
  live in Manage dashboards. Deleting the last layout leaves Create Dashboard.
- Corrupt or future-schema JSON is retained in storage as an explicit recovered
  shell with a warning; it is not silently dropped or overwritten by normal Edit.

Visual review on the compact emulator also separated editor Save/Cancel from
undo/redo controls, preventing the title from breaking into a narrow column.
Home's workspace action replaces the redundant floating action that covered the
bottom of the canvas. Immersive connection status and page count occupy their own
row, preserving gauge labels for layouts that start at the top of the canvas.

## Performance, History and Garage

Performance retains existing acceleration/distance modes and the timing engine,
with clearer readiness, countdown cancellation, active-run cancellation, result
hierarchy and quality caveats. History reads existing records without enabling
performance polling. Compatible comparisons and all existing speed/estimated
acceleration/estimated distance and available RPM/throttle curves remain.

PNG reports use the existing FileProvider cache path, URI read grants and local
Android Sharesheet. A bounded report cache is used; bitmap/file work runs on IO.
Legacy missing elapsed/date/error estimates display unavailable, not invented zero
or a fake 1970 date. OBD-based reports explicitly disclaim GPS/Dragy-grade precision.

Garage groups active vehicle, adapter connection, read-only diagnostics, live
telemetry and measured health. Unknown health is not shown as 100% success; command
frequency is distinguished from per-PID sampling. Settings groups are Connection,
Display, Units, Alerts, Storage and About, retaining backed controls and exports.

## Validation record

Verification uses Java 17, the declared Android SDK/dependencies and the repository's
Gradle version. Release signing variables are unset for the local build.

```sh
gradle -p android-app test assembleDebug assembleRelease \
  assembleDebugAndroidTest --no-daemon --max-workers=2
gradle -p android-app lintDebug lintRelease --no-daemon --max-workers=2
adb -s emulator-5556 shell am instrument -w -r \
  com.nowtuneup.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

| Check | Result |
| --- | --- |
| Debug JVM suite | 133 passed; no failures, errors or skips |
| Release JVM suite | 133 passed; no failures, errors or skips |
| Debug and release lint | Both pass; zero errors, 42 warnings each |
| Debug app and instrumentation APKs | Assembled |
| Local release APK | Assembled unsigned; no production signing or publication |
| Compose/Room/report instrumentation | All 21 cases passed across the full run and focused retest; no skips |

Lint warnings concern dependency/tooling updates, compatibility/style advice and
unused resource/icon suggestions. No lint checks or test assertions were disabled.
Previews live in debug sources so release code does not depend on preview tooling.

Compilation and lint run sequentially because a combined incremental invocation
exposed a KAPT/lint race: lint read generated Java test stubs while KAPT replaced
them. Android UI CI now finishes compilation before invoking lint, retaining all
checks. Its artifact name no longer contains an obsolete app version.

Tests cover lossless legacy mapping; missing, stale, unsupported and real-zero
values; compact gauge labels; widget removal, resize and layers; corrupt-layout
recovery; dashboard order/landing; Room close/reopen CRUD and final deletion with
trip preservation; honest connection health; legacy report presentation; local PNG
sharing; compact navigation; light/dark themes; larger type; and reduced motion.
Editor tests exercise cancellation/confirmation for deletion and canvas clear,
orientation isolation and undo/redo before reopening Room. Regression cases also
cover connection loss during an active Time Slip run and retaining customized
legacy theme colors.

The final full device run passed 20 of 21 cases. Widget deletion hit a Compose
idle timeout on the software emulator; its unchanged focused rerun passed (113.035 s).
The new fullscreen test verifies that connection status and the first gauge label
do not overlap. The Garage test scrolls lazy content into composition before
asserting its visibility. No assertions were removed to obtain these results.

## Previews and device gates

`ProfessionalDesignPreviews.kt`, `DesignPreviews.kt`, Garage previews and Performance
previews are available in Android Studio. Debug example records are preview/test
fixtures; release screens never seed vehicle readings or history.

The baseline was captured from the existing signed v1.18.0 APK. Actual app
captures use only reviewer-created layouts with no vehicle, adapter, telemetry or
run records injected. Chart captures are explicitly named test fixtures.

| View | Capture |
| --- | --- |
| Published first-run vehicle gate | [Before](redesign-screenshots/before-first-run.png) |
| Home after deleting the final dashboard and restarting | [Empty Home](redesign-screenshots/after-home-empty.png) |
| Daily dashboard, dark | [Dark](redesign-screenshots/after-dashboard-dark.png) |
| Daily dashboard, light | [Light](redesign-screenshots/after-dashboard-light.png) |
| Adaptive landscape navigation | [Landscape](redesign-screenshots/after-dashboard-landscape.png) |
| Shared dashboard in fullscreen, status clear of labels | [HUD](redesign-screenshots/after-hud-landscape.png) |
| Compact editor with separate undo/redo controls | [Editor](redesign-screenshots/editor-compact.png) |
| Performance comparison — test fixture | [Dark chart](redesign-screenshots/performance-dark-test-fixture.png) |
| Performance with larger type — test fixture | [Light chart](redesign-screenshots/performance-light-large-text-test-fixture.png) |

Manual app checks exercised create, rename, duplicate, reorder, preferred landing,
restart, confirmed template reset, both themes, rotation and fullscreen exit.
The final reviewer layout was deleted through the UI. Room contained one dashboard
before deletion, zero after deletion and zero after process restart; Home retained
its Create Dashboard action. Other main Room table counts were unchanged. The
isolated automated persistence test additionally verifies that a stored trip
survives dashboard deletion. [Deletion evidence](redesign-screenshots/deletion-evidence.json).

Physical USB/Bluetooth behavior, extended monitoring, memory/recomposition
profiling, interrupted sessions on real hardware and OEM font/cutout layouts remain
release gates. Device tests ran on API 26 AOSP x86_64 at 640×1136 pixels/density 320
(320 dp width), using software emulation because KVM is unavailable. System UI
ANR dialogs appeared during manual startup and were dismissed with Wait. Some
ActivityManager startup waits timed out before the UI subsequently rendered. These
functional checks do not establish real-device frame-rate or startup performance.

## Implementation map

Paths below are relative to `android-app/app/src` unless stated otherwise.

| Area | Main files |
| --- | --- |
| App shell and primary routes | `main/java/com/nowtuneup/app/MainActivity.kt`, `ui/navigation/AppNavigation.kt` |
| Shared visuals and motion | `presentation/theme/Theme.kt`, `ui/components/NtuComponents.kt`, `NtuDesignSystem.kt`, `ui/motion/PremiumMotion.kt` |
| Dashboard workspace and persistence | `data/dashboard/DashboardRepository.kt`, `data/preferences/SettingsRepository.kt`, `presentation/dashboard/MainViewModel.kt`, `ui/dashboard/WorkspaceManagerScreen.kt` |
| Layout compatibility and widget editing | `data/dashboard/DashboardCodec.kt`, `DashboardDefaults.kt`, `DashboardWidgetOperations.kt`, `domain/model/Models.kt`, `TelemetryGaugeBinding.kt`, `ui/dashboard/editor/DashboardEditor.kt` |
| Dashboard rendering | `ui/dashboard/DashboardScreen.kt`, `ui/dashboard/components/DashboardWidgets.kt`, `DrivingDashboardWidget.kt`, `util/Contrast.kt` |
| Performance and History | `feature/timeslip/TimeSlipScreen.kt`, `PerformanceHistoryScreen.kt`, `TimeSlipShare.kt` |
| Garage, connection and settings | `ui/vehicle/GarageScreen.kt`, `VehicleProfilesScreen.kt`, `ui/connection/ConnectionScreen.kt`, `ConnectionHealthPanel.kt`, `ui/screens/NtuScreens.kt` |
| Preview coverage | `debug/java/com/nowtuneup/app/ProfessionalDesignPreviews.kt`, `feature/timeslip/PerformancePreviews.kt`, `ui/vehicle/GaragePreviews.kt` |
| JVM coverage | `test/.../DashboardWorkspaceTest.kt`, `PrimaryGaugeMigrationTest.kt`, `HistoryPresentationTest.kt`, `ConnectionHealthFactsTest.kt` |
| Device coverage | `androidTest/.../DashboardWorkspacePersistenceTest.kt`, `DashboardEditorDeletionUiTest.kt`, `NavigationUiTest.kt`, `GarageUiTest.kt`, `PerformancePresentationUiTest.kt`, `PerformanceReportShareTest.kt` |
| UI CI ordering | Repository-root `.github/workflows/android-ui-verification.yml` |

The implementation uses the existing Compose, Room, DataStore and FileProvider
dependencies. No new backend, social feature, account requirement, diagnostic
command or dependency was introduced. The database version and published APK
version are unchanged on this development branch.

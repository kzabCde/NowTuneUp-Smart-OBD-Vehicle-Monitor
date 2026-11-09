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

# Dashboard deletion / gauge density follow-up

This is a targeted refinement of the 1.19.1 redesign, not another app-wide rebuild.

## Observed source issues

- Home's **Manage dashboards** action was absent while Driving Mode was enabled. The manager itself intentionally blocks editing in Driving Mode but can now be opened to explain why controls are locked.
- Profile deletion existed only in a nested overflow menu. The direct **ลบ** action is now visible on each profile row in the workspace manager.
- The old delete dialog closed before the asynchronous repository write returned, even if persistence failed. The dialog now stays open and shows failure feedback when the ViewModel reports a failure.
- Dial gauge had 21 scale marks and a min/max-text chrome layer. It now uses five quiet index marks and a slimmer needle, retaining the original range mapping.
- Digital gauge now uses a left-aligned label, restrained tabular value and adjacent unit rather than a three-row centered block.

## Do not regress

- Home -> Manage dashboards -> **ลบ** -> Confirm must remove the selected profile.
- After application force-stop/restart, the deleted profile must remain absent.
- The final dashboard is deletable; empty Home presents a Create Dashboard action.
- Trips, vehicle profiles, saved readings, DTC scans and performance runs remain intact.
- In Driving Mode, the manager shows the safety lock explanation; mutation remains disabled. Disable Driving Mode while safely parked before editing.
- PID measurements, source quality, units, warning thresholds and Time Slip calculations are unchanged.
- Compact 320dp devices, landscape, larger text, light/dark themes, disconnected and stale values remain readable.

## Verification still required

1. Compile and run Android unit and UI tests in CI.
2. On a physical Android device, create two profiles, delete one using the directly visible action, kill and relaunch the app, and verify the other remains.
3. Repeat after deleting the last profile and confirm empty Home appears.
4. Enable Driving Mode and verify a clear disabled state (not a silent action).
5. Compare 320dp portrait, landscape, large-text and both themes; inspect both gauges at minimum and large widget sizes.
6. Test on a device with an ELM327 adapter, without changing the OBD read-only pipeline.

No APK publication or production release should occur until these checks pass.

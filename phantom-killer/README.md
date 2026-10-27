# Phantom Killer

A small Android app that applies the three common Phantom Process Killer settings through **Shizuku**, then checks the live values read back from Android.

## Features

- **Apply Phantom Killer** executes the three settings commands in sequence through Shizuku.
- After applying, the app automatically reads the current settings back.
- **Check Status** runs a fresh check on demand; it does not rely on button state or a simulated result.
- Each setting reports **Verified**, **Partial**, **Not active**, or **Read error**.
- Shizuku must be running and permission must be granted before any shell command can execute.

## Commands applied

```sh
/system/bin/device_config set_sync_disabled_for_tests persistent
/system/bin/device_config put activity_manager max_phantom_processes 2147483647
/system/bin/settings put global settings_enable_monitor_phantom_procs false
```

## Verification performed

1. Reads `device_config get_sync_disabled_for_tests`. On Android versions exposing the newer API, `persistent` is required; `until_reboot` is reported as partial. For older Android 12 builds, it falls back to the legacy boolean command and the stored mode.
2. Reads `device_config get activity_manager max_phantom_processes` and expects `2147483647`.
3. Reads `settings get global settings_enable_monitor_phantom_procs` and expects `false`.

The app marks the whole fix active only when all three expected values are verified. This confirms the current settings values, not that every OEM's process-killing behavior is guaranteed to change identically.

## Important safety note

The persistent DeviceConfig sync setting survives reboots and can prevent Android from applying some bulk configuration updates used by recovery mechanisms. Keep that trade-off in mind, especially before OS upgrades or if the device begins crashing. Use this change at your own risk.

The effect of the third setting varies by Android version and manufacturer. The underlying commands are based on the published [Phantom Process Killer instructions](https://github.com/atamshkai/Phantom-Process-Killer) and the [Android process-limit notes](https://github.com/agnostic-apollo/Android-Docs/blob/master/en/docs/apps/processes/phantom-cached-and-empty-processes.md).

## Requirements

- Android 8.0 or newer
- Shizuku 11 or newer, running
- Phantom Killer permission granted in Shizuku

On Android 11+, Shizuku can be started using Wireless debugging without root. Shizuku needs to be started again after a reboot when using the non-root Wireless debugging method. See the [official Shizuku guide](https://shizuku.rikka.app/).

## Build APK using GitHub Actions

The workflow `.github/workflows/phantom-killer.yml` uses Java 17, Android platform 35, and Gradle 8.11.1 to build a debug APK from the `phantom-killer/` directory whenever project files on the `phantom-killer-app` branch change. It uploads the file as the `phantom-killer-debug-apk` artifact. Download the artifact ZIP from the latest successful run in the repository's **Actions** tab, then extract and install `app-debug.apk`.

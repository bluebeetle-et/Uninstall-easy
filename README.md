# Uninstall Easy

A lightweight Android utility for reviewing installed apps and uninstalling them without hunting through system settings.

## MVP features

- Shows user-installed apps with icon, package name, and first install date
- Sort by newest installed, oldest installed, most used, least used, recently used, or name
- Search by app name or package name
- Shows foreground usage from the last 7 days when Usage Access is granted
- One-tap **Uninstall** button that opens Android's normal uninstall confirmation
- Local-only: no account, analytics, backend, ads, or data upload

## Android permissions

- `PACKAGE_USAGE_STATS` for usage sorting. The user grants this manually from Android Settings.
- `QUERY_ALL_PACKAGES` so the utility can enumerate installed apps.
- `REQUEST_DELETE_PACKAGES` so it can request an uninstall through Android's system UI.

## Build

The project uses Android Gradle Plugin 9.4.0, compile/target SDK 37, and min SDK 23. Open the repository in a current Android Studio version and run the `app` configuration on a device or emulator with API 23+.

This repository currently does not include the binary Gradle wrapper JAR, so use Android Studio's configured Gradle installation or generate a wrapper locally with Gradle 9.6 before command-line builds.

## Notes

Android exposes `firstInstallTime`, not the original app-store download timestamp. The UI therefore labels this value as the install date.

Usage sorting remains available without Usage Access, but all apps will show zero usage until access is granted.

`QUERY_ALL_PACKAGES` is subject to Google Play policy. This MVP is intended primarily for personal/sideloaded use while distribution requirements are evaluated.

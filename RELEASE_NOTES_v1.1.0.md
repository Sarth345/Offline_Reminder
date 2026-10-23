# Planerize 1.1.0 Release Notes

**Planerize: Private, Offline-First Calendar, Reminders & Alarms**

Version: `1.1.0`
Version Code: `2`
Application ID: `com.example.offlinereminder`
Branch: `feature/planerize-1.1`
Target: `main`

---

## Overview

Planerize 1.1.0 is a major feature, usability, and reliability release. This version brings biometric security, deep productivity analytics, flexible data portability, custom audio personalization, refined responsive UI layouts, and uncompromising offline notification and alarm delivery.

Planerize remains strictly 100% offline-first. There are zero cloud dependencies, zero network permissions, zero tracking, and zero account requirements. All data stays entirely on your physical device.

---

## Key Highlights & New Features

### 1. App Lock & Biometric Protection
- Protect your personal calendar and agenda with biometric authentication (fingerprint, face unlock) or system credentials.
- Configurable lock timeouts: Immediate, 1 minute, 5 minutes, 15 minutes, or 30 minutes.
- Immediate UI obfuscation when backgrounded or locked to prevent notification snapshot leaks.

### 2. Built-in Audio Personalization & Alarm Tunes
- **Dedicated Notification Tones**: Choose from crisp built-in notification alerts including *Planerize Bell*, *Gentle Chime*, *Soft Bell*, *Digital Alert*, *Short Beep*, or *Rising Alert*.
- **Dedicated Alarm Tunes**: Choose persistent alarm sounds including *Classic Alarm*, *Planerize Alarm*, *Digital Alarm*, or *Strong Alarm*.
- **Custom Sound Selection**: Pick custom audio files from local device storage for notifications and alarms.
- Dedicated sound management interface under **Settings → Notifications & Alarms**.

### 3. Configurable Snooze & Alarm Controls
- Choose default snooze intervals (5m, 10m, 15m, 30m) for alarms and notifications.
- Interactive notification shade actions with one-tap **Complete** and **Snooze**.
- Persistent heads-up notification and full-screen alarm experience that cuts through Doze mode via exact RTC alarm scheduling.

### 4. Advanced Offline Quick Add
- Fast text parsing for rapid reminder entry:
  - Time & Date: `today 5pm`, `tomorrow 9am`, `friday 2pm`, `nov 15 10am`.
  - Recurrence tags: `rep:daily`, `rep:weekly`, `rep:monthly`, `rep:yearly`.
  - Reminders: `rem:0m` (at start), `rem:15m`, `rem:1h`, `rem:1d`.
  - Priority: `p:high`, `p:medium`, `p:low`.
  - Categories: `#work`, `#personal`, `#health`, etc.

### 5. Statistics & Local Productivity Insights
- Comprehensive local productivity analytics dashboard:
  - Completion rates, weekly trends, category breakdown, overdue metrics.
  - Heatmaps and streak tracking calculated directly from local SQLite database.

### 6. Full Data Portability & Standards Support
- **Standalone Export**: Export reminders and events as **JSON**, **CSV**, or standard **iCalendar (.ics)**.
- **ICS Import/Export**: Seamlessly import calendar feeds from `.ics` files and export events to standard calendar applications completely offline.
- **Full Backup & Restore**: Encrypted local backup archive generation and restoration preserving all recurring rules, tags, attachments, and settings.

### 7. Recurring-Series Editing & Event Management
- Granular recurrence handling: edit "This occurrence only", "This and following occurrences", or "All occurrences".
- Archive and trash lifecycle management with 30-day auto-purge safety or instant recovery.
- Attachment support: link local documents and images directly to events.
- Saved locations: attach offline physical locations and coordinates without relying on internet geocoding.

### 8. Responsive UI/UX & Startup Branding
- Refined responsive layouts for compact 720x1600 mobile screens and larger displays.
- Unified event state and badge consistency across Calendar, Today's Dashboard, All Reminders, Upcoming, and Event Detail screens.
- Polished branded startup screen experience.

---

## Reliability & Verification

Planerize 1.1.0 has undergone an exhaustive physical-device verification process on real hardware (OnePlus Nord CE4 / Android 16):

- **Physical-Device Acceptance Tests**: `161 / 161 PASS` (100% pass rate across UI, alarms, notifications, reboot survival, biometric auth, and Doze recovery).
- **Unit Test Suite**: `577 / 577 PASS` (0 failures, 0 errors).
- **Connected Instrumented Tests**: `5 / 5 PASS` on physical Android 16 hardware.
- **System Reboot Recovery**: Verified that `AlarmManager` schedules, notification instances, Room database state, and custom audio channel bindings cleanly survive cold physical reboots.
- **Production Build Artifacts**: Both release APK and release Android App Bundle (AAB) successfully built and validated.

---

## Privacy & Security Commitment

- **Zero Network Access**: The `android.permission.INTERNET` permission is completely absent from the application manifest. The app physically cannot send or receive data over the network.
- **Zero Third-Party SDKs**: No Google Analytics, no Firebase, no advertising libraries, no tracking frameworks.
- **Local Biometric Security**: Biometric prompts are mediated by Android's secure system `BiometricPrompt` framework; biometric data never leaves hardware trust zones.
- **Local Storage**: All calendar events, reminders, notes, and preferences reside exclusively in local SQLite (Room) and encrypted DataStore files on device.

---

## Build Artifacts & Checksums

### Release APK
- **File**: `app-release-unsigned.apk`
- **Size**: `15,662,553 bytes` (14.93 MB)
- **SHA-256**: `3501f87c9b7d48b5dd42068b245317f77431ac61b98af2db68af3d9b99b98b6e`

### Release Android App Bundle (AAB)
- **File**: `app-release.aab`
- **Size**: `14,739,991 bytes` (14.05 MB)
- **SHA-256**: `34fe1642d158360219f9564b9b8432ffa1a10910dee91f14fcb6f15f0cb578d3`

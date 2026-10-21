# Planerize (v1.1.0)

> **Plan Your Time. Organize Your Life.**

Planerize is a high-performance, 100% offline reminder and event calendar application for Android. Designed around uncompromising privacy, deterministic local execution, and Clean Architecture, Planerize keeps your schedule completely private and operational without requiring an internet connection or external cloud services.

---

## Key Features

- **100% Offline & Private:** Zero network permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, etc. are omitted). All schedule data, attachments, and preferences remain strictly on your device.
- **Biometric App Lock:** Complete application privacy shield utilizing on-device biometrics and device credentials with customizable inactivity and background timeouts.
- **Local Statistics & Productivity Insights:** Deterministic, privacy-first analytics providing schedule distribution, category/priority breakdowns, completion rates, and actionable productivity insights.
- **RFC 5545 iCalendar (.ics) Import & Export:** Full calendar interoperability with Google Calendar, Apple Calendar, Outlook, and Thunderbird—featuring import preview validation and line-folded export.
- **Standalone Data Export:** Export reminders and calendar entries directly to iCalendar (.ics), standard CSV spreadsheets, or canonical JSON formats.
- **Saved Locations:** Manage frequently used locations with addresses, building/room details, notes, and favorite pins, integrated into event creation and Quick Add.
- **Granular Recurring Series Editing:** Three RFC-compliant editing modes: "This occurrence only", "This and all following" (series split), or "Entire series".
- **Advanced Offline Quick Add:** Enhanced natural language processing supporting multi-day recurring schedules, interval recurrence, time ranges, natural reminders, and saved locations without cloud AI.
- **Smart Dashboard & Horizon Agenda:** Intelligent Today dashboard surfacing upcoming, overdue, pinned, and completed occurrences alongside a responsive chronological timeline.
- **Robust Recurrence Engine:** Full support for complex recurrence rules (Daily, Weekly, Monthly, Yearly), leap-year handling, month-end adjustments, and Daylight Saving Time (DST) preservation.
- **Reliable Alarms & Notifications:** Scheduled via Android `SCHEDULE_EXACT_ALARM` with intelligent inexact fallbacks, fullscreen alarm alerts, and persistent reboot recovery.
- **Attachments & Local Media:** Private scoped-storage attachment management supporting documents, images, and custom reminder audio.
- **Checklists & Sub-events:** Interactive checklists embedded within events, tracked reactively across agenda cards and dashboard views.
- **Dependencies & Relationships:** Finish-to-Start (FS), Start-to-Start (SS), Finish-to-Finish (FF), and Start-to-Finish (SF) links with cycle detection.
- **Taxonomy & Organization:** Customizable Categories, Event Types, Event Groups, and Tags.
- **Local Backup & Restore:** Atomic, versioned ZIP archive export and restore with SHA-256 integrity validation and multi-strategy conflict resolution.

---

## Technical Architecture

Planerize is built strictly following Clean Architecture and Unidirectional Data Flow (UDF):

- **Domain Layer:** 100% pure Kotlin standard library (`java.time.*`, `kotlinx.coroutines.*`, `kotlinx.serialization.*`). Zero Android, Room, or Lifecycle dependencies.
- **Data Layer:** Room SQLite with relational integrity, type converters, scoped storage file handlers, and platform alarm receivers.
- **Presentation Layer:** Jetpack Compose, Material 3 design system, StateFlow-backed ViewModels, and navigation graphs.

---

## Building and Verification

### Prerequisites
- JDK 17+
- Android SDK (API 35 compileSdk, minSdk 26)

### Run Unit Tests
```bash
./gradlew test --continue
```

### Build APKs
```bash
# Debug APK
./gradlew assembleDebug

# Release APK (Unsigned)
./gradlew assembleRelease
```

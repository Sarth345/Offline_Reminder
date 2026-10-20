# Offline Reminder & Event Calendar App — Product & Architecture Specification

**Project status:** Design phase — DO NOT START IMPLEMENTATION UNTIL THE USER EXPLICITLY SAYS "START DEVELOPMENT"

## 1. Project Goal

Build a completely offline-first Android mobile application for reminders, dates, events, recurring events, countdowns, and notifications.

The application must work without:
- Internet access
- User accounts
- Cloud services
- Backend servers
- Online APIs
- Analytics/telemetry
- Advertisements
- Cloud synchronization

GitHub is used only for source-code version control during development.

The architecture must keep the UI layer independent from business logic and persistence so the UI/design can be replaced later without rewriting the core application.

---

# 2. Development Stack

Recommended baseline:

- Android Studio
- Kotlin
- Jetpack Compose for UI
- Android Jetpack architecture components
- Local database for persistent application data
- Android local notification/alarm mechanisms
- Git + GitHub for version control
- Google Antigravity for agent-assisted development

Do not introduce a backend.

Do not introduce a network dependency into the application.

---

# 3. Core Architecture Requirement

Use strict separation of concerns.

Preferred conceptual architecture:

    UI / Presentation
            |
            v
    ViewModels / UI State
            |
            v
    Domain / Business Logic
            |
            v
    Repository Interfaces
            |
            v
    Local Data Layer
            |
            +--> Local Database
            |
            +--> Local Backup/Restore
            |
            +--> Notification Scheduler
            |
            +--> Android System APIs

The UI must NOT directly access the database.

The UI must NOT contain reminder/business rules.

The UI must NOT schedule alarms directly.

The UI must communicate through ViewModels/use cases/interfaces.

The data layer must not depend on UI classes.

The domain layer must not depend on Compose.

This separation is mandatory because the UI must be replaceable later.

---

# 4. UI Replaceability Requirement

The UI must be treated as a replaceable presentation layer.

Example:

    Domain/Data
         |
         v
    ViewModel / UI State
         |
         v
    UI Contract
         |
         +--> Current Compose UI
         |
         +--> Future redesigned UI
         |
         +--> Alternative UI implementation

Do not allow business logic to leak into Composable functions.

Use immutable UI state and event-driven/unidirectional data flow.

The UI receives state and sends user events upward.

Changing colors, layouts, navigation, screens, animations, components, or the entire visual design should not require rewriting the database or reminder engine.

---

# 5. Main Navigation

The primary application experience is calendar-centric.

Suggested navigation:

    Calendar
    Upcoming
    Add
    Completed
    Settings

Possible future additions:

    Search
    Categories
    Statistics
    Archive

The exact visual design is intentionally NOT locked.

---

# 6. Calendar

The application must provide a calendar.

Required:

- Month view
- Week view if practical
- Day/event details
- Date selection
- Event indicators
- Tap a date to view events
- Navigate between months
- Jump to today
- Upcoming event display
- Date-specific event list

Events must be represented independently from their visual calendar representation.

---


# 6A. Event Groups

Users must be able to group multiple events together into named, persistent groups and view those groups at any time.

## Group capabilities

- Create custom event groups.
- Rename and edit groups.
- Optionally assign a group icon, color, and description.
- Add existing events to a group.
- Remove events from a group without deleting the events themselves.
- Delete a group without deleting its events.
- View all groups from a dedicated Groups section.
- Open a group to view all events belonging to it.
- Search groups by name.
- Search and filter events within a group.
- Sort group events by date, priority, title, completion status, or creation date.
- Show active, completed, skipped, and overdue events within a group.
- A single event may belong to multiple groups.
- Groups must work with recurring events and their occurrences.
- Groups must persist locally and work completely offline.
- Groups must be included in backup and restore.

### Group data model requirement

Groups must be first-class domain entities and must not duplicate event data. An event remains the single source of truth; group membership represents a relationship between an event and one or more groups.

Conceptually:

    Event <---- membership ----> Event Group

Deleting a group must never implicitly delete its events. Removing an event from a group must never delete the event.

### Navigation

The primary navigation should include:

    Calendar
    Upcoming
    Groups
    Add
    Completed
    Settings

The exact visual presentation remains replaceable and is not locked to a specific UI design.

### Example

A user may create groups such as:

    College
        - DBMS Exam
        - DSA Assignment
        - C++ Viva
        - Project Submission

    Goa Trip
        - Book Train
        - Hotel Check-in
        - Pack Bags
        - Return Journey

    Monthly Payments
        - Rent
        - EMI
        - Electricity Bill
        - Internet Bill

An event may belong to multiple groups, for example:

    DBMS Exam
        - College
        - Exams
        - Important

Groups are persistent collections, not temporary UI filters.

---

# 7. Reminder/Event Types

The application should ship with many predefined event types.

Initial categories/templates:

- General reminder
- Birthday
- Anniversary
- Exam
- Assignment
- Deadline
- Work
- Meeting
- Appointment
- Doctor appointment
- Medication
- Bill
- Payment
- Subscription renewal
- Rent
- EMI
- Travel
- Flight
- Train
- Bus
- Vacation
- Holiday
- Festival
- Party
- Wedding
- Call someone
- Follow-up
- Shopping
- Task
- Project milestone
- Application deadline
- Document renewal
- License renewal
- Maintenance
- Vehicle service
- Insurance renewal
- Personal
- Custom

The system must support adding more event types later without changing the core reminder engine.

---

# 8. Event Type Search

Because there may be many event types, the Add Event screen must include event-type search.

Example:

    Add Event
        |
        +--> Search event type
        |
        +--> "birthday"
        |
        +--> Birthday
        |
        +--> Create

Requirements:

- Fast local search
- Search by event type name
- Search by aliases/keywords
- Category filtering
- Recently used types
- Favorites/pinned types
- Custom event types
- No network search

The event-type catalog must be stored locally and be extensible.

---

# 9. Generic Reminder/Event Model

A custom/general event must support:

- Unique ID
- Title
- Description/notes
- Event type
- Category
- Icon
- Color
- Priority
- Start date
- Start time
- Optional end date
- Optional end time
- Location text
- Recurrence
- Notification schedule
- Completion state
- Skip state
- Created timestamp
- Updated timestamp
- Optional attachment references
- Optional checklist/tasks
- Optional custom metadata

Do not hard-code the database around only birthdays or only calendar events.

The generic event model is the foundation.

---

# 10. Date Modes

Events must support:

### Date only

Example:
    Mom's Birthday — 25 October

### Date + time

Example:
    Doctor — 25 October, 10:30 AM

### Date range

Example:
    Vacation — 25–30 October

### Date range + time

Support when appropriate.

The date/time model must handle time zones correctly at the Android system level while remaining completely local.

---

# 11. Countdown

The application should display countdown information where useful.

Examples:

    Birthday
    42 days remaining

    Exam
    3 days, 7 hours remaining

After an event:

    Passed 2 days ago

For recurring events:

    Next occurrence: 25 October 2027

Countdown calculations belong to the domain/presentation logic, not directly inside UI components.

---

# 12. Recurrence Engine

Support:

- Once
- Daily
- Weekly
- Monthly
- Yearly
- Weekdays
- Weekends
- Every X days
- Every X weeks
- Every X months
- Every X years
- Custom recurrence

Recurring events must have a clear distinction between:

- Event definition
- Individual occurrence
- Completion state
- Notification instances

Do not duplicate an infinite number of future events into the database.

Generate or materialize occurrences intelligently.

---

# 13. Multiple Notifications

Each event can have unlimited notification/reminder offsets.

Example:

    Project Submission
        30 days before
        14 days before
        7 days before
        1 day before
        2 hours before
        At event time

Notification schedules belong to the event data model and must be managed by a dedicated notification scheduling component.

---

# 14. Snooze

Notifications must support:

- 5 minutes
- 10 minutes
- 15 minutes
- 30 minutes
- 1 hour
- 2 hours
- Tomorrow
- Custom

All options should be available.

Snoozing must create/update a local scheduled notification without changing the underlying event definition unless explicitly intended.

---

# 15. Notification Actions

Notifications should support:

- Open/View
- Dismiss
- Snooze
- Mark Complete
- Skip
- Reschedule

Exact Android notification capabilities should be verified during implementation against the target Android API level.

---

# 16. Event Lifecycle

Every event should support:

    Active
       |
       +--> Completed
       |
       +--> Skipped
       |
       +--> Rescheduled
       |
       +--> Deleted

User actions:

- Complete
- Skip
- Reschedule
- Delete

Completed events should remain available in history unless the user explicitly deletes them.

For recurring events, completing one occurrence must not accidentally delete the entire recurring series.

The system should distinguish:

- Complete this occurrence
- Complete the series
- Skip this occurrence
- Skip future occurrences
- Reschedule this occurrence
- Edit the entire series

---

# 17. Search

Global local search is required.

Search should cover:

- Event title
- Description
- Event type
- Category
- Location
- Notes
- Relevant metadata

Search must work entirely offline.

Possible filters:

- Date
- Event type
- Category
- Priority
- Completed
- Active
- Overdue
- Upcoming
- Groups

---

# 18. Categories

Default categories may include:

- Personal
- Work
- Education
- Finance
- Health
- Travel
- Family
- Social
- Shopping
- Maintenance
- Other

Users must be able to create custom categories.

Categories should be data-driven rather than hard-coded into business logic.

---

# 19. Priority

Support:

- Low
- Normal
- High
- Critical

Priority should affect presentation and potentially notification behavior, but must not bypass Android notification/security restrictions.

---

# 20. Attachments and Notes

Design the event model so future support can include:

- Notes
- Checklist
- Local images
- Local documents

Attachments must remain local.

Do not build cloud storage into the architecture.

---

# 21. Local Persistence

All application data must survive:

- App restart
- Device restart
- App update
- Notification process restart
- Normal Android lifecycle changes

The app must be designed to recover/re-register scheduled notifications after a device reboot or other relevant system event.

Use a proper local persistence layer rather than raw scattered file storage.

---

# 22. Backup and Restore

Required.

Users must be able to:

    Settings
       |
       +--> Export Backup
       |
       +--> Import Backup

Backup must contain enough information to reconstruct:

- Events
- Event groups and group memberships
- Event types
- Categories
- Notification schedules
- Recurrence definitions
- User preferences
- Relevant local metadata

Recommended format:

    Versioned backup format

Example concept:

    backup_version
    app_version
    exported_at
    events
    event_types
    categories
    settings

The backup format must be versioned so future application versions can migrate older backups.

Do not assume the internal database schema is the public backup format.

---

# 23. Offline Security

The application must not require internet permission unless an unavoidable Android system dependency requires it.

Do not include:

- Firebase
- Google Analytics
- Crash-reporting network services
- Online advertisements
- Cloud database
- Online authentication
- Remote configuration
- Online event catalogs

If any dependency introduces network functionality, review and reject it unless explicitly approved.

---

# 24. Settings

Potential settings:

- Theme
- Language
- Date format
- Time format
- First day of week
- Default event duration
- Default notification behavior
- Default snooze duration
- Notification sound
- Vibration
- Calendar preferences
- Backup/restore
- Categories
- Event types
- Permissions/status
- About
- Data management

---

# 25. Theme and UI

Initial implementation should support:

- Light
- Dark
- System default

Use a centralized design system/theme.

Do not scatter colors, typography, dimensions, icons, or component styling across business logic.

A future UI redesign should be able to replace the visual layer without changing domain/data behavior.

---

# 26. Architecture Package/Module Direction

Suggested conceptual structure:

    app/
      presentation/
        navigation/
        screens/
        components/
        theme/
        state/

      domain/
        model/
        usecase/
        recurrence/
        countdown/
        search/

      data/
        local/
        repository/
        backup/

      notification/
        scheduler/
        receiver/
        notification/

      core/
        time/
        result/
        utilities/
        constants/

      tests/

Exact package names may be adjusted during implementation, but the dependency direction must remain:

    presentation -> domain
    presentation -> interfaces
    data -> domain/interfaces
    notification -> domain/interfaces
    domain -> no UI dependency

Avoid circular dependencies.

---

# 27. UI Contract

The UI should consume presentation models such as:

    CalendarUiState
    EventListUiState
    EventEditorUiState
    SearchUiState
    SettingsUiState

And emit events such as:

    OnDateSelected
    OnEventSelected
    OnCreateEvent
    OnEditEvent
    OnDeleteEvent
    OnCompleteEvent
    OnSkipEvent
    OnRescheduleEvent
    OnSearchQueryChanged
    OnFilterChanged

The UI should not know how the database works.

---

# 28. Testing Requirements

Before each major feature is considered complete:

- Unit tests for domain logic
- Recurrence tests
- Countdown/date calculation tests
- Search tests
- Event group and membership tests
- Repository tests
- Backup/restore tests
- Notification scheduling tests
- UI tests for critical flows

Important edge cases:

- Leap years
- Month-end recurrence
- February 29
- Yearly events
- Daylight-saving/time-zone transitions where relevant
- Device reboot
- App restart
- Missed notification
- Recurring event completion
- Importing older backup versions
- Duplicate imports
- Deleted events with pending notifications

---

# 29. GitHub Strategy

Use Git for all source control.

Recommended branch strategy:

    main
      |
      +-- develop
             |
             +-- feature/project-foundation
             +-- feature/event-model
             +-- feature/calendar
             +-- feature/notifications
             +-- feature/recurrence
             +-- feature/search
             +-- feature/backup
             +-- feature/settings
             +-- feature/ui

Keep commits small and meaningful.

Examples:

    chore: initialize Android project
    feat: add event domain model
    feat: add local event persistence
    feat: add calendar presentation
    feat: add notification scheduling
    test: add recurrence edge cases
    fix: restore alarms after reboot

Never commit:

- API keys
- passwords
- signing secrets
- machine-specific files
- unnecessary build outputs
- local IDE caches

Use an appropriate Android/Kotlin .gitignore.

---

# 30. Antigravity Rules

Antigravity is an engineering assistant, not the product owner.

Before modifying code:

1. Read this specification.
2. Inspect the existing repository.
3. Explain the intended changes.
4. Identify affected modules.
5. Identify risks.
6. Ask for clarification if a requirement is ambiguous.
7. Implement only the approved scope.
8. Run relevant tests.
9. Show the changes/diff.
10. Do not silently redesign architecture.

Prefer small, verifiable tasks.

Do not allow the agent to rewrite the entire project when fixing a small issue.

Keep terminal command execution under review during early development.

---

# 31. Development Phase Order

Do not implement everything at once.

Recommended phases:

## Phase 0 — Environment

- Android Studio
- JDK/Android SDK
- Git
- GitHub
- Antigravity
- Repository setup

## Phase 1 — Project Foundation

- Empty Compose application
- Package/module structure
- Architecture boundaries
- Design system skeleton
- Testing foundation
- Git workflow

## Phase 2 — Event Domain

- Event model
- Event types
- Categories
- Event groups and group memberships
- Priority
- Recurrence model
- Event lifecycle

## Phase 3 — Local Persistence

- Database
- Repositories
- Migrations
- Persistence tests

## Phase 4 — Calendar & Basic UI

- Calendar
- Event list
- Event creation/editing
- Event details
- Event groups
- Search

## Phase 5 — Notification Engine

- Scheduling
- Multiple notifications
- Snooze
- Notification actions
- Reboot recovery

## Phase 6 — Recurrence Engine

- Recurrence calculations
- Occurrences
- Series/occurrence management
- Edge cases

## Phase 7 — Backup/Restore

- Export
- Import
- Validation
- Version migration
- Conflict handling

## Phase 8 — Settings & Polish

- Theme
- Preferences
- Categories
- Event types
- Accessibility
- UI polish

## Phase 9 — Testing & Release

- Full regression testing
- Offline verification
- Battery/background behavior
- Device testing
- Release build
- GitHub release/tag

---

# 32. Android Studio + Antigravity + GitHub Setup

## Step 1 — Install Android Studio

Install the current stable Android Studio and required Android SDK/JDK components.

Create the application using a modern Compose Empty Activity template.

Android's current guidance recommends Empty Activity as the starting point for new Compose applications.

## Step 2 — Install Git

Install Git and verify:

    git --version

Configure Git identity:

    git config --global user.name "YOUR NAME"
    git config --global user.email "YOUR EMAIL"

## Step 3 — Create GitHub Repository

Create a new GitHub repository.

Recommended:

    reminder-calendar

Do not add unnecessary files from GitHub if Android Studio will create the project locally.

## Step 4 — Create the Android Project

Create the project locally in Android Studio.

Recommended:

- Kotlin
- Jetpack Compose
- Phone/tablet
- Empty Activity

Choose a stable application/package identifier.

Example:

    com.example.remindercalendar

Replace it with the final project identifier before release.

## Step 5 — Open the Project in Antigravity

Antigravity works with local project/repository workspaces.

Open the same project/repository directory in Antigravity.

Create an Antigravity Project pointing at the local Git repository.

Use Local mode initially so changes occur in the actual working directory.

Do not immediately use autonomous/high-permission execution.

## Step 6 — Authenticate Antigravity

Sign in to Antigravity with the intended Google account.

Antigravity uses a unified authentication system across its supported surfaces.

## Step 7 — Establish Repository Baseline

In Android Studio:

- Sync Gradle
- Build the empty app
- Run it on emulator/device
- Confirm it works

Then commit:

    chore: initialize Android project

Push to GitHub.

## Step 8 — Configure Antigravity Rules

Create project-level instructions/rules telling the agent:

- Read the specification first
- Preserve architecture boundaries
- UI must remain replaceable
- No network dependencies
- No backend
- No code changes outside requested scope
- Test every change
- Do not delete existing functionality without approval
- Prefer small changes
- Ask before architectural changes

Keep these rules inside the repository so the development context travels with the project.

## Step 9 — First Antigravity Task

Do NOT ask Antigravity to build the whole application.

First ask it to:

    Inspect the repository and this specification.
    Do not modify application code.
    Produce an implementation plan for Phase 1.
    Identify architecture risks and ambiguities.
    Wait for approval.

Review the plan.

## Step 10 — Implement Phase 1

After approval, ask Antigravity to implement only the project foundation.

Then:

- Build
- Run tests
- Run app
- Inspect diff
- Commit
- Push

## Step 11 — Continue Phase by Phase

Use one feature/phase at a time.

Example:

    Phase 2 -> Event domain

Then:

    review -> test -> commit -> push

Do not combine recurrence, notifications, backup, calendar, and UI polish into one huge agent task.

---

# 33. Important Git/Antigravity Workflow

Recommended daily workflow:

    Android Studio
         |
         +--> edit/run/debug
         |
         v
    Git working tree
         |
         v
    Antigravity
         |
         +--> inspect/implement/test
         |
         v
    Review diff
         |
         v
    Git commit
         |
         v
    GitHub

Use GitHub as the source-of-truth repository.

Use Antigravity as the agent-assisted development environment.

Use Android Studio as the primary Android build/debug environment.

---

# 34. Critical Constraint

The application being offline does NOT mean Antigravity must be offline.

Antigravity is a development tool and may require its own online connectivity/authentication.

The requirement is that the BUILT APPLICATION must function completely offline.

The application itself must not depend on Antigravity, GitHub, Google services, or any external server at runtime.

---

# 35. Future-Proofing

Do not add a backend now.

However, repositories/interfaces should make a future backend possible without rewriting the UI or domain model.

Future architecture could become:

    UI
     |
    Domain
     |
    Repository Interface
     |
     +--> Local Repository
     |
     +--> Future Cloud Repository

But the current MVP must use only:

    Local Repository

No cloud implementation should be created merely for future-proofing.

---

# 36. MVP Definition

The first usable version should contain:

- Calendar
- Create/edit/delete event
- Custom event
- Event types
- Event type search
- Categories
- Priority
- Date-only events
- Date/time events
- Date-range events
- Recurring events
- Multiple notifications
- Snooze
- Complete
- Skip
- Reschedule
- Search
- Upcoming events
- Completed history
- Local persistence
- Offline notifications
- Reboot recovery
- Local backup
- Local restore
- Light/dark/system theme

Everything else is secondary.

---

# 37. Explicit Non-Goals for MVP

Do NOT implement:

- Accounts
- Cloud synchronization
- Backend
- Online calendar synchronization
- Google Calendar integration
- Ads
- Analytics
- Social features
- Online sharing
- Remote notifications
- Online event discovery

These may be considered later, but they are outside the current product.

---

# 38. Agent Instruction

When this document is provided to Antigravity, the agent must understand:

"This is the authoritative product and architecture specification for the offline reminder/calendar application. Do not begin implementation merely because this document exists. The user must explicitly authorize development. Until then, inspect, question, plan, and report."

When the user says:

    START DEVELOPMENT

begin with the current approved phase only.

Do not jump directly to the complete application.

---

# 39. Current Product Decisions

Confirmed by the user:

- All major reminder/event categories
- Additional/custom reminder types
- Event-type search
- Recurring reminders
- Multiple notifications
- Calendar-centric interface
- Date-only, date/time, and date-range events
- Countdown functionality
- Unlimited notification schedules
- All proposed snooze options
- Complete / Skip / Reschedule / Delete
- Completely offline application
- Local backup/restore
- Replaceable UI architecture

Pending future design decisions may be resolved during subsequent design phases.

---

# 40. Reference Documentation

Use current official documentation when implementing:

- Android project structure
- Jetpack Compose architecture
- Android UI layer architecture
- Antigravity setup and project workflow

Do not rely on this specification for version-specific Android/Antigravity commands when official current documentation provides newer instructions.

---

# 41. Additional Approved Features — Tier 1

The following features are approved additions to the product specification and should be designed as offline-first, local features while preserving the existing architecture boundaries.

## 41.1 Smart Dashboard / Today View

Provide a dedicated overview showing:
- Today's events
- Overdue events
- Upcoming events
- Next scheduled notification
- Useful countdowns
- Quick actions such as Complete, Snooze, and Reschedule
- Today / Tomorrow / This Week sections where useful

The dashboard is a presentation of existing domain data and must not become a separate source of truth.

## 41.2 Favorites / Pinned Events

Users can pin important events for quick access.

Pinned state belongs to the event model and is searchable/filterable locally.

## 41.3 Overdue Management

Missed events must be clearly represented as overdue when appropriate.

Actions should include:
- Complete
- Reschedule
- Skip
- Delete
- Keep overdue

For recurring events, overdue handling must operate on individual occurrences without accidentally altering the entire series.

## 41.4 Event Checklists

Events may contain local checklists/tasks.

Checklist requirements:
- Add, edit, reorder, and remove items
- Mark individual items complete/incomplete
- Show checklist completion progress
- Persist checklist state locally
- Include checklist data in backup/restore

## 41.5 Agenda / Timeline View

Provide an optional chronological agenda/timeline view in addition to calendar views.

The timeline may show events by time and date and must derive its information from the same event domain model.

## 41.6 Event Templates

Users can create reusable event templates.

Templates may define:
- Event type
- Category
- Priority
- Default duration
- Recurrence
- Notification schedules
- Checklist structure
- Default notes/metadata
- Visual preferences

Creating an event from a template must create a normal independent event.

## 41.7 Quick Add

Provide a fast event creation path that minimizes the number of steps required for common events.

Quick Add should support fast selection of:
- Event type
- Title
- Date/time
- Priority
- Notification preset

It must still use the normal domain/use-case layer rather than bypassing business rules.

## 41.8 Archive

Users can archive events that they want to retain but remove from normal active views.

Archived events:
- Remain stored locally
- Remain searchable
- Can be restored
- Must not be treated as deleted

## 41.9 Trash / Recently Deleted

Deleted events should optionally move to a local Trash state before permanent deletion.

Users should be able to:
- Restore an item
- Permanently delete an item
- Empty Trash

The retention policy, if automatic deletion is introduced, must be configurable.

## 41.10 Duplicate / Clone Event

Users can duplicate an existing event to create a new independent event.

The new event must receive its own identity and lifecycle while optionally inheriting the source event's configuration.

## 41.11 Conflict Detection

The application should detect overlapping date/time events and present a local schedule-conflict warning.

Conflict detection must account for:
- Single events
- Date ranges
- Recurring occurrences
- Overlapping times

Conflict detection is advisory and must not prevent event creation unless a future explicit setting enables such behavior.

## 41.12 Notification Profiles

Users may define reusable notification profiles such as:
- Normal
- Silent
- Critical
- Exam
- Custom

Profiles may contain notification behavior such as reminder offsets, sound, vibration, and other supported local notification preferences.

Applying a profile to an event must resolve into normal event notification configuration without introducing a runtime network dependency.

## 41.13 Quiet Hours

Users can define local quiet hours.

During quiet hours, notifications may be:
- Delayed
- Silent
- Allowed for selected critical events

The exact behavior must respect Android notification and alarm restrictions.

## 41.14 Dashboard Quick Actions

Common event actions should be available from appropriate dashboard/list surfaces without opening unnecessary screens.

Actions may include:
- Complete
- Snooze
- Reschedule
- Skip
- Open details

All actions must flow through domain use cases.

---

# 42. Additional Approved Features — Tier 2

The following advanced features are also approved for inclusion in the product design. They may be implemented after the core system is stable.

## 42.1 Event Dependencies

Events may depend on other events.

Example:

    Buy Flight Ticket -> Pack Bags -> Airport Check-in -> Flight

Dependency state may include:
- Blocked
- Ready
- Completed

Dependencies must be modeled as relationships between events rather than duplicated event data.

## 42.2 Event Relationships

Events may have explicit relationships such as:
- Related to
- Depends on
- Follow-up to
- Preparation for
- Replaces

Relationships are separate from group membership and must not be confused with categories or temporary filters.

## 42.3 Advanced Tags

Users may create reusable tags such as:
- #important
- #semester
- #dbms
- #travel

An event may have multiple tags.

Tags must support local search and filtering and remain independent from categories and groups.

## 42.4 Event Statistics

Provide optional local statistics derived from stored event history.

Examples:
- Events completed
- Events skipped
- Overdue events
- Completion rate
- Events by category
- Events by type
- Events by group
- Activity by selected time period

Statistics must be computed locally and must not transmit analytics data.

## 42.5 Offline Natural Quick Entry

The application may support deterministic local parsing of simple natural-language event input.

Example:

    DBMS exam tomorrow 10 AM

Possible interpretation:
- Title: DBMS Exam
- Date: Tomorrow
- Time: 10:00 AM
- Type: Exam

This feature must not require an AI model, cloud service, API, or network connection unless explicitly approved in a future product decision.

Parsing must provide a confirmation/edit step before committing ambiguous input.

## 42.6 App Lock

Optional local application protection may support:
- PIN
- Pattern where supported/appropriate
- Biometric authentication where supported by the device

Authentication secrets must never be stored as plaintext.

The app must remain functional without an online authentication service.

## 42.7 Local Attachments

Events may reference local attachments such as:
- Images
- PDFs
- Documents
- Screenshots

Attachments must remain local.

The architecture must define safe ownership, deletion, backup, restore, and missing-file behavior.

## 42.8 Export Formats

In addition to the versioned full backup, users may optionally export selected event data into human-readable formats such as:
- JSON
- CSV

Exports must be generated locally.

The public versioned backup format remains separate from convenience exports.

## 42.9 Sub-events

The product may support hierarchical event structures where a parent event contains child events.

Example:

    Goa Trip
      - Book train
      - Book hotel
      - Pack bags
      - Leave for station

Sub-events must remain independent domain entities with explicit parent/child relationships. They must not duplicate event data.

The design must clearly distinguish sub-events from checklists, groups, and dependencies to avoid overlapping semantics.

## 42.10 Enhanced Location Data

Events may store structured local location information in addition to free-form location text.

Possible fields:
- Place name
- Address text
- Building
- Room number
- Optional manually provided local coordinates

No online map lookup is required or permitted for the offline product unless explicitly approved later.

---

# 43. Cross-Feature Architecture Rules

All newly approved features must follow these rules:

1. The application remains completely functional without internet access.
2. No feature may introduce cloud storage, remote APIs, accounts, telemetry, advertisements, or remote notifications.
3. Event remains the single source of truth.
4. Relationships such as groups, dependencies, tags, and parent/child links must reference events rather than duplicate event records.
5. Business rules belong in the domain/use-case layer.
6. Persistence belongs behind repository interfaces.
7. Notification behavior belongs to the notification subsystem.
8. UI components must consume immutable UI state and emit user actions.
9. New features must not couple the domain layer to Jetpack Compose.
10. All persistent new data must be included in versioned backup/restore where applicable.
11. New functionality must have appropriate unit/repository/UI tests before being considered complete.
12. New features must handle app restart, device reboot, lifecycle changes, and database migrations where relevant.
13. The UI remains replaceable; a future UI redesign must not require rewriting the underlying feature logic.

---

# 44. Expanded Domain Relationship Model

The conceptual model now includes:

    Event
      |
      +---- Group Membership ----> Event Group
      |
      +---- Tag Membership ------> Tag
      |
      +---- Dependency ----------> Event
      |
      +---- Relationship --------> Event
      |
      +---- Parent/Child --------> Event
      |
      +---- Checklist -----------> Checklist Items
      |
      +---- Notifications -------> Notification Schedule
      |
      +---- Attachments ---------> Local Attachment
      |
      +---- Template Source -----> Event Template (when created from one)

These relationships must remain explicit and must not cause duplicated event records.

---

# 45. Expanded Testing Requirements

Additional tests are required for the new features.

## Dashboard and views
- Correct Today/Upcoming/Overdue grouping
- Correct countdowns
- Correct quick-action behavior

## Favorites
- Pin/unpin persistence
- Search/filter behavior

## Checklists
- Item CRUD
- Completion state
- Ordering
- Backup/restore

## Templates
- Template creation/editing/deletion
- Event creation from template
- Independence between template and created event

## Archive/Trash
- Archive/restore
- Trash/restore
- Permanent deletion
- Notification cleanup after permanent deletion

## Conflicts
- Same-time overlap
- Partial overlap
- Date-range overlap
- Recurring occurrence overlap
- No false conflict for non-overlapping events

## Dependencies and relationships
- Create/remove relationship
- Circular dependency detection where applicable
- Dependency state calculation
- Deleting related events safely

## Tags
- Tag CRUD
- Multi-tag events
- Search/filter
- Backup/restore

## Natural Quick Entry
- Valid date/time parsing
- Ambiguous input confirmation
- Invalid input handling
- Fully offline operation

## App Lock
- Correct lock/unlock behavior
- Failed authentication handling
- Local-only credential protection

## Attachments
- Add/remove
- Missing file handling
- Backup/restore behavior
- Event deletion behavior

## Sub-events
- Parent/child creation
- Reordering where supported
- Independent lifecycle
- Deleting parent safely

## Enhanced location
- Structured field persistence
- Backup/restore
- No network dependency

---

# 46. Expanded Development Phase Guidance

The approved features should be introduced incrementally rather than implemented simultaneously.

Suggested additions to the existing phases:

## Phase 2 — Event Domain
Add:
- Pin/favorite state
- Archive/trash state
- Tags
- Dependencies
- Event relationships
- Parent/child relationships
- Template domain model
- Checklist model

## Phase 3 — Local Persistence
Add:
- Persistence for new entities and relationships
- Database migrations
- Attachment metadata
- Backup model preparation

## Phase 4 — Calendar & Basic UI
Add:
- Smart Dashboard
- Agenda/Timeline
- Favorites
- Checklists
- Templates
- Archive
- Trash
- Tags
- Relationship/dependency views

## Phase 5 — Notification Engine
Add:
- Notification profiles
- Quiet hours
- Overdue notification handling
- Conflict-aware presentation where relevant

## Phase 7 — Backup/Restore
Add:
- Groups
- Tags
- Dependencies
- Relationships
- Parent/child links
- Checklists
- Templates where applicable
- Attachments according to selected backup policy

## Phase 8 — Settings & Polish
Add:
- Quiet hours configuration
- Notification profiles
- App lock
- Export formats
- Dashboard customization

## Phase 9 — Testing & Release
Add full regression testing for all Tier 1 and Tier 2 features, with special attention to migration, restore, reboot recovery, notification cleanup, and offline operation.

---

# 47. Updated Product Scope

The application is now intended to function as a comprehensive offline personal event and reminder manager rather than only a basic reminder list.

Core pillars:

1. Calendar and timeline management
2. Reminders and recurring events
3. Local notifications
4. Event groups and organization
5. Checklists and templates
6. Search, tags, and filtering
7. Dependencies and event relationships
8. Local history, archive, and trash
9. Local statistics and countdowns
10. Local backup, restore, and export
11. Optional local security
12. Strict zero-network runtime architecture
13. Replaceable UI architecture

The product remains offline-first and local-only. No newly approved feature changes that requirement.

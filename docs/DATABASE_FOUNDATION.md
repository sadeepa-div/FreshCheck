# Phase 1 — Room database foundation

## Scope and compatibility

This documents the Phase 1 foundation on Member 3's feature/database-reminders branch.
Phase 2 now connects the repository to MainActivity/FreshCheckApp through a ViewModel;
see [DATABASE_UI_INTEGRATION.md](DATABASE_UI_INTEGRATION.md). DemoInventory remains isolated;
camera/inference, WorkManager and notifications are not implemented by these phases.

The existing AGP 9.5.1, Kotlin Compose plugin 2.2.10, Compose BOM and SDK settings are preserved.
Room 2.8.5 and KSP2 2.3.9 are declared in the version catalog. AGP 9 uses built-in Kotlin;
no kotlin-android or kapt plugin is added. Coroutines core 1.10.2 is an explicit dependency
for the repository's Flow and suspend APIs. Room 2.8 runtime already contains its coroutine
APIs, so an extra room-ktx artifact is unnecessary.

Compatibility references checked during implementation:
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://github.com/google/ksp/releases/tag/2.3.9

## Database and schema

FreshCheckDatabase opens freshcheck.db, schema version 1, using application context and a
thread-safe singleton. It has no production seed data, main-thread allowance, or destructive
migration fallback. Future schema changes require an explicit migration and migration tests.
KSP exports schemas to app/schemas; the generated version 1 JSON belongs in version control
after review. No migration from DemoInventory exists or is needed.

Dates are epoch-day INTEGER values. Event and audit timestamps are UTC epoch-millisecond
INTEGER values. Millisecond precision is the persisted precision. Display mappings take an
explicit ZoneId and convert to the existing LocalDateTime UI contract. Scan persistence
requires an explicit Instant supplied by the future adapter: analyzedAtLabel is not parsed,
and persistence time is not fabricated as an analysis time.

Enum names are TEXT values, never ordinals or translated labels. Existing category, storage,
scan mode, visible condition and outcome enums are reused to preserve contract terminology;
renaming persisted enum constants requires a database migration. Unknown stored enum values
fail visibly rather than silently reclassifying an item.

## Entities and relationships

- FoodItemEntity: stable opaque ID, canonical produceId, name/category, quantity/unit,
  storage, added/recheck dates, notes, Active/Used/Discarded status and audit instants.
  Status and added-date index supports active inventory. Re-check dates never determine
  visible condition or imply that a notification was scheduled.
- ScanRecordEntity: UUID scan ID, unique original requestId, nullable live foodId, retained
  originalFoodId snapshot, produce identity/name/category, mode, condition, nullable confidence,
  image URI, explicit event instant and nullable embedded StoredGuidance.
  foodId references food_items with ON DELETE SET NULL. Food/time and history/time indexes
  support observation. A request may be persisted once; a duplicate reports Conflict.
- FoodOutcomeEntity: UUID event ID, original food ID, name/category snapshot, Used or
  Discarded and event instant. Unique foodId permits one terminal outcome per item.
  No foreign key intentionally: the audit snapshot survives explicit food deletion.
- ReminderEntity: UUID definition ID, required foodId FK with ON DELETE CASCADE, due instant,
  enabled flag, DefinitionOnly/Disabled state, created/updated instants.
  Indexes cover food and due/enabled/state lookups. There is no Scheduled state or OS job ID.
- FoodWithScans: Room relation projection only. DAO @Transaction methods read food plus its
  scans consistently; it is not another table or a replacement UI model.

## Retention and atomic operations

Mark Used and Mark Discarded each run one Room transaction:
1. Require an existing Active food item.
2. Change its status, insert the distinct outcome, and disable its reminder definitions.
3. Confirm success only when the transaction commits.

The row leaves active inventory but remains in all-food queries. Scans and their associations
remain unchanged. Repeat terminal operations, edits of terminal food and rescans of terminal
food are rejected. Deleting a food row is a separate operation and never creates an outcome:
SQLite detaches live scan links, keeps all scan snapshots and originalFoodId, removes reminder
definitions, and leaves existing outcome snapshots intact. The UI history mapping exposes
foodId = null after deletion, so it cannot navigate to a deleted item.

Individual scan deletion and explicit history clearing are supported independently of
inventory, outcomes and reminders. Clearing history removes request deduplication records too;
it should be a deliberate user action. It can leave food with no scans, which maps to Uncertain.
Food IDs are immutable opaque identities: future adapters must generate UUIDs for new food,
never reuse deleted IDs, and never derive them from produce names, positions or dates.
Canonical produceId identifies a catalogue entry, not a tracked item.

## DAOs and repository

Four DAOs provide suspend insert/update/delete/lookups and ordered Flow queries. Inserts use
ABORT, never REPLACE (which could trigger cascading deletion). Update/delete return affected
counts for missing-record checks. findDue returns enabled definitions due at/before an explicit
instant for Active food only; it does not schedule or deliver anything.

FreshCheckRepository / RoomFreshCheckRepository provide inventory CRUD, scan persistence and
lookup/deletion/clearing, outcome observation and atomic marking, and reminder CRUD/due queries.
Outcomes are recorded through terminal operations rather than an independently writable public
repository API, preventing inconsistent food status and outcome combinations.

Food insertion rejects embedded scan history: use real ScanSubmission operations separately.
Food editing preserves stored creation time and status and ignores the UI's read-only scans;
it cannot rewrite history. Standalone scans (including Before Buying) are allowed without
automatically inventing inventory. An associated scan must reference an existing Active food.
addScanToFood also checks the argument against the original request's rescanFoodId.

DatabaseResult distinguishes Success from InvalidInput, MissingRecord, InactiveFood, Conflict
and Storage failures. Coroutine cancellation is rethrown; exceptions are caught outside Room
transactions so failures roll back before being reported. Observation errors propagate to
collectors; a future UI adapter must display them, not substitute empty or sample data.
DAOs are low-level primitives; application writes must use the repository to enforce validation,
provenance and multi-table invariants.

PersistenceMappers bridge existing FoodItemUi, FoodScanUi, ScanHistoryRecord, OutcomeRecord and
ScanSubmission without altering them. Demo flags and reserved demo- IDs (including canonical
IDs and request IDs) are rejected before writes. Unlabelled synthetic content cannot be inferred
from its name: callers must preserve provenance flags honestly. Tests use isolated synthetic
fixtures only. No demonstration rows are imported into the production database.

## Images and responsible interpretation

Only URI strings are stored; this phase does not obtain durable grants, copy images, download
images or delete files. Member 2/3's later integration must define URI ownership and retention.
Snapshots preserve optional guidance and confidence without inventing missing information.
All conditions describe visible external appearance, not food safety, internal quality, expiry
or nutrient measurements. Reminder definitions are check-in suggestions only.

## Tests and verification

PersistenceMappersTest contains local JVM tests for converters, all persisted enums, nulls,
demo rejection, identity, explicit timestamps and UI mappings.
FreshCheckDatabaseTest uses a fresh real in-memory Room database per Android test. Coverage
includes inventory CRUD and Flow invalidation, standalone and repeated associated scans,
request uniqueness, distinct outcomes, retained and detached history, missing/invalid IDs,
reminder CRUD and due filtering, SQLite foreign keys, competing terminal operations, and rollback
after injected outcome failure. A separate uniquely named file-backed test closes/reopens its
database to verify durability, then deletes only that test-owned database.

Validation commands:
- .\gradlew.bat :app:assembleDebug
- .\gradlew.bat :app:testDebugUnitTest
- .\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.freshcheck.app.data.FreshCheckDatabaseTest

Validation performed on November 18, 2026:
- :app:assembleDebug — passed (2m 24s); Room generated the version 1 schema.
- :app:testDebugUnitTest — passed (15s), including all four new JVM mapper/converter tests.
- :app:assembleDebugAndroidTest — passed (13s); the 12 new Room tests and existing
  instrumented sources compiled and packaged.
- Android Studio Gradle sync — passed.
- Room instrumented execution — NOT RUN. ADB initially listed no attached device. The
  installed Medium_Phone_API_37.0 AVD was started via the Android CLI but exited before boot:
  its userdata partition requires 12,288 MB; only approximately 7,769 MB was available.
  No AVD resizing, data deletion, notification permission, or device reset was performed.
  The compiled test APK is not evidence of runtime Room test success.

The initial wrapper invocation failed because JAVA_HOME was unset. Successful wrapper commands
used Android Studio's bundled JDK at F:\\android studio\\jbr via process-local JAVA_HOME;
no project JDK setting or machine-wide environment setting was changed.

Once a compatible device is connected, run the connectedDebugAndroidTest command above to
execute the Room suite. The new tests verify production behavior using isolated synthetic data;
none prepopulate or modify the application's named production database.

## Original Phase 2 integration plan

Implemented behavior and validation are now documented in [DATABASE_UI_INTEGRATION.md](DATABASE_UI_INTEGRATION.md).
The original handoff requirements follow:

Add a lifecycle-aware ViewModel adapter that collects independent repository inventory,
history and outcome streams into AppUiData. Translate DatabaseResult into UiOperationResult
and deliver callbacks on the main thread only after commit. Connect food/history capabilities
explicitly; do not replace DemoInventory until that adapter is ready. Member 2 must supply the
original ScanRequest and a trustworthy event Instant, with durable image URI ownership.
Design the explicit new-inventory-from-scan workflow separately; saving a standalone history
entry does not create food implicitly. Later phases can add stored preferences, scheduling,
permissions/channels and notification delivery. Stored reminders alone are not scheduled work.

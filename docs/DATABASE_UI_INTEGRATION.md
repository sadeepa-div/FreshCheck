# Phase 2 — Database and UI integration

## Delivered scope

MainActivity now launches FreshCheckRoute. The existing FreshCheckApp and feature screens
receive Room state and callbacks; no database is created in a Composable and no screen
directly calls a DAO. DemoInventory, the scanner demo, previews, visual-condition terminology
and the existing search/filter/sort/chart calculations are preserved.

No camera, gallery, AI inference, model/labels, WorkManager, notification permission or delivery
implementation was added. No production database is prepopulated. New scan-to-inventory
creation remains explicitly pending for the reasons below.

## Ownership, loading and errors

FreshCheckViewModelFactory captures application context only. The Activity ViewModelStore
owns one FreshCheckViewModel. Its provider constructs RoomFreshCheckRepository using the
existing FreshCheckDatabase singleton on Dispatchers.IO, once after successful initialization.
Retry reuses that repository. Repository initialization failures can be retried.

The ViewModel observes four independent sources:
- active inventory with related scans;
- all persisted scan history, independent of active inventory;
- Used outcomes;
- Discarded outcomes.

Each source supplies a read-only copied snapshot. Food scan lists are copied too. Demo flags,
reserved demo- IDs and incorrectly typed outcome streams are rejected instead of displayed as
production records. No synthetic records are created on observation failure.

AppUiData initially has isLoading=true and inventoryConnected=false. Connection becomes ready
only after all four sources emit successfully, including empty lists. A read error sets
inventoryConnected=false and isLoading=false, preserves last-known production records and
displays an error. Retry is explicit through the existing top-bar error area.
The production flag on FreshCheckApp prevents its legacy disconnected-demo fallback during
both loading and failure. Existing preview/demo callers retain their default behavior.

Room observation is bounded by viewModelScope and is cancelled when the ViewModelStore is
cleared. Compose consumes StateFlow with collectAsStateWithLifecycle. Mutable navigation,
theme and display-name state remain owned by Member 1's coordinator as before.

## Commands and UI callbacks

FreshCheckIntegration creates AppIntegrationActions for a route-owned coroutine scope.
FoodMutationActions delegates edits, Used, Discarded and deletion to suspend ViewModel commands.
HistoryActions delegates individual scan deletion and clearing, with capabilities enabled only
while data is connected. Profile forwards these capabilities to Settings.

All repository mutation work is dispatched off the main thread. A shared try-lock prevents
overlapping database commands, including repeated taps. Existing screen busy checks also
prevent repeat submissions. DatabaseResult.Success is translated to UiOperationResult.Confirmed
only after the repository returns from its committed operation. Failures retain their useful
messages; unexpected exceptions report failure. Cancellation is rethrown, not reported as success.

The adapter delivers one completion on Dispatchers.Main.immediate per live invocation.
UiCompletionGate prevents duplicate delivery, drops callbacks after the source screen is
disposed, and waits for a background lifecycle owner to start before delivering. Route
cancellation cancels pending work and suppresses its completion. Busy flags are not restored
as permanently true across recreation. The ViewModel never retains UI completion lambdas.
If cancellation happens after SQLite has committed, the stored records remain authoritative;
no cancelled screen claims success and later observation/retry shows committed state.

My Food receives AppUiData.activeFood without changing its existing search, filters, sort,
details, editing or journey models. Existing confirmations are reused. Used/Discarded remove
food from active inventory and record the distinct outcome atomically. Delete creates no
outcome. Phase 1 scan retention is unchanged.

Scan History receives AppUiData.scanHistory, including standalone Before Buying scans and
scans of terminal or explicitly deleted food. Existing timestamps, modes, nullable confidence,
image URI references and guidance are retained. Explicit food deletion leaves a detached
history snapshot. Clearing history does not clear inventory, outcomes or reminder definitions.

Home still uses homeSummary. Insights still uses activitySummary/recordedOutcomes and existing
charts. They receive active inventory, independent history and distinct outcome streams,
rather than reconstructing history from active items.

## Scanner boundary and exact pending work

ScanSubmission has one additive, nullable field:
- scannedAt: Instant? = null

Existing callers remain source-compatible. Null is display-only: the production adapter refuses
to accept/save it. analyzedAtLabel is never parsed, and current time is never substituted for
an analysis event time. Future timestamps are rejected; the scanner must supply its actual event
Instant with an appropriate device clock.

FreshCheckViewModel.scanRequests exposes the current ScanRequest as StateFlow. Member 2 can
observe that identity within its own lifecycle-aware camera/inference coordinator. On completion,
it calls suspend acceptAnalysis(ScanSubmission(originalRequest, realResult, actualEventInstant)).
No capture or inference is started by Phase 2 itself.

acceptAnalysis returns AnalysisAcceptance.Accepted or Rejected: acceptance means display
eligibility, not database persistence. Only saving via AppIntegrationActions.saveScan can
return UiOperationResult.Confirmed for database success.

Acceptance and saving reject demo results/IDs, closed or changed request identities, missing
or future event times and already-saved request IDs. Saving additionally requires exact equality
with the accepted payload. Request ID, mode, optional rescanFoodId and the original timestamp
are preserved. The repository independently enforces unique request IDs and active rescan targets.
Successful request IDs remain consumed for the ViewModel lifetime even after history clearing.

The same request reattaching after configuration recreation retains the accepted analysis.
Logical navigation away from Scan closes it. A changed request clears its old completion.
ScannerFlow forwards the accepted submission intact so its timestamp is not lost when rendering
the existing result screen. Real Before Buying results have a save-history action; rescans save
against the same food ID. No replacement food ID is generated.

A new After Buying scan cannot currently create inventory:
- Member 2's capture/inference integration is absent; only the labelled demo runs.
- The result UI has no new-food quantity, unit, storage or date-added inputs.
- A re-check date is optional and must not be inferred from visible condition.

The existing pending action remains labelled and returns a specific failure explaining the
missing food details. No food or scan rows are partially inserted through that action.
No guessed defaults or new form were introduced. A later explicitly designed creation form
must submit validated food details plus the real scan in one repository transaction.
Phase 1 insertFood remains available for valid explicit production inputs; it is not connected
to a new UI form by this phase.

Image ownership remains a Member 2/3 integration dependency: Phase 2 stores URI references
only and does not obtain durable grants, copy images or delete files.

## Demo and reminder boundaries

My Food's explicit sample switch uses its existing DemoInventory only. Sample edits/removals
never call production mutations. Returning to saved inventory reads Room again. Home sample
exploration, labelled history samples and Insights sample activity remain visibly labelled;
they never seed or overwrite production storage.

Food re-check dates are persisted through the existing edit action. If an edit changes/removes
that date, RoomFreshCheckRepository disables old reminder definitions in the same transaction.
The old due instants are retained as disabled definitions; no time is guessed from a date.
Unchanged dates preserve existing definition state. Food deletion still cascades definition
deletion; Used/Discarded disable definitions as before.

No definition is claimed to be scheduled. Phase 3 must design time/timezone input, permissions,
channels, scheduling, cancellation/rescheduling and notification delivery.

## Dependencies and validation

Added direct lifecycle-runtime-compose and lifecycle-viewmodel-compose dependencies at 2.9.4,
matching the Lifecycle family already resolved by Phase 1. Added coroutines-android and the
test-only coroutines-test artifact at the existing 1.10.2 version. AGP, Kotlin, Compose BOM,
SDK settings, Room schema version and exported version 1 schema are unchanged.

References:
- https://developer.android.com/develop/ui/compose/state
- https://developer.android.com/topic/libraries/architecture/viewmodel
- https://developer.android.com/kotlin/coroutines/test

FreshCheckViewModelTest contains 16 JVM tests covering loading/readiness, empty state, independent
Flow updates and Home/Insights calculations, mutations, callbacks, failures/retry, demo rejection,
scan identity/timestamps, stale/duplicate handling, pending new-food creation and cancellation.
FakeFreshCheckRepository is an isolated synthetic JVM test fixture.
Two real Room tests were added to FreshCheckDatabaseTest for re-check edits and rollback of a
failed reminder-definition update. The existing persistence/retention tests remain in place.

Validation on November 18, 2026:
- .\gradlew.bat :app:assembleDebug — passed.
- .\gradlew.bat :app:testDebugUnitTest — passed, including all 16 Phase 2 JVM tests and the existing suite.
- .\gradlew.bat :app:assembleDebugAndroidTest — passed compilation and packaging.
- The final combined run of all three tasks passed in 15 seconds (76 tasks, 41 executed).
- Android Studio Gradle sync — passed.
- git diff --check — passed.
- Android instrumented execution — NOT RUN. Full-device testing is deferred as requested;
  no emulator setup or disk cleanup was attempted. Phase 1's known emulator storage limitation
  was not changed or re-tested. Compiling a test APK is not a runtime test pass.

The bundled JDK path F:\android studio\jbr\bin\java.exe was verified before building.
JAVA_HOME was set only in each build process, with no machine-wide or project JDK changes.
The Room version 1 schema was unchanged; no migration or destructive fallback was added.

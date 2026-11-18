# FreshCheck UI integration contracts — Phase 6A

This describes UI boundaries, not connected CameraX, LiteRT, Room, DataStore or reminder services.

Member 3 Phase 2 connects the Room foundation through FreshCheckRoute and FreshCheckViewModel.
See [DATABASE_UI_INTEGRATION.md](DATABASE_UI_INTEGRATION.md) for lifecycle, callbacks, timestamp requirements and pending scanner inputs.
See [DATABASE_FOUNDATION.md](DATABASE_FOUNDATION.md) for the Phase 1 schema and retention policy.

## IDs and provenance

- FoodItemUi.id and ScanHistoryRecord.id are stable opaque IDs owned by Member 3. Never use list positions, produce names or timestamps as production IDs.
- produceId is a separate canonical catalogue ID, shared by Member 2's labels and the nutrition catalogue. It is not a food or scan ID.
- Production scan history is AppUiData.scanHistory. Do not reconstruct it from active food. Before Buying records may have foodId = null.
- Demo IDs use demo- prefixes and isDemo = true. Never write demonstration data through production callbacks.
- Deleting active food must follow an explicit scan-retention policy; marking Used/Discarded should retain history.

## App coordinator

FreshCheckApp accepts AppUiData and AppIntegrationActions. A lifecycle-aware ViewModel adapter should collect Member 3 flows and pass immutable snapshots.
inventoryConnected distinguishes a connected empty inventory from disconnected session samples.
activeFood, scanHistory, usedEvents and discardedEvents are independent inputs. The event collections reuse OutcomeRecord/FoodOutcome.
Loading and errors belong to supplied state. Never turn a failed load into success or silently replace real data with samples.
Home receives HomeUiState, computed with homeSummary. Zero input produces zero counts and empty sections. Its optional sample dashboard is separately labelled.
Insights receives active food, independent history and both outcome streams. Counts represent recorded items, not food weight or environmental impact.

## Member 2 — scanner input/output

ScanRequest contains requestId, ScanMode and optional rescanFoodId.
Preserve all three from Food Details through capture/inference/result and persistence.
A food rescan never creates a replacement food ID. An explicit new scan uses a new request ID; Scan Again within the flow retains the food association.
AnalysisResult contains visible condition, nullable finite confidence in [0,1], optional URI/timestamp/guidance and canonical produceId.
ScanSubmission combines the request with AnalysisResult and optional scannedAt: Instant. This is an unsaved proposal, not a stored scan record.
Production acceptance/saving requires the actual scannedAt supplied by Member 2; a display label or current-time fallback is not used.
Supply the original request with completion so stale inference output cannot attach to a newer request.

Existing ScannerUiState, ScannerActions and ScanScreen preview slot remain available. Member 2 must provide:
- camera permission/controller lifecycle, capture and gallery state;
- URI ownership/orientation handling and off-main-thread inference;
- cancellation, unsupported input, low confidence and failure states;
- real AnalysisResult mapping, without guaranteed safety or expiry claims.
The existing ScannerFlow remains a demo adapter. Supplying a real result does not implement camera capture.

## Member 3 — mutations

FoodActions exposes distinct onMarkUsed(foodId), onMarkDiscarded(foodId), and onDelete(foodId) navigation intents.
FoodMutationActions exposes separate confirmed async operations and edit(item, completion).
Used and Discarded must record different FoodOutcome values and remove the item from active inventory atomically.
Delete is a different operation and must not invent a Used/Discarded event.
Confirmations describe each operation; UI success only follows UiOperationResult.Confirmed.
Failed(message) leaves the record available and displays failure feedback. Default callbacks return integration-pending failures.

AppIntegrationActions.saveScan receives ScanSubmission and a UiOperationResult callback.
If rescanFoodId is present, validate it and append the scan transactionally to that item.
If it is missing, retain standalone Before Buying history without requiring an inventory item.
Assign the real scan ID in the persistence layer, return confirmation only after commit and update observable snapshots.
Never persist isDemo results.

HistoryActions is reused for individual deletion/clearing capabilities. Keep capabilities false until repository operations can confirm completion.
Reminder actions/settings remain pending: connect permission, channel, stored reminder and scheduler states separately. A re-check date is not proof of scheduling.
No reminder service is implemented by the UI phase. The separate Member 3 Phase 1 Room foundation
stores reminder definitions only. Phase 2 persists re-check edits and disables stale definitions atomically; notifications remain unconnected.

## Async behavior and lifecycle

- Deliver callbacks on the main thread, once per operation. Member adapters catch service exceptions and map them to Failed.
- Loading/busy UI must prevent repeated submissions. Lifecycle owners cancel outstanding work on exit or reject stale completions by request ID.
- A completion must not imply persistence of a different request or food ID.
- Repository data remains authoritative; do not optimistically create successful scan records.
- Edit validation checks quantities, units and ISO dates before emitting a command. Stored re-check dates may be overdue.
- Do not hold image bitmaps or large database lists in saved-instance state. Persist IDs and reload production data.

## Images and nutrition

ProduceImage receives local content/file URIs, decodes off main thread with downsampling and uses an illustration on missing/unreadable input.
Member 2/3 owns durable URI permissions, retention and deletion. Remote URLs are not downloaded.
Selected produceId is carried to nutrition. Supply a verified NutritionProfile for that ID; otherwise show a pending state.
The generic nutrition demo is explicitly synthetic. Neither nutrient content nor a serving mass is measured from camera pixels.

## Demo boundaries and compatibility

DemoInventory is an isolated session sample provider. Its history and outcomes remain after active-item removal; clear/reload explicitly resets the sample dataset.
Sample Used/Discarded commands may create labelled demo outcomes, never real predictions or persisted records.
Home and Insights can expose separate, visibly labelled sample exploration.
The demo saved-state snapshot changed in Phase 6A: do not rely on restoration from an older installed build. No Room migration is involved.
New nullable model fields have defaults; FoodActions intentionally replaces onRemove with three callbacks. Update callers and test fakes accordingly.

## Responsible AI

AI estimates visible external condition only. It cannot confirm food safety, internal spoilage, pathogens or chemical contamination.
Only Fresh, Use Soon and Visibly Spoiled are visual condition labels; Uncertain / Unable to Assess is separate.
Never equate confidence with safety, expiry or measurable deterioration.
Do not recommend consuming visibly spoiled produce. Uncertain inputs require another clear image/manual checking.
Reminder dates are check-in suggestions, never guaranteed expiry dates.

## Request delivery in the prepared coordinator

AppIntegrationActions.onScanRequest receives the current request when the scanner opens or its mode/request changes.
Member 2 returns ScanSubmission in AppUiData.completedAnalysis. analysisFor accepts it only if the request ID, mode and optional food ID all match.
Do not assign a UI demo request ID to real results. Root navigation generates a fresh request ID on a new scan or Scan Again while preserving the rescan food ID.
The coordinator does not invoke camera or inference services. Phase 2 registers requests with FreshCheckViewModel;
Member 2 can observe scanRequests and return a real timestamped submission via acceptAnalysis.
Accepted analysis is distinct from confirmed persistence. New After Buying inventory creation remains pending until a user supplies food details.
AppUiData.isLoading/error are displayed in the main scaffold. Adapters should also use ScannerUiState for scanner-specific loading/error presentation.
A confirmed operation updates snapshots from Member 3; the UI does not fabricate a replacement scan or claim a notification was scheduled.

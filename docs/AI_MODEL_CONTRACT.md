# FreshCheck AI model contract

Status: implementation preparation; runtime inference and training parity are not yet verified.

Owner: Member 2, `feature/ai-camera`. Inspected on November 24, 2026 at application commit `21a568f6f1fd28b6030bea803b7a12bdefa31477`.

This contract describes the existing bundled model without changing its bytes, labels, application code, or dependencies. It complements [INTEGRATION_CONTRACTS.md](INTEGRATION_CONTRACTS.md). Visual condition estimates cannot confirm food safety, internal spoilage, pathogens, chemical contamination, expiry, or nutrient content.

## Evidence and verification limits

**Verified from repository assets** means the file, FlatBuffer tables, operator codes, or constant values were read directly. Inspection used Python standard-library `struct` and `hashlib` in memory; no LiteRT/TFLite interpreter was installed or invoked. This is structural inspection, not successful inference or an accuracy evaluation.

**Inferred** identifies a conclusion suggested by the graph. **UNVERIFIED** identifies information not established by the available training/export evidence. A proposed Android behavior is a future requirement, not implemented functionality.

## Asset identity

| Property | Verified value |
|---|---|
| Model | `app/src/main/assets/freshcheck_3class.tflite` |
| Labels | `app/src/main/assets/labels.txt` |
| Asset-introduction commit | `a919178` (`feat(ml): add trained FreshCheck freshness classification model`) |
| Model size | 8,901,272 bytes, approximately 8.49 MiB |
| Model SHA-256 | `c8f81395acef4287a0cf80cbaa953e22fcad9eac88952b490adb65a140ed98ca` |
| Labels SHA-256 | `c5eec3b3c9ab99a6a5b43c1efa6749f0dcdaf4a9d21afc157e01a81d6f74d7eb` |
| FlatBuffer identifier / schema version | `TFL3` / `3` |
| Description | `MLIR Converted.` |
| Subgraphs | One, named `main` |
| Graph size | 177 tensors and 67 operators |
| Metadata entries | `min_runtime_version`, `CONVERSION_METADATA` |
| Custom operators | None declared |

Tensor names include `mobilenetv2_1.00_224_1`, global average pooling, and a dense classification head. A MobileNetV2-based architecture is **inferred**; the original training configuration and weights provenance are **UNVERIFIED**.

There is no `TFLITE_METADATA` entry supplying an application-level image contract. The separate label file is present, but its correspondence to training class indices is **UNVERIFIED**. Conversion/runtime metadata does not establish training accuracy, supported produce, preprocessing parity, or Android runtime compatibility.

Recheck asset identity after any model or label update:

```powershell
Get-Item app/src/main/assets/freshcheck_3class.tflite | Select-Object Length
Get-FileHash app/src/main/assets/freshcheck_3class.tflite -Algorithm SHA256
Get-FileHash app/src/main/assets/labels.txt -Algorithm SHA256
Get-Content app/src/main/assets/labels.txt
```

## Input and output tensors

Both tensors have type code `0` (`FLOAT32`). Their quantization scale and zero-point vectors are empty; do not apply an integer quantization formula to these interfaces.

| Property | Input | Output |
|---|---|---|
| Input/output ordinal | 0 | 0 |
| Graph tensor index | 0 | 176 |
| Name | `serving_default_input_layer_1:0` | `StatefulPartitionedCall_1:0` |
| Stored shape | `[1, 224, 224, 3]` | `[1, 3]` |
| Shape signature | `[-1, 224, 224, 3]` | `[-1, 3]` |
| Elements at stored shape | 150,528 | 3 |
| Float buffer size at stored shape | 602,112 bytes | 12 bytes |

Use batch size 1 for the initial Android adapter. The dynamic batch dimension in the shape signature is not evidence that other batch sizes have been tested. Interpret the image input as batch, height, width, channels (NHWC); pack pixels in row order with three channel floats per pixel. Three channels are verified; whether their training meaning is RGB rather than another order is **UNVERIFIED**.

The graph tensor index is not an interpreter input/output ordinal. In particular, output ordinal 0 refers to graph tensor 176 in this asset. Inspect tensors through the runtime API rather than hardcoding graph tensor indices.

The future runtime adapter must verify input/output counts, data types, and shapes before inference, and fail explicitly if an updated asset does not match this contract.

## Embedded normalization

The first two graph operators are verified:

1. `MUL` (builtin code 18): input tensor 0 multiplied by scalar tensor 108, whose float value is `0.007843137718737125`.
2. `ADD` (builtin code 0): that result plus scalar tensor 109, whose float value is `-1.0`.

These constants implement approximately:

```text
model_normalized_value = input_pixel_float / 127.5 - 1.0
```

The graph therefore supports supplying 0-255 image intensities as floats at its external input, producing approximately -1 to +1 internally. This external intensity range is an **inference from the embedded transform**; agreement with the actual training pipeline remains **UNVERIFIED** until checked against training code and reference examples.

For the intended 0-255 input, a channel value of 0 maps to -1, 127.5 maps approximately to 0, and 255 maps approximately to +1. These are arithmetic expectations, not measured interpreter results.

**Do not normalize twice.** The Android adapter must not additionally divide by 255, apply `pixel / 127.5 - 1`, run an equivalent MobileNetV2 preprocessing function, or add a normalization helper without verifying whether its transform duplicates these graph operators. Merely casting an 8-bit intensity to float preserves its 0-255 value.

## Labels and output interpretation

The final graph operator is `SOFTMAX` (builtin code 25), writing output tensor 176. Treat the three outputs as class scores after softmax; do not apply softmax again. Runtime checks must reject non-finite values, values outside [0, 1], or an invalid total, allowing a documented floating-point tolerance for a total near 1.

The current sidecar file lists the following **intended mapping** in line order:

| Output position | Exact label text | Existing UI condition |
|---|---|---|
| 0 | `Fresh` | `FreshnessCondition.Fresh` |
| 1 | `Use Soon` | `FreshnessCondition.UseSoon` |
| 2 | `Visibly Spoiled` | `FreshnessCondition.VisiblySpoiled` |

**UNVERIFIED: these positions match the training/export class indices.** Obtain the original class-index mapping and known-image predictions before enabling production label interpretation. Keep label order intact; do not alphabetically sort labels or infer their meaning from tensor shape. Validate exactly three non-empty labels.

After that mapping is verified, argmax selects a candidate condition and its score can supply the UI confidence field. A softmax score is not a calibrated probability of correctness or food safety. No confidence threshold or calibration method has been validated; do not document an arbitrary threshold as model performance.

`FreshnessCondition.Uncertain` is an application abstention state, not a fourth model output. Unusable images, unsupported inputs, or predictions failing a validated acceptance policy must not silently become `Fresh`. Technical failures should produce an explicit error/unable-to-assess state rather than a fabricated prediction or demo result.

There is no separate produce-identity, bounding-box, or unsupported-image output. High softmax confidence alone cannot establish that the input contains supported produce. The application still needs a validated suitability policy and a confirmed supported-produce scope.

## Preprocessing requirements for the future Android adapter

Camera capture and gallery selection must converge on the same pipeline. The following are implementation requirements; they are not implemented by this document.

1. **Own a readable image.** Handle cancelled selection, missing/revoked URI access, empty content, unsupported formats, and decode failure explicitly. Define durable URI permissions or app-owned copies and image retention with Member 3. A temporary cache URI is insufficient for durable scan history.
2. **Decode with bounded memory off the main thread.** Read dimensions before allocating large bitmaps and downsample as appropriate. Do not store bitmaps or tensor buffers in saved-instance state.
3. **Apply orientation exactly once.** Resolve EXIF rotation/reflection for decoded files or CameraX rotation/crop metadata for raw captures. Ensure the inference image and displayed photo agree. A mirrored preview must not accidentally redefine the input transform.
4. **Resolve color and channels explicitly.** Convert camera YUV data if that capture path is used; do not copy YUV planes as three-channel image pixels. The proposed target is RGB, pending training confirmation. Alpha handling, color-space conversion, and HDR/tone-mapping behavior also need a defined policy and reference checks.
5. **Produce a 224 by 224 image.** Confirm whether training stretched, center-cropped, or padded images and which interpolation was used. These choices are **UNVERIFIED**. The scanner's decorative frame does not establish the model's crop policy. Do not silently choose a crop and claim training parity.
6. **Pack the float input.** For batch 1, write `[1, 224, 224, 3]` in NHWC order using the confirmed channel order and intensity range. With a direct `ByteBuffer`, use native byte order and reset its position before inference. Avoid duplicate normalization.
7. **Run inference off the main thread.** Serialize access to a shared interpreter, manage its lifecycle, and release buffers/resources. Runtime choice, dependency version, delegate behavior, memory use, and latency remain unverified. A CPU execution baseline must pass before comparing acceleration.
8. **Validate and deliver the result.** Check output structure and values, apply only a validated acceptance policy, and reject stale/cancelled work. Preserve the exact request identity throughout preprocessing, inference, and delivery.

## Result and team integration boundary

Reuse the current `AnalysisResult`, `ScanRequest`, and `ScanSubmission` contracts described in [INTEGRATION_CONTRACTS.md](INTEGRATION_CONTRACTS.md). Preserve `requestId`, `ScanMode`, and optional `rescanFoodId`. A rescan must not create a replacement food ID, and completion must not attach to a newer request.

For genuine inference results, set `isDemo = false` and supply only observed or independently verified fields. This model does not identify a produce name, fruit/vegetable category, or canonical `produceId`. Use an unidentified produce name, `ProduceCategory.Unknown`, and null `produceId` unless identity is confirmed through a separate catalogue/user-input path. Leave guidance absent unless reviewed guidance exists for that confirmed produce. Confidence must be null or finite within [0, 1].

Do not reuse `DemoResults` as inference output. Do not infer expiry, check-in dates, storage advice, nutrient quantities, or consumption safety from class scores. Never recommend consuming visibly spoiled produce.

Member 3's integration is pending on this branch. The previously inspected `origin/feature/database-reminders` snapshot `597665d` adds `ScanSubmission.scannedAt: Instant?`, exposes `FreshCheckViewModel.scanRequests`, and accepts results through `acceptAnalysis`. These APIs are not present in the current checkout and must be rechecked after team integration. The intended handoff includes the actual analysis-event `Instant`, not a parsed display label, photo EXIF date, or timestamp invented later at save time. Analysis acceptance permits display; only repository confirmation establishes persistence. This document changes none of those shared APIs.

## UNVERIFIED training details and release evidence

The repository assets and current documentation do not establish the following:

| Missing evidence | Required confirmation |
|---|---|
| Training/export source and provenance | Original code/notebook, dataset source, split policy, export configuration, and applicable licenses |
| Class-index mapping | Training class names/indices matched to sidecar labels and reference predictions |
| Pixel contract | Channel order, color space, external intensity range, and whether training duplicated rescaling |
| Spatial preprocessing | Orientation, crop/stretch/padding, interpolation, and alpha handling |
| Supported produce and capture conditions | Evaluated species, visible-condition definitions, lighting, occlusion, and single/multiple-item policy |
| Predictive performance | Held-out confusion matrix, per-class metrics, test-set size, and leakage checks |
| Abstention and unsupported inputs | Validation-derived thresholds/calibration and tests on non-produce, unsupported produce, and poor images |
| Android execution | Runtime tensor introspection, known-image parity, supported devices, latency, memory, and resource cleanup |

Before production use:

- Match asset hashes and runtime tensor details to this contract; record any deliberate changes.
- Obtain known-image fixtures with expected preprocessing tensors and output scores from the original pipeline. Mark synthetic test fixtures clearly and keep them out of production persistence.
- Test channel order, embedded-scaling endpoints, orientation/reflection, crop policy, corrupt images, and large images with camera and gallery paths.
- Execute the bundled model and compare reference scores within a documented tolerance; structural inspection alone is insufficient.
- Validate uncertainty behavior and unsupported-image limitations without presenting confidence as safety.
- Verify stale-request cancellation, timestamp preservation, durable image access, and Member 3's confirmed-save flow after integration.

No runtime inference, accuracy evaluation, Android build, or application test suite was run for this documentation task. Documentation checks cover asset hashes, the local integration-document link, and whitespace. No model, labels, shared configuration, UI, navigation, or database files were changed.

## Reference

The [official TensorFlow Lite FlatBuffer schema](https://github.com/tensorflow/tensorflow/blob/master/tensorflow/compiler/mlir/lite/schema/schema.fbs) defines tensor type and builtin operator codes used to interpret the binary. It does not supply this model's training configuration or establish any of the unverified details above.

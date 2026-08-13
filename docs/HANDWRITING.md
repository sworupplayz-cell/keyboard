# Handwriting recognition

Phase 3 added a finger/stylus canvas, stroke capture, undo, clear, cancel, confirm, and safe `InputConnection` insertion. Phase 32 adds a real preprocessing pipeline, a model-loading boundary, cancellable recognition jobs, password-field blocking, and suggestion-bridge hooks. **This build still does not recognize handwriting.** Confirm reports that the offline recognizer is not bundled and never changes existing text.

## Research

The goal was a local, offline recognizer with Nepali Devanagari as the first-class target. Candidates considered:

| Approach | Why it was considered | Why it was not shipped |
| --- | --- | --- |
| Google ML Kit Digital Ink | Production stroke recognizer, includes Devanagari in some catalogs | Language packs are **downloaded at runtime** (~20 MB each) plus a native runtime. Conflicts with no-INTERNET, no-download, and lightweight APK rules. Already rejected in Phase 3. |
| TensorFlow Lite / LiteRT CNN on DHCD | DHCD is 92k isolated Devanagari characters, CC BY 4.0, 32×32 grayscale. A quantized CNN can run CPU-only on low-end phones. | No ready, permissively licensed **TFLite weight file** is bundled here. Training requires the 76 MB dataset plus a TensorFlow toolchain that is **not in this sandbox**. Isolated-character models also cannot read words such as `नमस्ते` without a separate, unsolved segmentation step. |
| CRNN / CNN-RNN-CTC / Transformer line models | Needed for word-level handwriting (`काठमाडौं`, `विद्यालय`). | Public Nepali/Devanagari line models are research artifacts (PyTorch/PyLaia/Transkribus), often with restricted manuscript licenses, and are tens of megabytes. None are a drop-in Android asset. |
| ONNX Runtime Mobile | Alternative on-device runtime | Adds a large native dependency with no model to run. |
| Tesseract OCR | Offline OCR with a Devanagari tessdata pack | Designed for scanned pages, not live stylus strokes. Pack + native engine is too large for this IME. |
| Stroke-count or tiny template maps | Trivial to ship | Explicitly forbidden. That is a fake demo, not recognition. |
| Roman transliteration as a stand-in | Already in the IME | Converts typed Latin, not ink. |

### Selected approach

**Prepare the real pipeline now; do not pretend inference works.**

1. Normalize and resample strokes, then rasterize to a 32×32 grayscale grid (DHCD-compatible).
2. Look only for a locally bundled `handwriting_devanagari.tflite` / `handwriting_english.tflite`.
3. If the file is absent — as it is in this commit — report `RECOGNIZER_UNAVAILABLE`.
4. When a legally licensed quantized model can be committed later, drop it in `app/src/main/assets/handwriting/` and implement `BundledHandwritingInterpreter.infer` against that file. No second architecture is required.

### Model status

- **Name / source:** none bundled
- **License:** n/a
- **Size:** 0 bytes in the APK
- **Format expected later:** TFLite int8, 32×32×1 input
- **Runtime:** CPU-only if a model is added; no GPU requirement
- **Quantization:** planned int8; **UNMEASURED**
- **Cold-start / warm latency / RAM:** **UNMEASURED** (no model, no JDK/device in this sandbox)

## Pipeline

```
HandwritingCanvasView
        ↓ InkPoint / InkStroke
StrokeNormalizer + InkRasterizer
        ↓ PreparedHandwriting
HandwritingModelLoader (local assets only)
        ↓
BundledHandwritingInterpreter   ← empty until a model is bundled
        ↓ RecognitionResult
HandwritingInputState / HandwritingCandidatePolicy
        ↓ user tap
InputConnectionCommitter
        ↓
existing SuggestionEngine
```

`KeyboardService` stays the orchestrator. This is not a second typing, prediction, or touch engine.

## Stroke preprocessing

- Drop non-finite points and strokes shorter than 0.02 in unit space.
- Resample each stroke to 32 arc-length points and interpolate timestamps.
- Translate to origin and scale into a padded unit box while preserving aspect ratio (matras and shirorekha stay in relative position).
- Rasterize with a 3×3 stamp so a future CNN sees a stable glyph, independent of canvas size or density.

## Languages

The selected keyboard language remains authoritative. Recognition never auto-switches EN / नेपाली / Roman. Mixed ink such as `म school जान्छु` is **not** claimed: no sequence model is present.

## Candidates and prediction

Up to five model candidates are allowed by policy; the existing `HandwritingInputState` still shows at most three. Candidates are never invented to fill slots. The user must tap Confirm or a result chip. Accepted text is committed through `InputConnectionCommitter` and then offered to the existing `SuggestionEngine` as ordinary committed text. Nothing is silently rewritten.

## Privacy

- No INTERNET permission
- No network recognizer
- No runtime download
- No Logcat of strokes
- No persistent stroke store
- Password / PIN fields set status `BLOCKED` and do not run recognition
- Learning uses the existing bounded stores only after an explicit accept, and only when `EditorFieldPolicy` allows it

## Settings

Settings still say handwriting recognition is unavailable. There is no fake enable toggle.

## Phase 33 — shippability audit

Phase 33 re-checked whether a **real** offline Devanagari handwriting model can be bundled and run. Status: **`RECOGNITION_NOT_SHIPPABLE`**.

Additional candidates reviewed:

| Model | License | Verdict |
| --- | --- | --- |
| ML Kit Digital Ink | vendor terms + downloaded packs | Runtime download. Rejected. |
| DHCD custom CNN | dataset CC BY 4.0; no official weights | Isolated characters only; cannot train/convert here. |
| kaushu42/nepali-ocr `model.h5` | **no license** | Cannot redistribute. Isolated characters. |
| tulasiram58827/ocr_tflite | Apache-2.0 repo | Latin/captcha/scene OCR. Wrong script and task. |
| PaddleOCR PP-OCRv3 Devanagari ONNX | Apache-2.0, 8.6 MB rec | Printed/scene OCR, not digital ink. Needs ONNX Runtime + detector. Inference cannot be verified in this sandbox. Bundling it would mislabel printed OCR as handwriting. |
| IIIT-HW-Dev academic CRNN | research / dataset terms | No redistributable Android weights. |
| Tesseract Devanagari | Apache-2.0 | Printed page OCR + large native engine. |

Required artifact to finish the feature: a redistributable offline Devanagari **digital-ink or handwritten-word** TFLite/ONNX model with a documented input spec, plus a toolchain that can load it and verify at least one real handwritten word.

`ProductionIntegrationPolicy.handwritingRecognitionImplemented()` remains false.

## Dual-language framework

The Phase 33 framework now has:

- `HandwritingStrokeCollector` with begin/end, undo, clear, optional pressure
- `StrokeNormalizer` + reused `InkRasterizer` 224×224 buffer (Phase 32 still prepares 32×32)
- geometry-only `InkLanguage` detection (`ENGLISH` / `DEVANAGARI` / `UNKNOWN`)
- `HandwritingModelManager` for local `english.tflite` and `nepali.tflite`
- `HandwritingSession` reset on confirm, cancel, hide, and field change

Both model files are **Missing**. Recognition returns `HandwritingResult.UNAVAILABLE` and never invents characters. Settings → Appearance → Handwriting shows Auto language and Installed/Missing status only. There is no fake enable switch.

## Phase 34 English inference

`HandwritingModelManager.loadEnglishModel()` / `recognizeEnglish()` / `unloadEnglishModel()` implement a singleton interpreter and a 1×224×224×1 float32 packer. Token decoding is CTC over printable ASCII and runs only on real interpreter output. This sandbox still has no `english.tflite` and existing tests forbid adding a TensorFlow Gradle dependency without a model, so production inference stays unavailable.

## Future upgrade

1. Train or obtain a CC-BY / Apache-2.0 quantized Devanagari model.
2. Place it at `app/src/main/assets/handwriting/handwriting_devanagari.tflite`.
3. Implement `BundledHandwritingInterpreter.infer` with TensorFlow Lite (or LiteRT) **only after** that file exists.
4. Measure APK delta, RAM, and latency on a low-end device before calling recognition implemented.
5. Keep `ProductionIntegrationPolicy.handwritingRecognitionImplemented()` false until that measurement lands.

References:

- Android `HandwritingGesture`: https://developer.android.com/reference/android/view/inputmethod/HandwritingGesture
- ML Kit Digital Ink Recognition: https://developers.google.com/ml-kit/vision/digital-ink-recognition/android
- DHCD (CC BY 4.0): https://archive.ics.uci.edu/dataset/389/devanagari+handwritten+character+dataset

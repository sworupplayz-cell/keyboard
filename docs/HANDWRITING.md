# Phase 3 handwriting recognition status

Phase 3 includes a working finger/stylus canvas inside the real IME, stroke capture, undo, clear, cancel, confirm, result buttons, mode switching, and safe insertion of recognized Unicode through `InputConnection`.

## Why a recognizer is not bundled

A lightweight public Android API that converts arbitrary Nepali handwriting strokes to Unicode was not found. Android's public stylus handwriting APIs let an IME receive stylus events and perform editor gestures, but the IME remains responsible for recognizing handwriting.

Google ML Kit Digital Ink Recognition was evaluated and intentionally rejected for this lightweight, fully preloaded phase:

- ML Kit's current Android integration uses `com.google.mlkit:digital-ink-recognition` and dynamically downloaded language models.
- Google's documentation states that each language model requires about **20 MB** of device storage and must be downloaded before recognition.
- Published Maven information for the older 18.1.0 Android AAR reports about **11.4 MB** before its downloaded model; the runtime also contains ABI-specific native `libdigitalink.so` binaries. The exact current APK increase depends on ABI filtering.
- A fixed peak-RAM figure is not published and would require measurement on representative low-end devices after loading the model. Adding a native runtime plus a roughly 20 MB model without that profiling would conflict with this project's low-resource goal.
- A runtime model download conflicts with the requirement that the keyboard not download anything while it is being used.

No ML dependency, native library, model, internet permission, or model-download code was added.

## Measured size impact

Using the same Android 35 debug build process:

- Phase 2 APK: **713,350 bytes**
- Phase 3 APK: **725,638 bytes**
- Canvas UI, input state, and recognition interface increase: **12,288 bytes**

## Integration point

`NepaliHandwritingRecognizer` in `HandwritingRecognition.kt` is the small synchronous recognition contract. `HandwritingInputState` limits output to three distinct Devanagari candidates and inserts nothing when recognition is unavailable or uncertain. The shipped implementation is `UnavailableNepaliHandwritingRecognizer`, so Confirm reports the limitation without changing text.

A future recognizer should be bundled with the APK, require no account or network access, publish its APK/native/RAM cost, and be profiled before replacing the unavailable implementation.

References:

- Android `HandwritingGesture`: https://developer.android.com/reference/android/view/inputmethod/HandwritingGesture
- ML Kit Digital Ink Recognition: https://developers.google.com/ml-kit/vision/digital-ink-recognition/android

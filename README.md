# Simple Nepali Keyboard — Phase 4

A lightweight native Android input method (IME) built with Kotlin and `InputMethodService`. It is a real system keyboard, not an in-app keyboard simulation, and works completely offline.

## Features

- English lowercase and one-shot uppercase Shift
- Backspace, space, enter/editor action, punctuation, numbers, and symbols
- Nepali consonants, independent vowels, vowel signs, conjunct shortcuts, marks, and Devanagari digits
- Roman mode with an English QWERTY layout and offline Nepali conversion
- A compact suggestion row for the current Roman word
- Mode cycle: **EN → नेपाली → Roman → EN**
- `✍` handwriting mode with a compact finger/stylus canvas
- Handwriting stroke capture, undo, clear, cancel, confirm, result row, and safe Unicode insertion interface
- Direct return from handwriting to EN, नेपाली, or Roman
- Compact Unicode emoji panel with Recent, Smileys, People, Animals, Food, Objects, and Symbols
- Small on-device recent-emoji history with no image assets or downloads
- Dedicated ASCII number panel and expanded common-symbol panel
- A compact navigation row for EN, नेपाली, Roman, numbers, emoji, and handwriting
- Previous-layout return behavior for temporary panels
- Long-press a mode key to move to the next system keyboard
- Optional key sound and vibration
- Light or dark keyboard appearance
- No internet permission, account, cloud service, GIF, sticker, text collection, AI, or sentence prediction

The Roman dictionary is the tab-separated file at `app/src/main/res/raw/roman_nepali_dictionary.tsv`. Known words convert when a suggestion is tapped or when space/punctuation is entered. Unknown words remain in their original Roman spelling. A small rule-based transliterator provides optional suggestions without automatically replacing unknown text.

## Handwriting recognition limitation

The handwriting UI and offline recognition interface are complete, but this build deliberately does **not** bundle a Nepali recognition model. Android has no lightweight public Nepali stroke recognizer. The evaluated ML Kit option requires an approximately 20 MB language-model download and native runtime, which conflicts with the no-download and lightweight requirements. Confirm therefore reports that recognition is unavailable and never changes existing text. See [`docs/HANDWRITING.md`](docs/HANDWRITING.md) for the size assessment and future integration contract.

## Build

Requirements: JDK 17 and an Android SDK containing Android 35.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Enable the keyboard

1. Install the APK and open **Simple Nepali Keyboard**.
2. Tap **Enable keyboard**, then enable **Simple Nepali Keyboard** in Android's input-method settings.
3. Return to the app and tap **Choose keyboard**.
4. Select **Simple Nepali Keyboard**.
5. Open a text field in another app and type normally.

The IME service is declared in `AndroidManifest.xml` with the `android.permission.BIND_INPUT_METHOD` permission and `android.view.InputMethod` intent. Its English and Nepali system subtypes are declared in `res/xml/method.xml`; Roman and handwriting are local modes inside the IME.

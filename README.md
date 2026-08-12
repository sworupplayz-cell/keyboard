# Simple Nepali Keyboard — Phase 1

A lightweight native Android input method (IME) built with Kotlin and `InputMethodService`. It provides English QWERTY, Nepali Devanagari, a small symbol layout, and only local settings. It is a system keyboard, not an in-app keyboard simulation.

## Phase 1 features

- English lowercase and one-shot uppercase Shift
- Backspace, space, enter/editor action, punctuation, numbers, and symbols
- Nepali consonants, independent vowels, vowel signs, conjunct shortcuts, marks, and Devanagari digits
- English/Nepali switch on the keyboard (long-press the language key to move to the next system IME)
- Optional key sound and vibration
- Light or dark keyboard appearance
- No internet permission, account, cloud service, text collection, autocorrect, or suggestion engine

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

The IME service is declared in `AndroidManifest.xml` with the `android.permission.BIND_INPUT_METHOD` permission and `android.view.InputMethod` intent. Its English and Nepali subtypes are declared in `res/xml/method.xml`.

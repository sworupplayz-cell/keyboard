# Simple Nepali Keyboard — Phase 26

A lightweight native Android input method (IME) built with Kotlin and `InputMethodService`. It is a real system keyboard, not an in-app keyboard simulation, and works completely offline.

Phase 26 polishes everyday typing on the existing IME: faster consecutive taps, safer composing text, grapheme backspace, hyphen/contraction word boundaries, cursor-safe suggestion replacement, and password-field privacy. Phase 25 touch recognition and Phases 19–24 stay in place.

## Features

- English lowercase, one-shot Shift, and double-tap caps lock
- Backspace, space, enter/editor action, punctuation, numbers, and symbols
- Nepali consonants, independent vowels, vowel signs, conjunct shortcuts, marks, and Devanagari digits
- Roman mode with an English QWERTY layout, reusable phonetic rules, and offline Nepali conversion
- Large offline English, Nepali Devanagari, and Romanized Nepali vocabularies that can be edited as plain files
- Context-aware, frequency-weighted suggestions that also prefer recent and learned words
- Previous-word and two-word phrase prediction from a small offline seed plus bounded local pairs
- High-confidence emoji suggestions for clear keywords such as heart, happy, sad, fire, and football
- Conservative typo correction using nearby keys, vowel deletions, missing letters, and adjacent transpositions
- Sentence capitalization and punctuation spacing without auto-replacing uncertain text
- Rule-generated Devanagari for unknown names, slang, and made-up Roman words
- Alternate Roman spellings such as `cha/chha`, `aja/aaja`, and lengthened vowels
- Compact suggestions that include a keep-Roman option for mixed English/Nepali text
- Up to 250 explicitly selected Roman-to-Nepali mappings learned locally on the device
- Compact offline English and Nepali prefix suggestions with conservative typo alternatives
- Local learned English and Nepali words, ranked only after explicit suggestion selection
- Suggestions never auto-replace normal English or Nepali typing
- Named keyboard themes (Default Light/Dark, Blue, Green, Purple, High Contrast) plus System/Light/Dark
- Safe Paper / Ink / Midnight color overlays with unreadable combinations rejected
- Short / Normal / Tall keyboard height that actually changes the rendered IME
- Compact / Normal / Comfortable key size, spacing, and corner radius
- Optional number row, key shadows, borders, and pressed highlight
- Key sound on/off with Low / Medium / High system-click volume
- Haptic on/off with Light / Medium / Strong; long-press vibrates once
- Live mini-keyboard preview in settings
- Confirmed reset appearance, reset layout, and reset all settings without deleting learned words or clipboard
- One-handed Left / Center / Right padding; floating is reserved and labeled unavailable
- Confirmed clearing of learned English, Nepali, and Roman mappings without touching built-in vocabularies
- Mode cycle: **EN → नेपाली → Roman → EN**
- `✍` handwriting mode with a compact finger/stylus canvas
- Handwriting stroke capture, undo, clear, cancel, confirm, result row, and safe Unicode insertion interface
- Direct return from handwriting to EN, नेपाली, or Roman
- Offline Unicode emoji catalog with Smileys, People, Animals, Food, Travel, Activities, Objects, Symbols, and Flags
- Local emoji search, recent history, usage ranking, and skin-tone variants
- Number pad with operators plus a Latin/Devanagari digit switch
- Grouped symbol pages and a small recent-symbol history
- A compact toolbar above suggestions for emoji, clipboard, settings, and extra tools
- Local clipboard history with a 20-item cap, sensitive-text filtering, and no upload
- A compact navigation row for EN, नेपाली, Roman, numbers, emoji, handwriting, and settings
- Gboard-style rounded keys, blue Enter, language-labeled space bar, and a flat suggestion strip
- Key-press preview for letters and long-press alternate characters
- Responsive key heights, readable mode labels, visible active modes, and pressed-key feedback
- Clear contrast in both light and dark appearances
- Previous-layout return behavior for temporary panels
- Long-press a mode key to move to the next system keyboard
- Held backspace deletes one grapheme at a time after a short delay
- Contractions and hyphenated words stay one token; emails and URLs are not suggestion-replaced
- Suggestion taps replace only the current word, including when the cursor is inside it
- Password and PIN fields hide suggestions and never learn typed text
- Enter follows the target app's Search / Go / Send / Next / Done action
- Optional key sound and vibration
- System-default, light, or dark keyboard appearance
- No internet permission, account, cloud service, GIF, sticker, text collection, AI, or large language model

The expandable Roman vocabulary is the tab-separated file at `app/src/main/res/raw/roman_nepali_dictionary.tsv`. Vocabulary and learned mappings improve ranking, while the phonetic engine remains the fallback for every unknown Roman word. Known and unknown Romanized Nepali words convert on boundaries such as space, punctuation, or Enter. Common mixed-language terms such as `school` remain Roman by default, and every word also offers its original Roman spelling as a compact suggestion.

English suggestions use `app/src/main/res/raw/english_vocabulary.txt`. Nepali suggestions use `app/src/main/res/raw/nepali_vocabulary.txt` plus Nepali values from the Roman dictionary. Both are prefix-indexed, retain unusual input unchanged, and only replace a word after the user taps a suggestion.

See [`docs/LANGUAGE.md`](docs/LANGUAGE.md) for offline intelligence, [`docs/QUALITY.md`](docs/QUALITY.md) for Phase 21 typing quality, [`docs/APPEARANCE.md`](docs/APPEARANCE.md) for themes and customization, [`docs/UX.md`](docs/UX.md) for interaction and Back behavior, [`docs/UI.md`](docs/UI.md) for the visual contract, [`docs/VOCABULARY.md`](docs/VOCABULARY.md) for the offline dictionaries, [`docs/SUGGESTIONS.md`](docs/SUGGESTIONS.md) for ranking, [`docs/TOOLBAR.md`](docs/TOOLBAR.md) and [`docs/PERSONALIZATION.md`](docs/PERSONALIZATION.md) for the tools row, [`docs/ROMAN_ENGINE.md`](docs/ROMAN_ENGINE.md) for the Roman engine, [`docs/EMOJI.md`](docs/EMOJI.md) for emoji panels, and [`docs/SMART_TYPING.md`](docs/SMART_TYPING.md) for Phase 14 behavior.

## Handwriting recognition limitation

The handwriting UI and offline recognition interface are complete, but this build deliberately does **not** bundle a Nepali recognition model. Android has no lightweight public Nepali stroke recognizer. The evaluated ML Kit option requires an approximately 20 MB language-model download and native runtime, which conflicts with the no-download and lightweight requirements. Confirm therefore reports that recognition is unavailable and never changes existing text. See [`docs/HANDWRITING.md`](docs/HANDWRITING.md) for the size assessment and future integration contract.

## Floating keyboard limitation

Floating is a stored presentation value only. This build does not create a `WindowManager` overlay IME, so settings describe it as unavailable and do not expose a working toggle.

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

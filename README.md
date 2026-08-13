# Simple Nepali Keyboard — Phase 15

A lightweight native Android input method (IME) built with Kotlin and `InputMethodService`. It is a real system keyboard, not an in-app keyboard simulation, and works completely offline.

Phase 15 keeps the Gboard-style chrome and all earlier typing engines. The focus is a compact offline suggestion engine: prefix completions, phrase prediction, mixed English/Nepali ranking, conservative typos, local learning, and high-confidence emoji suggestions. The strip still shows at most three items and never auto-replaces unknown text.

## Features

- English lowercase and one-shot uppercase Shift
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
- A simple local settings screen for default mode, System/Light/Dark appearance, suggestions, learning, compact number row, brief feedback, and Small/Normal/Large height
- Confirmed clearing of learned English, Nepali, and Roman mappings without touching built-in vocabularies
- Mode cycle: **EN → नेपाली → Roman → EN**
- `✍` handwriting mode with a compact finger/stylus canvas
- Handwriting stroke capture, undo, clear, cancel, confirm, result row, and safe Unicode insertion interface
- Direct return from handwriting to EN, नेपाली, or Roman
- Offline Unicode emoji catalog with Smileys, People, Animals, Food, Travel, Activities, Objects, Symbols, and Flags
- Local emoji search, recent history, usage ranking, and skin-tone variants
- Number pad with operators plus a Latin/Devanagari digit switch
- Grouped symbol pages and a small recent-symbol history
- A compact navigation row for EN, नेपाली, Roman, numbers, emoji, handwriting, and settings
- Gboard-style rounded keys, blue Enter, language-labeled space bar, and a flat suggestion strip
- Key-press preview for letters and long-press alternate characters
- Responsive key heights, readable mode labels, visible active modes, and pressed-key feedback
- Clear contrast in both light and dark appearances
- Previous-layout return behavior for temporary panels
- Long-press a mode key to move to the next system keyboard
- Optional key sound and vibration
- System-default, light, or dark keyboard appearance
- No internet permission, account, cloud service, GIF, sticker, text collection, AI, or large language model

The expandable Roman vocabulary is the tab-separated file at `app/src/main/res/raw/roman_nepali_dictionary.tsv`. Vocabulary and learned mappings improve ranking, while the phonetic engine remains the fallback for every unknown Roman word. Known and unknown Romanized Nepali words convert on boundaries such as space, punctuation, or Enter. Common mixed-language terms such as `school` remain Roman by default, and every word also offers its original Roman spelling as a compact suggestion.

English suggestions use `app/src/main/res/raw/english_vocabulary.txt`. Nepali suggestions use `app/src/main/res/raw/nepali_vocabulary.txt` plus Nepali values from the Roman dictionary. Both are prefix-indexed, retain unusual input unchanged, and only replace a word after the user taps a suggestion.

See [`docs/UI.md`](docs/UI.md) for the Phase A visual contract, [`docs/VOCABULARY.md`](docs/VOCABULARY.md) for the offline dictionaries, [`docs/ROMAN_ENGINE.md`](docs/ROMAN_ENGINE.md) for the Roman engine, [`docs/EMOJI.md`](docs/EMOJI.md) for emoji panels, and [`docs/SMART_TYPING.md`](docs/SMART_TYPING.md) for Phase 14 behavior.

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

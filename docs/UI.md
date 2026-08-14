# Phase A Gboard-style chrome

Phase A restyles the existing IME so it reads like Gboard. Phase 20 keeps that contract and routes every surface through `AppearanceCatalog` plus `KeyboardThemeTokens`. It does not add cloud services, change committed text, or replace EN / नेपाली / Roman typing.

## Visual contract

- Default light and dark palettes follow Gboard: pale gray board, white letter keys, darker modifier keys, blue Enter.
- Named themes (Blue, Green, Purple, High Contrast) and Paper / Ink / Midnight overlays reuse the same token roles.
- Keys use the selected corner radius, optional 1 dp contact shadow, optional border, and a fast pressed-state fade.
- The suggestion strip sits above the mode toolbar. Suggestions are plain labels with thin dividers, not chips.
- The space bar shows the current language name. Letter keys show a preview balloon on press. Emoji keys do not.
- Long-pressing a letter or punctuation key opens a compact alternate row. Mode keys still long-press to the next system keyboard.
- A settings gear on the suggestion strip and toolbar opens the existing local settings screen, which now includes a live mini-keyboard preview.

## What stays the same

All previous typing paths stay in place: English, Nepali consonants/vowels, Roman conversion, suggestions, learned words, number/symbol panels, emoji, handwriting, and offline-only operation. See [`APPEARANCE.md`](APPEARANCE.md) for the Phase 20 controls.

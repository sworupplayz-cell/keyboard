# Phase A Gboard-style chrome

Phase A restyles the existing IME so it reads like Gboard. It does not add cloud services, change committed text, or replace EN / नेपाली / Roman typing.

## Visual contract

- Light and dark palettes follow Gboard: pale gray board, white letter keys, darker modifier keys, blue Enter, no heavy key borders.
- Keys are 6 dp rounded rectangles with a 1 dp contact shadow and a slightly darker pressed fill.
- The suggestion strip sits above the mode toolbar. Suggestions are plain labels with thin dividers, not chips.
- The space bar shows the current language name. Letter keys show a preview balloon on press.
- Long-pressing a letter or punctuation key opens a compact alternate row. Mode keys still long-press to the next system keyboard.
- A settings gear on the suggestion strip and toolbar opens the existing local settings screen.

## What stays the same

All previous typing paths stay in place: English, Nepali consonants/vowels, Roman conversion, suggestions, learned words, number/symbol panels, emoji, handwriting, height/appearance settings, and offline-only operation.

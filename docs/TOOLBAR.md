# Toolbar and local clipboard

The compact tools row sits above suggestions. The letter layout and existing panels stay as they are.

## Architecture

- `ToolbarConfiguration` persists order and enabled tools by enum ID
- `ToolbarController` owns collapsed/expanded state and overflow
- `ClipboardRepository` / `ClipboardPolicy` own the local history
- `LanguageSwitcher` owns tap-cycle and long-press picker labels
- `KeyboardService` only draws the row and routes taps

## Default collapsed tools

`😊` emoji · `📋` clipboard · `EN`/`ने`/`Ro` language · `⚙` settings · `⋯` more

Users can enable Numbers, Symbols, and Handwriting on the strip, or leave them under More.

## Expanded tools

Overflow items plus numbers, symbols, handwriting, EN / नेपाली / Roman, and collapse.

## Clipboard

History is local only. Limit: **20** snippets, **500** characters each.

Not stored: empty text, passwords, bearer/API tokens, JWT-like strings, huge pastes, binary blobs.

Tap inserts raw Unicode after finishing the composing word. Roman conversion is not applied to clipboard content.

## Settings

- Always show toolbar (default ON)
- Auto-collapse (default ON)
- Customize / restore defaults
- Clipboard history (default ON)
- Clear clipboard

## Back

A popup or language picker closes first. Expanded More collapses next. An open emoji, clipboard, number, symbol, or handwriting panel then returns to the previous keyboard. Letters and vowels leave Back to Android.

## Privacy

No internet permission, upload, account, analytics, or cloud sync.

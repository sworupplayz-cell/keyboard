# Toolbar and local clipboard

Phase 16 adds a compact tools row above suggestions. The letter layout, suggestion strip, and existing panels stay as they are.

## Architecture

- `ToolbarController` owns collapsed/expanded state and the button list.
- `ClipboardRepository` and `ClipboardPolicy` own the local history.
- `KeyboardService` only draws the row and routes taps to the existing emoji, number, symbol, handwriting, language, and settings paths.

## Collapsed tools

`😊` emoji · `📋` clipboard · `⚙` settings · `⋯` more

## Expanded tools

`123` numbers · `#+=` symbols · `✍` handwriting · `EN` / `नेपाली` / `Roman` · clipboard · settings · collapse

Emoji is omitted from the expanded row because it is already on the collapsed strip. Mode chips stay in sync with the live keyboard language.

## Clipboard

History is stored only in local preferences. Limit: **20** snippets, **500** characters each.

Not stored:

- empty text
- passwords / passcodes
- bearer tokens, API keys, JWT-like strings
- very long pastes
- binary / control-character blobs

Tap inserts the raw Unicode text after finishing the current composing word. Roman conversion is not applied to clipboard content. Delete removes one item. Clear removes the local list.

## Settings

- Toolbar: default ON
- Clipboard history: default ON
- Clear clipboard history

Turning clipboard history off stops new saves. Existing snippets can still be cleared from settings.

## Privacy

No internet permission, upload, account, analytics, or cloud sync. Clipboard text never leaves the device.

## Height

The toolbar is 28–32 dp and shrinks on 320-wide and landscape screens so letter keys stay usable.

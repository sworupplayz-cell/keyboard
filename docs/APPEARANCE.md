# Appearance and keyboard customization

Phase 20 adds real, persisted appearance controls on top of the existing IME. Typing engines, vocabularies, suggestions, and clipboard behavior are unchanged. Everything stays offline.

## Theme system

`AppearanceCatalog` is the single palette source. `KeyboardService` caches the resolved `KeyboardPalette` when preferences are read and reuses it for keys, chrome, suggestions, and panels. Individual keystrokes do not rebuild the theme.

Visual themes:

- `FOLLOW_APPEARANCE` — Default Light or Default Dark from the System / Light / Dark setting
- `DEFAULT_LIGHT` / `DEFAULT_DARK` — fixed Gboard-like palettes
- `BLUE`, `GREEN`, `PURPLE` — tinted boards that still keep white or dark letter keys
- `HIGH_CONTRAST` — black/white board with a stronger Enter accent

Safe color overlays (`ColorPreset`) can replace the board and key surfaces:

- Theme colors
- Paper
- Ink
- Midnight

There is no free-form color picker. `AppearanceCatalog.sanitize` rejects unreadable text/key, text/board, Enter, and popup combinations by swapping in a contrasting label.

Reset appearance restores theme, overlay, height, density, spacing, corners, number row, shadows, borders, and pressed highlight. Learned words are not touched.

## Height, density, and spacing

`KeyboardHeight` is still `SMALL` / `NORMAL` / `LARGE`. Settings labels are **Short / Normal / Tall**. The values feed `KeyboardUiMetrics`, so letter rows, the compact number row, emoji, clipboard, handwriting, suggestions, toolbar, and navigation all change height.

`KeyDensity` adds a bounded delta (`-2` / `0` / `+3` dp) on top of height. Keys never go below 40 dp. Existing default height formulas stay the same when density is Normal.

`KeySpacing` changes the key gap (`1–3` dp). Compact never drops below 1 dp. `KeyCornerStyle` is Tight (3 dp), Normal (6 dp), or Round (10 dp).

The existing number-row switch still prepends a compact digit row on English/Roman and Nepali letter layouts.

## Key chrome

Shadows, borders, and the pressed highlight are independent switches. They are applied in `KeyboardTheme.keyBackground` and `KeyboardKeyView.bind`. A shadow of 0 px draws a flat key. Borders use the palette divider. Pressed highlight can be turned off without changing tap behavior.

The space bar still shows **English**, **नेपाली**, or **Roman**. Enter uses the accent fill. Backspace, Shift, caps lock, and the language key use the modifier fill. Active Shift/caps uses the accent.

Letter and Devanagari keys still show a preview balloon. Emoji keys do not. The preview view is not clickable and `dispatchTouchEvent` returns false so it cannot steal neighboring taps.

## Sound and haptics

Key sound and vibration remain off by default. When sound is on, only Android system key-click effects play, at Low / Medium / High volume. When vibration is on, taps and a single long-press use Light (12 ms) / Medium (18 ms) / Strong (28 ms). Toolbar chrome and panel navigation stay silent. There is no vibration storm on a held key.

## Layout

One-handed Left / Center / Right still apply padding only. Screens narrower than 360 dp stay full width. Floating remains a stored `KeyboardPresentationMode` value but is not drawn and has no working toggle. Settings explains that it is unavailable.

## Settings preview and reset

The settings screen shows a mini keyboard (`KeyboardPreviewView`) that updates as soon as a control changes. It is display-only and never commits text.

Sections: **APPEARANCE**, **SOUND & HAPTICS**, **LAYOUT**, **LANGUAGES**, **TYPING**, **SUGGESTIONS**, **EMOJI**, **CLIPBOARD**, **PRIVACY**, **ABOUT**.

- Reset appearance — theme and visual metrics
- Reset layout — one-handed mode and toolbar
- Reset all settings — every preference key in `KeyboardPreferences.ALL_SETTING_KEYS`

Reset all does **not** delete learned words, recents, context pairs, emoji usage, or clipboard history.

## Persistence

New keys live in the existing `keyboard_preferences` file:

- `keyboard_visual_theme`
- `keyboard_color_preset`
- `keyboard_key_density`
- `keyboard_key_spacing`
- `keyboard_key_corner`
- `keyboard_key_shadows`
- `keyboard_key_borders`
- `keyboard_pressed_highlight`
- `key_sound_volume`
- `key_haptic_strength`

Unknown stored values fall back to the previous defaults so older installs keep working.

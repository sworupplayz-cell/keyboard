# Keyboard UX

Phase 18 polishes interaction, navigation, and layout. Typing engines, vocabularies, and the Gboard-style letter layout stay as they are.

## Interaction model

- Letter, number, and symbol keys use a fast pressed-state fade (`KeyboardTheme.PRESS_FADE_MS` / `RELEASE_FADE_MS`). Only the key drawable changes; the layout is not rebuilt per press.
- Character keys show a preview balloon with the inserted glyph, including Devanagari letters and matras. Emoji keys do not show a character preview.
- The preview is visual only (`dispatchTouchEvent` returns false) so it cannot block neighboring keys. It dismisses on lift, cancel, Back, or a layout change.
- Long-press still offers the existing accent, punctuation, Nepali, and symbol alternates. Near-edge menus are clamped inside the keyboard window. Tap outside or Back dismisses the chooser without inserting.

## Toolbar and panels

Default collapsed tools: Emoji, Clipboard, Language, Settings, More.

- Toolbar taps use chrome feedback (no key-click sound or haptic).
- A short activation guard ignores a second tap on the same tool.
- Opening a panel that is already visible is a no-op, so panels are not stacked.
- Previous letter/vowel layout is remembered and restored when the panel closes.

## Language picker

Toolbar language **tap** cycles EN → नेपाली → Roman. **Long-press** opens a picker with those three labels and the current mode selected.

The space-row language key still long-presses to the **system IME switcher**. The two paths stay separate.

## Back behavior

`PanelNavigation` implements the hierarchy:

1. Popup / preview / language picker → close overlay
2. Toolbar More expanded → collapse More
3. Emoji, clipboard, numbers, symbols, or handwriting → previous keyboard
4. Letters / vowels → normal Android IME Back

More is treated as chrome overlay, so it still collapses before a panel underneath. That matches the existing `ToolbarController.consumeBack` contract.

## One-handed and height

- Off / Left / Center / Right insets come from `OneHandedLayoutPolicy`.
- Screens narrower than 360 dp stay full width so keys are not clipped.
- Floating remains a stored preference only; it is not drawn.
- Short / Normal / Tall height (`KeyboardHeight.SMALL` / `NORMAL` / `LARGE`) scales letter, number, symbol, emoji, clipboard, handwriting, suggestion, toolbar, and navigation rows through `KeyboardUiMetrics`. Compact / Comfortable density adds a bounded delta on top.
- Sound volume and haptic strength are local only. Disabled means completely off. Long-press vibrates once.

## Accessibility and feedback

- Keys, toolbar tools, suggestions, language options, and panel controls have spoken labels.
- Practical minimum touch is 40 dp for chrome, suggestions, and picker options. Compact letter rows keep their existing key heights.
- Sound and vibration are completely off when disabled. Toolbar and panel navigation do not play a keyboard click or spam haptics. Long-press vibrates once.

## Performance

- Vocabularies, the Roman converter, and the emoji catalog stay lazy and in-memory.
- Keystrokes update the suggestion strip only. Identical suggestion lists are not redrawn.
- Suggestion generation, clipboard, and learning remain bounded.
- There is no internet permission and no online lookup.

# Personalization

Phase 17 adds toolbar customization, a dedicated language switch, one-handed layout, and organized settings. Typing engines are unchanged.

## Toolbar

`ToolbarConfiguration` stores tool IDs, not view positions.

Default collapsed order:

1. Emoji
2. Clipboard
3. Language
4. Settings
5. More

Preference keys:

- `toolbar_enabled` — always show the strip
- `toolbar_order` — comma-separated `ToolbarAction` names
- `toolbar_enabled_items` — which of those IDs are visible
- `toolbar_auto_collapse` — collapse More after opening a panel
- `language_button_enabled` — include the language chip

On widths under 340 dp the strip shows at most four tools. Extra items move under More.

## Language switch

Tap cycles **EN → नेपाली → Roman**. Long-press opens a picker with those three labels. The space-row language key still long-presses to the system IME switcher.

## Presentation

`KeyboardPresentationMode`:

- `NORMAL` — full width
- `ONE_HANDED` — 82% width, aligned left / center / right
- `FLOATING` — stored only; not applied (no WindowManager overlay)

One-handed mode is padding-only. Suggestion, toolbar, and letter rows share the same width.

## Privacy

Still offline. `clearLocalData` removes learned words, recents, context pairs, clipboard history, and emoji usage. Built-in dictionaries stay.

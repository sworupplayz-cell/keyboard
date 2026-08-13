# Personalization

Phase 19 keeps toolbar customization and one-handed layout, and organizes settings into Appearance, Languages, Typing, Suggestions, Emoji, Clipboard, Toolbar, Personalization, Privacy, and About. Typing engines are unchanged.

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
- `FLOATING` — stored only; not applied (no WindowManager overlay). Settings labels it unavailable and does not show a working toggle.

One-handed mode is padding-only. Suggestion, toolbar, and letter rows share the same width. Reset layout restores one-handed mode and the toolbar. Reset all settings does not delete learned words or clipboard history.

## Prediction personalization

Phase 23 keeps learning on-device and bounded.

- Unigrams live in `LearnedWordStore` (usage count + recency). Frequent words such as `help` can outrank `hello` for `hel` without hiding the other useful completions.
- Accepted suggestions also write a previous-word and previous-two-word pair into `ContextModel` / `PhrasePredictor`.
- Recency decays ranking of old learned words. Generic vocabulary is never deleted just because it has not been used lately.
- Score caps prevent personalization from erasing dictionary frequency or exact matches.

Short function words such as `I` and `ma` may participate in phrase pairs, but they are still not stored as standalone learned vocabulary.

## Privacy

Still offline. The personal dictionary learns selected or twice-finished words only. Passwords, tokens, URLs, credit-card-like numbers, and garbage are rejected. Typed text is never written to Logcat. `clearLocalData` removes learned words, recents, context pairs, learned phrases, clipboard history, and emoji usage. Built-in dictionaries stay.

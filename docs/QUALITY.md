# Core keyboard quality

Phase 21 tightens the existing IME so everyday typing feels closer to a production keyboard. Phase 25 adds adaptive hit-testing on that same path. Phase 26 polishes the real typing pipeline. Phase 28 keeps that same path and sends Roman candidates through the same `SuggestionRanker` as English and Nepali. It does not replace English, Nepali, or Roman engines, and it does not change the Gboard-style appearance system.

## Touch

`KeyTouchPolicy` keeps the visual key the same size and lets the view fill its cell. Edge keys use a smaller outer inset so they stay reachable. Phase 25 adds `TouchCalibration`, `TouchGeometryPolicy`, `TouchTrajectory`, `AdaptiveHitboxPolicy`, and `TouchRecognitionState` behind that policy. Logical hitboxes grow slightly toward neighbors; a centered press still wins. A tiny drift is ignored. A clear slide that *started on a boundary* may resolve to the neighbor and update the preview; ordinary typing is not a swipe keyboard.

The key that received `ACTION_DOWN` owns the pointer. A second finger cannot steal it. Sliding far enough without a neighbor target cancels preview, backspace repeat, and the click. Long-press alternates still require a stable hold of 420 ms on the same key. Letter keys ignore bounces shorter than 32 ms. Toolbar chrome still uses the longer activation guard.

Neighbor-choice weights are local, bounded, and store only short key ids. They are never typed sentences, passwords, URLs, or clipboard text. `clearLocalData` removes them.

Feedback plays on press. Character keys still commit on lift unless the gesture was cancelled. Backspace commits on press so held delete can start immediately. `ACTION_MOVE` only runs geometry/policy math; it does not rebuild the keyboard.

## Backspace

A single tap still deletes one grapheme (`GraphemeBackspace`): Devanagari clusters, combining marks, emoji, ZWJ sequences, flags, and skin tones. Holding backspace waits 400 ms, then repeats one grapheme at a time. The interval shortens after several repeats. `ACTION_UP`, `CANCEL`, leaving the key, hiding the IME, or destroying the service stop the `Handler` callback.

The composing-word tracker uses the same grapheme rule so suggestions stay aligned with the editor.

## Shift and Enter

English is still one-shot shift, then double-tap caps lock. Punctuation, space, and backspace do not consume one-shot shift. Nepali never capitalizes. Roman stays one-shot only. After a letter consumes one-shot shift, letter labels refresh in place instead of rebuilding the whole keyboard.

Enter follows the target field's `EditorInfo` action: Search, Go, Send, Next, Done, or a newline when the app asks for none. The key label and spoken name match that action. The space bar still shows English / नेपाली / Roman.

## Suggestions and typos

The strip still shows at most three candidates, with the strongest in the center. Ranking prefers exact prefixes, then context, learned/recent words, frequency, and only then high-confidence typos. Unknown text is never auto-replaced. Extra conservative typo shapes: nearby keys, missing/extra letters, transpositions, repeats, and a small common-mistake list.

## Roman Nepali and mixed language

`RomanSpellingNormalizer` now also maps `ee/i`, `oo/u`, `ii/ee`, `sh/s`, `ph/f`, `ny/n`, and informal `chu/chhu` endings. Mixed sentences stay in the user-selected mode:

- `ma school jaanchu` → `म school जान्छु`
- `I am ghar` → `I am घर`
- `Nepali is awesome` stays English
- `today ma school jaanchu` → `today म school जान्छु`
- `mero phone good cha` → `मेरो phone good छ`

## Phase 26 typing pipeline

`CoreTypingPolicy` reuses the Phase 25 bounce window so alternating keys and fast space/punctuation are kept, while a 32 ms same-key bounce still drops accidental repeats. `TypingGeneration` cancels held-backspace ticks when the field, cursor, or language changes.

`WordBoundaryPolicy` treats apostrophes and hyphens as word characters, so `can't` and `mother-in-law` stay one token. Emails, URLs, mentions, and hashtags are still protected whole tokens and are never suggestion-replaced. `SuggestionSelectionPlan` can now look after the cursor so `I am go|ing` + *going* becomes `I am going`, not `I am goinging`.

Opening numbers, symbols, emoji, clipboard, or handwriting finishes composing first. Language switches commit the in-progress Roman or direct word and never rewrite already committed text. Password and PIN fields hide suggestions and skip learning. Shift still refreshes letter labels in place.

## Punctuation and screens

Comma, period, `?`, `!`, `:`, `;`, `%`, danda, quotes, and brackets still attach conservatively. URLs, emails, mentions, hashtags, and decimals are left alone.

Narrow phones still skip one-handed gutters. Letter text is capped under large font scales so keys are not enlarged just for accessibility. Existing default height formulas are unchanged.

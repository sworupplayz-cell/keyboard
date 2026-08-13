# Smart typing behavior

Phase 14 adds everyday keyboard polish without changing the Gboard-style letter layout.

## Punctuation

When **Smart punctuation** is on (default):

- A space before `, . ! ? : ; % ।` is removed.
- A space is added after those marks.
- URLs, emails, `@mentions`, `#hashtags`, numbers, and decimals are left alone.

Turn the setting off to insert punctuation exactly as typed.

## Double-space period

When **Double-space period** is on (default), two spaces within 450 ms become:

- English / Roman: `. `
- Nepali: `। `

It does not fire after a URL, email, number, or existing punctuation.

## Capitalization

When **Auto capitalization** is on (default), English letters are capitalized:

- at the start of a field
- after `. ! ? ।`
- after Enter / a newline

Romanized Nepali is not auto-capitalized. Usernames, hashtags, URLs, and already-capitalized text are skipped.

## Backspace

If there is no composing word, backspace deletes one Unicode grapheme: Devanagari clusters, combining marks, emoji, ZWJ sequences, flags, and skin-tone modifiers.

## Suggestions and learning

A tapped suggestion replaces only the current word and keeps the typed capitalization when reasonable. Obvious garbage (`aa`, `@@@`, URLs) is not learned. Unknown text is never auto-replaced.

## Mixed English + Nepali

Roman mode still converts `ma school jaanchu` to `म school जान्छु` and `I am ghar` to `I am घर`. A capitalized English sentence such as `Nepali is awesome` stays English. Unknown Roman words still go through the phonetic engine.

## Settings

- Smart punctuation
- Double-space period
- Auto capitalization
- Emoji recents
- Clear learned words
- Clear emoji recents

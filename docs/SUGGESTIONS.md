# Gboard-style suggestions

Phase 19 keeps the existing Gboard-style letter layout and ranks at most **three** offline suggestions. The strip shows the strongest candidate in the **center** slot.

## Ranking

`SuggestionEngine` combines, in order of strength:

1. Exact typed word
2. Explicitly learned words (suggestion taps, or the same unknown word finished twice)
3. Recently used words
4. Previous-word and previous-two-word phrases
5. Dictionary prefix matches, ordered by file frequency
6. A few everyday stem completions that are not strict prefixes (`goo` → going)
7. Conservative typos
8. The typed word itself, so unknown text is never discarded

Emoji candidates are scored separately and only occupy a strip slot when the keyword is exact and high-confidence.

## Phrase prediction

`PhrasePredictor` stores a small seed of everyday pairs/triples plus at most 240 locally learned continuations.

Examples:

- `good` → morning, night, luck
- `how are` → you
- `thank` → you
- `मलाई` → मन, नेपाली, मन पर्छ
- `तिमीलाई` → कस्तो छ
- `I am` → fine, going, घर
- `ma` / `ma school` → घर, जान्छु, school / jaanchu

This is not a language model. Unknown or low-confidence input produces no forced phrase.

## Local learning

Words are learned only when:

- the user taps a suggestion, or
- the same unknown word is finished at least twice, or
- the user repeatedly chooses the same Roman → Nepali mapping

They are **not** learned when they look like garbage, a single repeated character, a URL, an email, a `@mention`, a `#hashtag`, or a temporary numeric token. Stores stay bounded (250 learned words, 60 recents, 400 context pairs, 240 phrases).

## Romanized Nepali

Roman mode still uses the Phase 12 engine. Dictionary hits such as `ma`, `mal`, `tim`, `ghar`, and `jaan` produce compact Devanagari suggestions. Unknown Roman text still goes through the phonetic fallback, so `zorpa` is converted rather than rejected. The original Roman spelling remains available as a compact option when there is room.

## Mixed language

English, Nepali, and Romanized Nepali can sit in the same sentence.

- `ma school jaanchu` → `म school जान्छु`
- `I am ghar` → `I am घर`
- `Nepali is awesome` stays English because the first word is capitalized English

A word is never rewritten into the other language just because the rest of the sentence converted.

## Emoji suggestions

Clear English keywords may add one emoji:

- heart → ❤
- happy → 😊
- sad → 😢
- fire → 🔥
- football → ⚽
- laugh → 😂
- birthday → 🎂

Short or ambiguous prefixes such as `he` never show emoji. At most one emoji occupies a strip slot.

## Unknown words

Typed text is never auto-replaced. Names (`Sworup`), nonsense (`blablabla`, `zorpa`), and tokens such as URLs, emails, and hashtags stay exactly as entered unless the user taps a suggestion.

## Performance

Dictionaries are prefix-indexed and loaded lazily. Candidate lists are bounded. There is no network lookup and no large model.

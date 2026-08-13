# Gboard-style suggestions

The strip still ranks at most **three** offline suggestions. The strongest candidate occupies the **center** slot.

## Ranking

`SuggestionEngine` is the only facade. It runs one pipeline (`PredictionPipeline` + `SuggestionRanker`):

1. Exact typed word
2. Strong previous 2–3 word context
3. Explicitly learned words (suggestion taps, or the same unknown word finished twice)
4. Previous-word phrases
5. Dictionary prefix matches, ordered by file frequency
6. Morphology relatives such as play/playing or घर/घरमा
7. Everyday stem completions that are not strict prefixes (`goo` → going)
8. Conservative contractions (`cant` → can't) and typos
9. The typed word itself, so unknown text is never discarded

Emoji candidates are scored separately and only occupy a strip slot when the keyword is exact and high-confidence. Maximum one emoji.

This is a deterministic offline ranker. It is not a cloud model.

## Phrase prediction

`PhrasePredictor` stores a small seed of everyday pairs/triples plus at most 240 locally learned continuations. `ContextModel` stores at most 400 pairs. Both look at the previous one, two, or three words.

Examples:

- `good` → morning, night, luck
- `good morning` → everyone, guys
- `how are` → you
- `I am` → going, here, fine
- `मलाई` → मन, नेपाली, थाहा
- `मलाई मन` → पर्छ
- `तिमी कहाँ` → छौ, जान्छौ
- `thank you` → for, very
- `see you` → soon, tomorrow
- `ma` / `ma school` → घर, जान्छु, school / jaanchu
- `ma ghar` → jaanchu
- `ma college` → jaanchu

Unknown or low-confidence input produces no forced phrase.

## Local learning

Words are learned only when:

- the user taps a suggestion, or
- the same unknown word is finished at least twice, or
- the user repeatedly chooses the same Roman → Nepali mapping

Accepted suggestions also update previous-word and previous-two-word context. They are **not** learned when they look like garbage, a single repeated character, a URL, an email, a `@mention`, a `#hashtag`, a credit-card-like number, or a temporary numeric token. Stores stay bounded (250 learned words, 60 recents, 400 context pairs, 240 phrases). Recent usage is weighted above ancient usage; generic vocabulary stays as the fallback.

## Romanized Nepali

Roman mode still uses the Phase 12 engine. Dictionary hits such as `ma`, `mal`, `tim`, `ghar`, and `jaan` produce compact Devanagari suggestions, including useful relatives (`घर` / `घरमा`, `जान` / `जान्छु`). Unknown Roman text still goes through the phonetic fallback, so `zorpa` is converted rather than rejected. Playful spellings collapse to one conceptual candidate.

## Mixed language

English, Nepali, and Romanized Nepali can sit in the same sentence.

- `ma school jaanchu` → `म school जान्छु`
- `I am ghar` → `I am घर`
- `Nepali is awesome` stays English because the first word is capitalized English

A word is never rewritten into the other language just because the rest of the sentence converted. Capitalized place names such as `Kathmandu` stay Latin inside a Roman sentence. The selected mode stays where the user put it.

Near-duplicate English forms (`hello` / `hellos` / `hello's`) collapse to one slot so the bar can keep useful neighbors such as `help` and `he'll`.

## Emoji suggestions

Clear English keywords may add one emoji:

- heart / love → ❤
- happy → 😊
- sad → 😢
- fire → 🔥
- football → ⚽
- laugh → 😂
- birthday → 🎂
- party → 🎉

Short or ambiguous prefixes such as `he` never show emoji. At most one emoji occupies a strip slot. Empty input can reuse the previous word as context (`happy ` → 😊).

## Unknown words

Typed text is never auto-replaced. Names (`Sworup`), nonsense (`blablabla`, `zorpa`), and tokens such as URLs, emails, and hashtags stay exactly as entered unless the user taps a suggestion.

## Performance

Dictionaries are prefix-indexed and loaded lazily. Frequent prefixes are cached. Candidate lists are bounded. There is no network lookup, no full dictionary scan on each keypress, and no keyboard layout rebuild while ranking.

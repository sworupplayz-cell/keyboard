# Roman Nepali Engine 2.0

Roman mode still types on a QWERTY layout. Conversion is local and word-based.

## Pieces

- `RomanSpellingNormalizer` — reusable alternate spellings (`ch/chh`, `v/bh`, lengthened vowels, leading `aa/a`).
- `RomanPhoneticEngine` — dictionary-free transliteration for vowels, consonants, aspiration, clusters, matras, nasals, virama, and common `cha/chu/chau` endings.
- `RomanTextProcessor` — splits a sentence so URLs, `@mentions`, `#tags`, numbers, emoji, and punctuation stay unchanged.
- `RomanNepaliConverter` — vocabulary lookup, mixed-language protection, ranking, and phonetic fallback.

The IME UI does not own these rules. `KeyboardService` only asks the converter for a word or a suggestion list.

## Ranking

At most three suggestions, in this order of strength:

1. known vocabulary
2. learned mappings
3. frequency
4. previous-word context
5. phonetic likelihood
6. the original Roman spelling

Uncertain text is never auto-replaced.

## Unknown words

If a Roman word is absent from the TSV, the phonetic engine still emits Devanagari. `zorpa` and `manparcha` are converted by rule, not rejected.

Phase 19 adds everyday variants such as `auchu` / `aaunchu` → `आउँछु` and keeps `school` / `college` as English.

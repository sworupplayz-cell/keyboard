# Offline language intelligence

Phase 22 strengthens the existing suggestion path. It is not cloud AI, not a neural language model, and it never uploads text.

## Vocabulary architecture

Bundled files stay lazy-loaded and frequency-ordered. Earlier lines still rank higher.

| File | Role |
| --- | --- |
| `english_vocabulary.txt` | Everyday English, contractions, morphology forms, slang, places |
| `nepali_vocabulary.txt` | Everyday Devanagari, verb/noun families, school/tech words |
| `roman_nepali_dictionary.tsv` | Roman spellings → Nepali forms, including informal variants |

`VocabularyLoader` still reads a plain word per line. Optional tab fields may add `category`, `stem`, and `|` alternates. `VocabularyCatalog` assigns CORE / MORPHOLOGY / NAME / PLACE / SLANG / TECH so names and slang cannot outrank core prefixes.

Dictionaries are prefix-indexed once. `LocalWordSuggester` caches empty-context prefix queries. Keystrokes do not rescan the files.

## Ranking

One pipeline (`SuggestionRanker`) scores at most three unique candidates:

1. Exact prefix
2. Personal / learned frequency
3. Recent usage
4. Previous one, two, or three words
5. Dictionary frequency
6. Morphology relatives
7. High-confidence typos
8. Typed fallback

Score caps keep one weak signal from dominating. The center strip slot is still the strongest candidate. Unknown text is never auto-replaced.

## Context and learning

`PhrasePredictor` and `ContextModel` store a small seed plus bounded local pairs (240 phrases, 400 context pairs, 250 learned words, 60 recents). They look at the previous 1–3 completed words. This is a deterministic table, not a language model.

Learning still requires a suggestion tap or finishing the same unknown word twice. URLs, emails, tokens, passwords, and 1–2 character garbage are rejected. Clear-data settings are unchanged.

## Roman Nepali

`RomanSpellingNormalizer` collapses doubled letters/vowels and maps `ch/chh`, `sh/s`, `ph/f`, `ny/n`, and informal endings. Extra dictionary keys such as `jaanxu`, `malay`, and `dhanyabaad` share the same Nepali targets. Unknown words still go through the phonetic engine. Conversion is not more aggressive: `school` stays English and `ma school jaanchu` is still `म school जान्छु`.

## Privacy

No INTERNET permission, analytics, telemetry, or remote prediction. Learned data stays on the device. Typed words are not written to Logcat.

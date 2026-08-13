# Offline language intelligence

Phase 27 deepens everyday English, Nepali, Roman Nepali, and mixed-language intelligence on the Phase 23–26 pipeline. It is not cloud AI, not a neural language model, and it never uploads text.

The IME still has **one** prediction pipeline. `SuggestionEngine` is the only public facade. English, Nepali, and Roman candidates all finish in `SuggestionRanker`. `PredictionPipeline` describes the stages. There is no second competing engine.

```
InputContext
    → candidate generation
    → normalization
    → filtering
    → SuggestionRanker scoring
    → diversity / deduplication
    → top 3 suggestions
```

Sources that feed the same ranker: exact and prefix vocabulary, normalized prefixes, learned unigrams, recents, previous 1–3 word phrases, morphology, high-confidence typos, Roman phonetic forms, contractions, names, places, slang, and at most one emoji.

## Vocabulary architecture

Bundled files stay lazy-loaded and frequency-ordered. Earlier lines still rank higher.

| File | Role |
| --- | --- |
| `english_vocabulary.txt` | Everyday English, contractions, morphology forms, slang, places |
| `nepali_vocabulary.txt` | Everyday Devanagari, verb/noun families, school/tech words |
| `roman_nepali_dictionary.tsv` | Roman spellings → Nepali forms, including informal variants |

`VocabularyLoader` still reads a plain word per line. Optional tab fields may add `category`, `stem`, and `|` alternates. `VocabularyCatalog` assigns CORE / MORPHOLOGY / NAME / PLACE / SLANG / TECH so names and slang cannot outrank core prefixes.

Dictionaries are prefix-indexed once. `LocalWordSuggester` caches empty-context prefix queries (`PREFIX_CACHE_LIMIT = 64`). Keystrokes do not rescan the files.

## Ranking

One scorer (`SuggestionRanker`) produces at most three unique candidates:

1. Exact current-prefix match
2. Strong 2–3 word context
3. Accepted personal usage (capped)
4. Previous-word / bigram context
5. Language-appropriate dictionary frequency
6. Recent personal usage
7. Morphology relatives
8. High-confidence typos / phonetic forms
9. Typed fallback

Score caps keep one weak signal from dominating. `CandidateIdentity` collapses playful Roman lengthening (`ramro` / `ramroo` / `ramrooo`) so the strip does not show the same concept three times. The center strip slot is still the strongest candidate. Unknown text is never auto-replaced.

## Personalization

`LearnedWordStore` is the unigram model: usage count plus recency order. Recent use matters more than ancient use, but generic vocabulary is never deleted. `ContextModel` and `PhrasePredictor` store bounded bigrams and trigrams (400 context pairs, 240 phrases). Learned pairs survive process restarts.

Tapping a suggestion records the accepted word, bumps its personal frequency, and stores previous-word / previous-two-word context when the tokens are eligible. Personalization cannot outrank an exact prefix or exceed the learned-score cap.

## Context

`PhrasePredictor` and `ContextModel` look at the previous 1–3 completed words. Seeds stay compact and conversational (`good` → morning, `how are` → you, `मलाई मन` → पर्छ, `ma school` → jaanchu). This is a deterministic table, not a language model. Empty input without a useful previous word shows nothing.

## Roman Nepali

`RomanSpellingNormalizer` collapses doubled letters/vowels and maps `ch/chh`, `sh/s`, `ph/f`, `ny/n`, `xa/cha`, `xu/chu`, `xau/chau`, and informal endings. Extra dictionary keys such as `jaanxu`, `malay`, `malaai`, `hunxa`, `nepaali`, `xa`, and `thikxa` share the same Nepali targets. Unknown words still go through the phonetic engine. Conversion is not more aggressive: `school`, `college`, `computer`, `internet`, and `game` stay English, capitalized `Kathmandu` stays Latin, and `ma school jaanchu` is still `म school जान्छु`. The selected keyboard mode never auto-switches.

## Privacy

No INTERNET permission, analytics, telemetry, or remote prediction. Learned data stays on the device. Typed words are not written to Logcat. URLs, emails, tokens, passwords, credit-card-like numbers, and 1–2 character garbage are rejected.

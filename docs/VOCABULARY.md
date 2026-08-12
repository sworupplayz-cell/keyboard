# Offline typing intelligence

The IME ranks suggestions locally. Nothing is downloaded while typing.

## Files

- `app/src/main/res/raw/english_vocabulary.txt` — frequency-ordered English words. Earlier lines rank higher.
- `app/src/main/res/raw/nepali_vocabulary.txt` — frequency-ordered Nepali Devanagari words.
- `app/src/main/res/raw/roman_nepali_dictionary.tsv` — Roman spellings mapped to one or more Nepali forms. Alternate spellings are extra rows or `|` alternatives.

Rebuild the lists after editing `scripts/build_vocabularies.py`:

```bash
python3 scripts/build_vocabularies.py
```

## Ranking

Suggestions combine dictionary frequency, prefix matches, recent words, explicitly learned words, and conservative nearby-key / vowel-deletion typos. The typed word is kept when nothing better exists. The strip still shows at most three items.

## Roman mode

Known Romanized Nepali words convert from the TSV. Known English words stay English. Unknown Roman words still go through the phonetic engine, so made-up words are not rejected.

# Emoji, symbols, and numbers

Phase 13 keeps the Gboard-style letter keyboard and adds a larger offline panel layer.

## Emoji

- Catalog file: `app/src/main/res/raw/emoji_catalog.tsv`
- Rebuild: `python3 scripts/build_emoji_catalog.py`
- Columns: glyph, category, keywords, optional skin-tone variants
- `EmojiRepository` lazy-loads the TSV once and keeps category plus prefix search indexes
- Recent emoji are bounded (40) and stored locally
- A small usage counter can promote frequent emoji inside a category
- Search is offline English-keyword matching; empty and unknown queries return nothing
- Multi-code-point emoji, variation selectors, ZWJ sequences, flags, and a practical set of skin-tone variants are stored as Unicode, not images

## Numbers and symbols

- The number pad includes operators, parentheses, percent, and currency
- `DigitScript` switches Latin `0-9` and Devanagari `०-९`
- Symbols are grouped into Common, Math, and More
- Recent symbols are optional, bounded, and local

## Privacy

No internet permission, emoji API, account, or cloud lookup is used.

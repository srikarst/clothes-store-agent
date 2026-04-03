# SimpleRagStore

This document explains `backend/src/main/java/com/example/clothesstoreagent/simple/SimpleRagStore.java`.

## Purpose

`SimpleRagStore` is an in-memory lexical retriever used by chat flow to return short context snippets.

## Data source

The class stores a fixed list of documents in code (`docs`), each with:

- `id` (for example: `returns`, `shipping`, `catalog`)
- `text` (the context snippet returned to chat)

## Retrieval method

Method: `retrieve(String query, int topK)`

1. Tokenize the incoming query.
2. Tokenize each document text.
3. Compute overlap score = number of shared tokens.
4. Add bonus `+0.5` if raw query text contains the document `id`.
5. Keep only documents with score `> 0`.
6. Sort descending by score.
7. Return up to `topK` document texts.

## Tokenization rules

Method: `tokenize(String text)`

- Lowercases input.
- Splits on non-alphanumeric characters (`[^a-z0-9]+`).
- Canonicalizes tokens:
  - If token ends with `s` and length > 3, remove trailing `s`.
  - Example: `returns` -> `return`, `jeans` -> `jean`.
- Drops tokens shorter than 3 chars.
- Drops stop words from `STOP_WORDS`.
- Uses a `LinkedHashSet` to keep unique tokens while preserving order.

## Example

Query: `what is your return policy`

- After tokenization: includes `return`, `policy`.
- `returns` document tokenizes to include `return`.
- Overlap score becomes positive.
- Result includes the returns-policy snippet in `ragContext`.

## Notes

- This is intentionally simple and deterministic.
- No embeddings, no vector DB, no external service calls.
- Good for demonstrating RAG behavior in a minimal app.

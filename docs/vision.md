# BooKI Vision

BooKI is a cloud-based conversational reading assistant. You open a PDF, pick a
starting page, and read as far as you want — with BooKI alongside you to discuss,
quiz, summarize, and explain by text or voice, without ever leaving the reading.

Primary clients: Android, Windows and Linux, all through one responsive web /
PWA application.

## Guiding principles

- Reading is never blocked: BooKI accompanies, it doesn't turn reading into homework.
- The core unit is the **session**: an open-ended reading journey with its own context and a recorded range of pages reached.
- Text and voice are the **same** conversation — one engine, one history, one context.
- A **tutor profile** (the code calls it an *AI Profile*) is the full editable set of prompts a session runs on — persona, difficulty, per-function behavior (`docs/prompts.md`).
- Quiz, summary and explanation are conversational capabilities, not separate destinations.
- The PDF stays the visual protagonist; the UI never becomes an LMS or a dashboard.

## Core flow

1. Upload a PDF.
2. Choose where to start and create a session (difficulty, language, tutor profile, reader profile).
3. Open the reader.
4. Read, type, or speak with BooKI.
5. Get contextual answers grounded in a bounded window around the current page,
   or name a page range explicitly.
6. Choose a range from the pages reached so far for a quiz or summary, or ask
   BooKI to explain a passage or build a mnemonic in the same conversation.

## Deliberately out of scope

- Social network, public library, recommendation feeds.
- Gamification, badges, rankings.
- Multiple family profiles.
- Native mobile apps — one responsive PWA instead.
- Background wake-word / always-listening voice.

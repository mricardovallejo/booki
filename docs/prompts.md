# Prompts & AI Profiles

How BooKI decides *what* to say — the instructions the model reads before every
answer, who owns which part, and how the pieces combine.

This is the single reference for the topic. `docs/backend.md` and
`docs/frontend.md` only point here; the decision record is `ADR-015` in
`docs/decisions.md`. Frontend, mock and Java backend all implement this.

## The model

Every conversational turn assembles one system prompt from ~7 parts. Two halves:

- **Core** — fixed, app-owned, never shown as editable. Safety, grounding in the
  page range, "respond in the session language", encouraging tone, and the
  conflict rule. One string; not part of any profile.
- **AI Profile** — everything else, and all of it editable by the user: the
  persona, the reader context, the three difficulty levels, the per-function
  instructions, and capability routing.

A session points at exactly one AI Profile and keeps it for life.

## The layered prompt

In precedence order (written into the core so the model knows it):

| # | Layer | Source | Editable |
|---|---|---|---|
| 1 | **Core** | app | no |
| 2 | **Difficulty** — the rubric for the active level | AI Profile | yes |
| 3 | **Function contract + body** — only when a capability runs | AI Profile | body only |
| 4 | **Persona** | AI Profile | yes |
| 5 | **Reader context** | AI Profile | yes |
| — | Session facts (document, page range, current page) + the page text | session | no |
| — | Capability routing (plain chat only) | AI Profile | body only |

**Conflict rule:** when two layers disagree, the higher one wins — *except* a
stated accessibility need in the reader context outranks persona style.

`GET /sessions/{id}/context` returns all of these (each tagged with a `group`) so
the reader can see exactly what shapes an answer; the ℹ button in the chat panel
renders it, folding the function/routing groups away by default.

## The AI Profile

### SlotPrompts

A **SlotPrompt** is one named prompt. `text` is the editable body. Function and
routing SlotPrompts also carry a **locked frame** (`lockedPreamble` /
`lockedPostamble`) — the part the *code* depends on (an output format it parses)
that the user can't touch. A `null` frame means the whole SlotPrompt is free text.

| key | shown as | group | locked frame |
|---|---|---|---|
| `persona` | Persona | persona | — |
| `reader_context` | Reader context | reader | — |
| `rubric_easy` / `_medium` / `_hard` | Difficulty — Easy/Medium/Advanced | difficulty | — |
| `fn_quiz_question` | Function — Quiz question | functions | "output only the question…" |
| `fn_answer_grading` | Function — Answer grading | functions | the `CORRECT:` / `SCORE:` / `FEEDBACK:` format |
| `fn_summary` | Function — Summary | functions | "prose only…" |
| `fn_explain` | Function — Explain | functions | — |
| `fn_mnemonic` | Function — Mnemonic | functions | — |
| `capability_routing` | Capability routing | routing | the `{"capability":"<name>"}` contract |

### Structured fields (not SlotPrompts)

- **`readerLevel`** — `beginner` / `intermediate` / `advanced` / null. Sits on
  the reader-context slot in the editor. Only use: the create-session screen
  suggests a difficulty from it (beginner→Easy …), which the user can override.
- **`enabledCapabilities`** — a subset of `quiz` / `summary` / `explain` /
  `mnemonic`. A capability left out is **off for the whole session**: BooKI never
  triggers it on its own, and its quick-action button is hidden in the chat. The
  `capability_routing` body only tunes how eagerly BooKI reaches for the enabled
  ones — it is not the on/off switch.

## Ownership & lifecycle

- **Shipped templates** are hidden originals (never listed, never run by a
  session). At **registration** every account gets one editable copy of each
  (one flagged `isDefault`). Sessions always run on one of the user's own,
  editable profiles — there is no read-only-profile state in normal use.
- A profile holds the *whole set* of SlotPrompts and is **autonomous**: it
  doesn't read from its template, it only remembers (`basedOnId`) which one it
  came from.
- Each SlotPrompt stores an `originalText` snapshot (the text it was born with).
  It powers the computed **Edited / Original** badge (`text != originalText`,
  never a stored flag), the per-SlotPrompt **Restore original text**, and the
  whole-profile **Restore to original** (`POST /ai-profiles/{id}/restore` —
  re-seeds all SlotPrompts + `readerLevel` + `enabledCapabilities` from
  `basedOnId`, keeps the name).
- **Duplicate** makes another autonomous copy.
- **When a shipped template's text is later improved: only the hidden template
  changes. Existing user profiles are never touched** — edited or not. A user who
  wants the new text does "Restore to original" or redoes that prompt by hand.
  Rationale: zero surprises, and no reconciliation logic in the migration.

## Difficulty

`easy | medium | hard` is just a label. What each level *means* — question style,
how much scaffolding, how strict the grading — is the text in `rubric_easy/…`.
The session carries a default difficulty; the quiz panel can override it per
round (the AI Profile is always the session's). The quiz panel shows the active
rubric inline with a deep link (`/ai-profiles/{id}?slot=rubric_<level>`) to edit
it.

## Language

Three separate things:

- **App UI language** — interface chrome. English only for now; out of scope.
- **Session language** (`session.language`, en/es/fr) — what BooKI speaks in.
  The core prompt forces output into it *regardless of what language the
  instructions are written in*.
- **Slot authoring language** — factory text is English; user-edited slots can be
  any language. An AI Profile has no language of its own.

## How a turn is assembled

- **Plain chat**: core + rubric(active level) + persona + reader context +
  session facts + page text + `capability_routing`. If the model replies with
  exactly `{"capability":"<name>"}` for an *enabled* capability, that capability
  runs instead; otherwise its reply is the answer.
- **Quick-action button / explicit capability**: skips routing, runs the
  capability directly (rejected if it isn't enabled).
- **A capability call** (quiz question, grading, summary, explain, mnemonic):
  core + rubric + the function's locked frame + its editable body + persona +
  reader context + the relevant page(s). Grading and quiz generation parse the
  model's reply against the locked frame's format.

## API surface

| Method | Path | Purpose |
|---|---|---|
| GET | `/ai-profiles` | the user's profiles (no slots) |
| GET | `/ai-profiles/{id}` | one profile with slots |
| PATCH | `/ai-profiles/{id}` | name / `readerLevel` / `enabledCapabilities` / slot bodies |
| POST | `/ai-profiles/{id}/duplicate` | autonomous copy |
| POST | `/ai-profiles/{id}/revert` | one SlotPrompt back to its `originalText` |
| POST | `/ai-profiles/{id}/restore` | whole profile back to its template |
| DELETE | `/ai-profiles/{id}` | delete (400 if it's the only one) |
| GET | `/sessions/{id}/context` | the assembled layers + `enabledCapabilities` |

Full schemas: `docs/openapi.yaml` (`AiProfile`, `AiProfileSlot`, `SessionContext`).

## Frontend

- `src/api/aiProfiles.ts` — the calls. `src/hooks/useAiProfiles.ts` (list +
  duplicate + delete), `src/hooks/useAiProfile.ts` (one profile + in-memory
  draft of every editable field + save/revert/restore), `src/hooks/useAiProfileSlots.ts`
  (read-only slots, for the quiz panel).
- `src/pages/AiProfilesPage.tsx` — one screen: profile selector +
  Duplicate / Restore to original / Delete, and inline the slot editor (slot
  nav, locked frame greyed out, Edited/Original badge, `?slot=` deep link,
  Advanced section folding functions + routing away, unsaved-changes guard).
- `src/components/ContextInfoButton.tsx` — the ℹ layers popup.
- `src/components/CreateSessionModal.tsx` — profile picker (defaults to
  `isDefault`), difficulty suggested from `readerLevel`.
- `src/components/ChatPanel.tsx` — hides quick-action buttons for capabilities
  not in `session.enabledCapabilities`.
- `src/components/SessionSidebar.tsx` — shows the active profile name, linked.

## Backend

Two tables:

- `ai_profiles` — `user_id`, `name`, `based_on_template` (a template key, not an
  FK), `is_default`, `reader_level` (nullable), `enabled_capabilities` (csv via
  `CapabilitySetConverter`).
- `ai_profile_slot_prompts` — `profile_id`, `slot` (`SlotKey` enum), `text`,
  `original_text`. `ON DELETE CASCADE`; sessions/quiz_attempts FK to
  `ai_profiles` is `ON DELETE SET NULL`.

Templates and the fixed core live in code: **`SlotPromptCatalog`** (mirror of
`mock-backend/src/aiProfiles.js`). `SlotKey` carries each prompt's label, group
and locked frame. "Improving a template" = editing that class; existing profiles
keep their own rows and are never touched.

**`PromptAssembler`** owns the layering + precedence: `forChat(session, docText)`,
`forFunction(session, SlotKey, difficulty, docText)`, and `describe(session)` for
`GET /sessions/{id}/context`. `ConversationEngine` appends the capability router,
filtered to the profile's `enabledCapabilities`; a routed directive or explicit
`capabilityHint` for a disabled capability is rejected. Quiz / summary / explain /
mnemonic ask the assembler for their `fn_*` SlotPrompt; their remaining inline
text is only the per-call dynamic bits.

Registration seeds one profile per template (`SlotPromptCatalog.seedFor(user)`);
`AiProfileBackfill` does the same on startup for any user with none. The schema
change is folded into `V1__init.sql` (no prod data) — **wipe the target DB before
deploying it** so Flyway re-runs clean.

## Design principles

- **One object per session.** No separate "persona library" + "learner profile" +
  "function settings" — the AI Profile is already per study-context.
- **Structured where the machine cares, free text where the human does.**
  Output formats and the capability list are structured/locked; tone and approach
  are free text.
- **Nothing changes under the user.** A profile is theirs and is never rewritten
  by an update.
- **Transparent.** Every layer that shapes an answer is inspectable from the
  session.
- **Provider-neutral routing.** Capabilities are opted into via a JSON directive
  in the reply, not native tool-calling — one contract for every AI provider.

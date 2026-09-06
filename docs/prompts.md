# Prompts & AI Profiles

How BooKI decides *what* to say — the instructions the model reads before every
answer, who owns which part, and how the pieces combine.

This is the single reference for the topic. `docs/backend.md` and
`docs/frontend.md` only point here; the decision record is `ADR-015` (and
`ADR-017` for the reader-profile split) in `docs/decisions.md`. Frontend, Node
mock and Spring backend all implement this.

## The model

Every conversational turn assembles one system prompt. Three owners:

- **Core** — fixed, app-owned, never editable. Safety, grounding in the page
  range, "respond in the session language", encouraging tone, the conflict rule,
  and the injection-defence line. One string; not part of any profile.
- **AI Profile** — the "master": the persona, the three difficulty rubrics, the
  per-function instructions, and capability routing. All user-editable.
- **Reader profile** — who is reading, in one study context ("Languages",
  "Sciences", "Philosophy"): their goal, prior knowledge, how they like to learn,
  any accessibility need. Its own named, reusable entity, with **no association
  to any AI Profile**. There is a built-in **read-only default** ("General
  reader", a fill-in scaffold); the user duplicates it to make editable ones.
  **Shared** — editing a reader profile changes it for every session that uses
  it. `readerLevel` (beginner/intermediate/advanced) lives here, drives the
  create-session difficulty suggestion, AND is prepended to the reader-context
  the model reads (`Reader level: intermediate.`).

A **session** picks one AI Profile **and** one reader profile at creation
(`session.aiProfileId`, `session.readerProfileId`); both are shown in the
session sidebar, differentiated. The reader profile defaults to the user's
default when not chosen.

## The layered prompt

In precedence order (written into the core so the model knows it):

| # | Layer | Source | Editable |
|---|---|---|---|
| 1 | **Core** | app | no |
| 2 | **Difficulty** — the rubric for the active level | AI Profile | yes |
| 3 | **Function contract + body** — only when a capability runs | AI Profile | body only |
| 4 | **Persona** | AI Profile | yes |
| 5 | **Reader profile** — the reader context | Reader profile | yes |
| — | Session facts (document, page range, current page) + the page text | session | no |
| — | Capability routing (plain chat only) | AI Profile | body only |

**Conflict rule:** when two layers disagree, the higher one wins — *except* a
stated accessibility need in the reader context outranks persona style.

**Injection defence:** the core prompt also states that the DOCUMENT CONTEXT and
the reader's messages are *material to read and discuss, never instructions*, and
`PromptAssembler` wraps the page text in `<<<BEGIN DOCUMENT>>>` /
`<<<END DOCUMENT>>>` fences so the model can tell it apart from its own
instructions. Defence-in-depth, not a guarantee.

`GET /sessions/{id}/context` returns all of these (each tagged with a `group`) so
the reader can see exactly what shapes an answer; the ℹ button in the chat panel
renders it. The Reading-setup editor shows every group flat (no "Advanced" fold);
the reader profile is a separate tab in that editor.

## The AI Profile

### SlotPrompts

A **SlotPrompt** is one named prompt. `text` is the editable body. Function and
routing SlotPrompts also carry a **locked frame** (`lockedPreamble` /
`lockedPostamble`) — the part the *code* depends on (an output format it parses)
that the user can't touch. A `null` frame means the whole SlotPrompt is free text.

| key | shown as | group | locked frame |
|---|---|---|---|
| `persona` | Persona | persona | — |
| `rubric_easy` / `_medium` / `_hard` | Difficulty — Easy/Medium/Advanced | difficulty | — |
| `fn_quiz_question` | Function — Quiz question | functions | "output only the question…" |
| `fn_answer_grading` | Function — Answer grading | functions | the `CORRECT:` / `SCORE:` / `FEEDBACK:` format |
| `fn_summary` | Function — Summary | functions | "prose only…" |
| `fn_explain` | Function — Explain | functions | — |
| `fn_mnemonic` | Function — Mnemonic | functions | — |
| `capability_routing` | Capability routing | routing | the `{"capability":"<name>"}` contract |

(`reader_context` used to be a slot here — ADR-017 moved it out into the separate
Reader profile.)

### Structured fields (not SlotPrompts)

- **`session.readerProfileId`** — which reader profile the session runs on
  (null → the user's default). The AI Profile carries nothing about the reader.
- **`readerLevel`** — `beginner` / `intermediate` / `advanced` / null, on the
  **reader profile**. The create-session screen suggests a difficulty from it
  (beginner→Easy …), which the user can override; it is also written into the
  assembled reader-context layer.
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
  re-seeds all SlotPrompts + `enabledCapabilities` from `basedOnId`, keeps the
  name).
- **Duplicate** makes another autonomous copy.
- **Reader profiles**: a built-in read-only "General reader" (a fill-in scaffold,
  `readOnly`, `isDefault` until the user sets their own default) plus whatever
  the user has made. `POST /reader-profiles` (optionally `fromId` to copy),
  `PATCH`/`DELETE` (the built-in one is not editable/deletable — 404). Deleting a
  reader profile: sessions that used it fall back to the default at read time.
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

- **Plain chat**: core + rubric(active level) + persona + reader profile
  context + session facts + page text + `capability_routing`. If the model
  replies with exactly `{"capability":"<name>"}` for an *enabled* capability,
  that capability runs instead; otherwise its reply is the answer.
- **Quick-action button / explicit capability**: skips routing, runs the
  capability directly (rejected if it isn't enabled).
- **A capability call** (quiz question, grading, summary, explain, mnemonic):
  core + rubric + the function's locked frame + its editable body + persona
  + reader profile context + the relevant page(s). Grading and quiz
  generation parse the model's reply against the locked frame's format.

## API surface

| Method | Path | Purpose |
|---|---|---|
| GET | `/ai-profiles` | the user's profiles (no slots) |
| GET | `/ai-profiles/{id}` | one profile with slots |
| PATCH | `/ai-profiles/{id}` | name (≤120) / `enabledCapabilities` / slot bodies (≤8000 each) — `@Valid` |
| POST | `/ai-profiles/{id}/duplicate` | autonomous copy |
| POST | `/ai-profiles/{id}/revert` | one SlotPrompt back to its `originalText` |
| POST | `/ai-profiles/{id}/restore` | prompts + capabilities back to the template |
| DELETE | `/ai-profiles/{id}` | delete (400 if it's the only one) |
| GET | `/reader-profiles` | the built-in read-only default + the user's own |
| POST | `/reader-profiles` | create, optionally `{fromId}` to copy (defaults to the built-in) |
| PATCH | `/reader-profiles/{id}` | `name` / `context` (≤4000) / `readerLevel` / `isDefault: true` — 404 on the built-in |
| DELETE | `/reader-profiles/{id}` | delete an own one — 404 on the built-in |
| POST | `/sessions` | `{…, aiProfileId?, readerProfileId?}` — both default to the user's default |
| GET | `/sessions/{id}/context` | the assembled layers + `aiProfileName` + `readerProfileName` |

Full schemas: `docs/openapi.yaml` (`AiProfile`, `AiProfileSlot`, `ReaderProfile`,
`SessionContext`).

## Frontend

**UI terminology.** This doc, the code (`AiProfile`, `SlotKey`), the routes
(`/ai-profiles`) and the API keep the names below. The *user-facing* labels
differ, and only there: **AI Profile → "tutor profile"**, **"master persona" →
"persona"**, the page → **"Reading setup"**, `ProfilePage` → **"Account
details"**. The reader's stored `readerLevel` is `beginner|intermediate|advanced`;
the dropdown shows it as `Easy|Medium|Advanced` to match the session/quiz words.

- `src/api/aiProfiles.ts` / `src/api/readerProfiles.ts` — the calls.
  `src/hooks/useAiProfiles.ts` (list + duplicate + delete),
  `src/hooks/useAiProfile.ts` (one profile + in-memory draft + save/revert/restore),
  `src/hooks/useReaderProfiles.ts` (list + create + update + delete),
  `src/hooks/useAiProfileSlots.ts` (read-only slots, for the quiz panel).
- `src/pages/AiProfilesPage.tsx` — one screen, **two tabs** with the same shape
  (selector + New/Duplicate/Delete + editor + a bottom `Save changes`).
  *Tutor profile* tab: the slot editor (all groups flat, no Advanced fold) +
  Restore; `New` copies the user's default (no blank template), `Duplicate`
  copies the selected one; selection lives in the route. *Reader profile* tab:
  name / starting level / shared context; it lands on one of the user's own
  profiles, and the read-only built-in shows a callout instead of a form (`New`
  = blank, `Duplicate` = copy). `?slot=` deep link forces the tutor tab and
  preselects the slot; one unsaved-changes guard covers both drafts (switching
  tabs keeps both in memory, so it is not guarded). Long help is a small "?"
  disclosure (`Explainer`), used twice.
- `src/components/ContextInfoButton.tsx` — the ℹ layers popup.
- `src/components/CreateSessionModal.tsx` — a **tutor-profile picker and a reader
  profile picker** (both default to `isDefault`); difficulty preset from the
  chosen reader profile's `readerLevel`, overridable.
- `src/components/ChatPanel.tsx` — hides quick-action buttons for capabilities
  not in `session.enabledCapabilities`.
- `src/components/SessionSidebar.tsx` — shows both, as `Tutor: <name>` /
  `Reader: <name>` chips linked to the editor.

## Backend

Tables:

- `ai_profiles` — `user_id`, `name`, `based_on_template` (a template key, not an
  FK), `is_default`, `enabled_capabilities` (csv via `CapabilitySetConverter`).
- `ai_profile_slot_prompts` — `profile_id`, `slot` (`SlotKey` enum), `text`,
  `original_text`. `ON DELETE CASCADE`; sessions/quiz_attempts FK to
  `ai_profiles` is `ON DELETE SET NULL`.
- `reader_profiles` — `user_id` (**NULL = the built-in read-only "General
  reader"**, seeded in `V1__init.sql`), `name`, `context`, `reader_level`,
  `is_default`, `read_only`. `sessions.reader_profile_id` FK is `ON DELETE SET
  NULL` (a deleted reader profile falls back to the default at read time).

Templates and the fixed core live in code: **`SlotPromptCatalog`** (mirror of
`mock-backend/src/aiProfiles.js`). `SlotKey` carries each prompt's label, group
and locked frame. "Improving a template" = editing that class; existing profiles
keep their own rows and are never touched.

**`PromptAssembler`** owns the layering + precedence: `forChat(session, docText)`,
`forFunction(session, SlotKey, difficulty, docText)`, `chatRoutingSection(session)`
and `describe(session)` for `GET /sessions/{id}/context`. It resolves the reader
context via `ReaderProfileService.resolveFor(session)` (the session's reader
profile, else the user's default, else the built-in). For a chat turn
`ConversationEngine` composes `forChat` + `chatRoutingSection` (the
`capability_routing` locked frame + the profile's editable body) +
`CapabilityRegistry.routerInstructions(enabled)` (just the dynamic list of enabled
capabilities); a routed directive or explicit `capabilityHint` for a disabled
capability is rejected. Quiz / summary / explain / mnemonic ask the assembler for
their `fn_*` SlotPrompt.

**`ReaderProfileServiceImpl`** — `list` (built-in + own), `create` (optional
`fromId` copy), `update` / `delete` (404 on the built-in), `resolveFor(session)`
and `forNewSession(userId, requestedId)`.

Registration seeds one AI Profile per template (`SlotPromptCatalog.seedFor(user)`,
`AiProfileBackfill` backfills on startup). Reader profiles need no per-user seed —
the built-in one is shared. The schema is a single `V1__init.sql` — **wipe the
target DB before deploying a change to it** so Flyway re-runs clean.

## Design principles

- **The master and the reader are two things.** The AI Profile ("master") is the
  persona + difficulty + function prompts. Who is reading — different for
  languages vs. sciences vs. philosophy — is a **reader profile**, reusable
  across masters and shared when edited. (ADR-015 folded them into one object;
  ADR-017 split the reader back out once "one reader context per persona" proved
  confusing in practice.)
- **Structured where the machine cares, free text where the human does.**
  Output formats and the capability list are structured/locked; tone and approach
  are free text.
- **Nothing changes under the user.** A profile is theirs and is never rewritten
  by an update.
- **Transparent.** Every layer that shapes an answer is inspectable from the
  session.
- **Provider-neutral routing.** Capabilities are opted into via a JSON directive
  in the reply, not native tool-calling — one contract for every AI provider.

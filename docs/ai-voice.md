# AI and voice in BooKI

## Conversation architecture

Text, quick-action buttons and voice all converge on one **`ConversationEngine`**
(`com.booki.conversation`). It owns every conversational turn:

1. resolve + ownership-check the `Session`;
2. build the recent-history window (most recent N messages, chronological order —
   `booki.conversation.history-window`, default 20);
3. persist the user turn (`Message`, `InputType.TEXT` or `VOICE`);
4. assemble the system prompt via `SessionContextBuilder` plus the session's
   page-range text, capped at `booki.conversation.max-context-chars` (24000) so a
   very wide range can't produce an unbounded request;
5. call the session's `AiProvider`;
6. persist BooKI's reply, or raise a controlled error.

The engine knows nothing about transport (REST / SSE / WebSocket). Today the
transports are `POST /api/sessions/{id}/messages` (text) and
`POST /api/sessions/{id}/voice` (audio).

### The layered system prompt (`SessionContextBuilder`)

- BooKI's base prompt, which names the session language (English / Spanish /
  French) for the model to reply in.
- The Profile Master's system prompt, if one was chosen for the session.
- What the reader wrote about themselves in their own profile
  (`User.systemPrompt`), if anything.
- Session difficulty, document title, page range, current page.
- Text extracted from the page range.

Chat, the conversational capabilities, and standalone quiz/summary generation all
use this same builder.

## AI providers

- Interface: `AiProvider.converse(systemPrompt, context, userMessage)`.
- Implementations, all always-registered named beans behind `AiProviderRegistry`:
  `claude` (`ClaudeProvider`), `openai` / `kimi` (`OpenAiCompatibleProvider`),
  `ollama` (`OllamaProvider`).
- Each `Session` picks one at `POST /sessions` (`Session.aiProvider`); `null`
  falls back to `booki.ai.default-provider` — `openai` by default (`dev`/`test`),
  `ollama` on `local` (`AI_PROVIDER` overrides either). `openai` is the default
  because the same key also powers cloud voice.
- **Provider failures are real errors.** A network failure, upstream 4xx/5xx, or
  an empty/unparseable payload raises `AiProviderException`; `ConversationEngine`
  turns it into `ConversationFailedException` and `GlobalExceptionHandler`
  returns **HTTP 502** with `{"error": "..."}`. Provider failure text is never
  persisted as a BooKI answer.

| Provider | Env vars | Default model |
|----------|----------|---------------|
| `openai` *(default)* | `OPENAI_API_KEY`, `OPENAI_MODEL` | `gpt-4o-mini` |
| `claude` | `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` | `claude-sonnet-5` |
| `kimi`   | `KIMI_API_KEY`, `KIMI_MODEL`, `KIMI_BASE_URL` | `kimi-k2` |
| `ollama` | `OLLAMA_BASE_URL`, `OLLAMA_MODEL` (no key) | `llama3.2:1b` |

## Conversational capabilities (ADR-008)

Quiz, summary, explain and mnemonic are usable **inside the conversation**, not
just their own panels. Small `ConversationCapability` beans (`name`,
`modelDescription`, `execute`) held by `CapabilityRegistry`. Not an agent
framework.

- **Routing is provider-neutral** — no native tool calling (four different wire
  formats), no keyword matching. `CapabilityRegistry.routerInstructions()` is
  appended to the system prompt; when a capability fits, the model replies with
  *only* `{"capability":"<name>"}` and `parseDirective()` recognises that
  strictly. Otherwise the reply is a normal answer.
- **Quick-action buttons** send their natural-language text plus an optional
  `capabilityHint` on `POST /messages` — the engine runs that capability
  directly, no routing call.
- **Conversational quiz asks a question only.** The reader's answer and any
  "give me a hint" are ordinary chat turns. Scored `QuizAttempt` rows stay
  exclusive to the `POST /sessions/{id}/quiz/answer` panel flow, so Progress and
  Reports are unaffected.

Capabilities reuse the existing services: `QuizService.generateComprehensionQuestion(Session)`
and `ReportService.generateSummaryText(Session, …)`.

## Voice (ADR-009 — supersedes the Web Speech API decision, ADR-002)

Voice is a **cloud, provider-agnostic** capability that shares the pipeline with
text: the transcript goes through the exact same `ConversationEngine.converse`
call, with `InputType.VOICE`.

```
🎤 getUserMedia + MediaRecorder ──audio──▶ POST /api/sessions/{id}/voice
        │
   SpeechToTextProvider.transcribe(bytes, mime, session.language)
        │ transcript
   ConversationEngine.converse(…, InputType.VOICE)
        │ reply text (persisted)
   TextToSpeechProvider.synthesize(reply, session.language)   ← best-effort
        │
   { userMessage, botMessage, audioBase64, audioContentType }
        │
   browser: refresh chat + play the reply
```

- Backend interfaces `SpeechToTextProvider` / `TextToSpeechProvider`
  (`com.booki.voice`), credentials **server-side only**. First implementation is
  OpenAI-compatible; both report `isConfigured()`.
- `GET /api/voice/capabilities` → `{stt, tts}` — the frontend calls this on load
  and picks the cloud path or the browser fallback up front.
- **STT failure fails the turn** (`VoiceTranscriptionException` → 502). **TTS
  failure is best-effort** — the text reply is already persisted and returned;
  the client shows text (or, on Chromium, browser `speechSynthesis`).
- **Session language drives STT/TTS.** No hardcoded `es-ES` anywhere.
- **Raw audio is never persisted** — it lives only for the request. Upload capped
  at `booki.voice.max-audio-bytes` (10 MB); TTS input capped at
  `tts-max-input-chars` (1200).
- The frontend captures audio with `getUserMedia` + `MediaRecorder` (universal
  browser support). `SpeechRecognition` (`useVoice`) is kept **only** as a
  fallback for browsers without `MediaRecorder` or deployments with no STT
  provider; it follows the session language and still posts through
  `POST /messages` with `InputType.VOICE`.
- Not streaming yet: a voice turn is STT + conversation + TTS, all synchronous,
  and the reply audio is base64 in the JSON body. Fine for "press, speak, hear
  the answer". See "Streaming" below.

## Browser support

BooKI targets Android / Windows / Linux through responsive web / PWA. What works
where, as of Phases 1–5:

| Feature | Chrome / Edge | Firefox | Safari | Notes |
|---|---|---|---|---|
| Everything text (chat, quick actions, quiz, progress, reports) | ✅ | ✅ | ✅ | plain REST, no special APIs |
| **Voice — cloud path** (record audio → backend transcribes → spoken reply) | ✅ | ✅ | ✅ | uses `getUserMedia` + `MediaRecorder`, supported everywhere modern; needs an STT provider configured on the backend (see setup below) |
| **Voice — browser fallback** (`SpeechRecognition`) | ✅ | ❌ | ⚠️ patchy | **plan B only** — used just when the browser has no `MediaRecorder` or the backend has no STT provider. Not the architecture. |

So: the app is fully usable on every modern browser. The only Chromium-only
piece is the *fallback* recognizer, and it is never the primary path. This is the
whole point of ADR-009 superseding ADR-002.

## Streaming

"Streaming" = BooKI's reply appearing word-by-word as the model writes it
(like ChatGPT), instead of all at once after a pause.

**BooKI does not do this yet, on purpose.** Phase 5 was *preparation only*
(ADR-010): the backend provider and engine interfaces were shaped so a streaming
implementation can be added later without re-architecting anything —
`AiProvider.converse()`, `ConversationEngine.converse()` and every HTTP endpoint
are unchanged, and no code path calls the new streaming interfaces yet
(`StreamingAiProvider`, `ConversationEngine.converseStreaming`,
`Streaming{TextToSpeech,SpeechToText}Provider`).

When a concrete low-latency requirement appears, the remaining work is: add an
**SSE** endpoint (Server-Sent Events — supported by every browser, no WebSocket
needed) and a native streaming method on one provider. Nothing about this
changes browser support.

## Voice provider setup

The `*_MODEL` / `*_VOICE` values are **plain identifiers from the provider's
docs**, not secrets — you pass them as-is. The only thing you must obtain is an
API key with billing enabled. With just the key, the defaults below work
out of the box.

### OpenAI (default implementation)

1. [platform.openai.com](https://platform.openai.com) → **Settings → Billing** →
   add credit (audio calls need billing; without it you get
   `insufficient_quota`).
2. **API keys → Create new secret key** → `sk-proj-…`.
3. `.env`: `OPENAI_API_KEY=sk-proj-…` (same key serves chat and voice —
   `booki.voice.openai.api-key` already points at `${OPENAI_API_KEY}`).

| Env var | Default | Alternatives | Notes |
|---------|---------|--------------|-------|
| `VOICE_STT_MODEL` | `whisper-1` | `gpt-4o-mini-transcribe`, `gpt-4o-transcribe` | `whisper-1` is cheapest and takes the `language` param; the `gpt-4o-*` transcribe better at higher cost |
| `VOICE_TTS_MODEL` | `gpt-4o-mini-tts` | `tts-1` (fastest), `tts-1-hd` (best audio) | |
| `VOICE_TTS_VOICE` | `alloy` | `echo`, `fable`, `onyx`, `nova`, `shimmer`, `ash`, `ballad`, `coral`, `sage`, `verse` | preview at [openai.fm](https://www.openai.fm) |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | any OpenAI-compatible endpoint | see Groq below |
| `VOICE_MAX_AUDIO_BYTES` | `10485760` | | reject oversized uploads before STT |
| `VOICE_TTS_MAX_INPUT_CHARS` | `1200` | | truncate long replies before synthesis |

Rough cost (subject to change — check OpenAI's pricing page): STT `whisper-1`
≈ $0.006/min; TTS `gpt-4o-mini-tts` ≈ $0.015/1000 chars. A typical voice turn is
under $0.01. Always confirm current model names and prices against
[platform.openai.com/docs/models](https://platform.openai.com/docs/models).

### Groq (free-tier STT, OpenAI-compatible)

`OpenAiSpeechToTextProvider` takes a configurable base URL, so it can talk to
Groq:

```
OPENAI_BASE_URL=https://api.groq.com/openai/v1
OPENAI_API_KEY=<groq key>
VOICE_STT_MODEL=whisper-large-v3
```

Groq has **no TTS** — leave the TTS side effectively disabled and BooKI replies
in text only (or the browser reads it aloud on Chromium).

### Other providers

Deepgram, Azure, Google Cloud Speech, ElevenLabs, local Whisper/Piper, etc. each
need a new bean implementing `SpeechToTextProvider` / `TextToSpeechProvider`. The
interfaces are already shaped for it (and for a future streaming method).

## Example interactions

- "BooKI, I didn't understand this part." → `explain`
- "Ask me a question about what I just read." → `quiz`
- "Give me a hint." → ordinary chat turn
- "Summarize these pages." → `summary`
- "Help me remember this." → `mnemonic`

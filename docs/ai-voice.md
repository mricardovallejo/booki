# AI and voice in BooKI

## AI strategy

- Interface: `AiProvider.converse(systemPrompt, context, userMessage)`- Only implementation today: **OpenAI** (`gpt-4o-mini`), in `OpenAiProvider`.
- Config-ready for **Kimi** (`AI_PROVIDER=kimi`, `KIMI_API_KEY`) but there's no `KimiProvider` class yet — setting that env var currently breaks startup (see `docs/backend.md`).
- If the OpenAI call fails for any reason (missing/invalid key, network error, etc.), `OpenAiProvider` silently returns a canned "couldn't reach the assistant" message instead of raising an error the frontend can distinguish — worth keeping in mind when a session's replies all look identical/generic.
- The system prompt is built dynamically (`SessionServiceImpl.buildSystemPrompt`) from:
  - BooKI's base prompt, which also names the session's language (English/Spanish/French) for the model to reply in.
  - The Profile Master's system prompt, if one was chosen for the session.
  - What the reader has written about themselves in their own profile (`User.systemPrompt`), if anything.
  - The session's difficulty.
  - The document title.
  - The page range and current page.
  - Text extracted from the page range.

## Example interactions

- "BooKI, I didn't understand this part."
- "Explain it to me like I'm 12."
- "Ask me a question."
- "Give me a hint."
- "Summarize these pages."

## Voice strategy

The MVP uses the **browser's Web Speech API**:

- `SpeechRecognition` for STT.
- `speechSynthesis` for TTS (not yet implemented, but ready to add).
- Advantages: no cost, no extra network dependencies, works in all modern browsers.
- Limitations: variable accuracy; `useVoice` currently hardcodes `recognition.lang = 'es-ES'`, so speech-to-text is only reliable in Spanish today even though sessions can be set to English or French.

## Future alternatives

- Local Whisper for STT.
- TTS via ElevenLabs, OpenAI TTS, or local Piper.
- Backend-side audio processing for devices without the Web Speech API.

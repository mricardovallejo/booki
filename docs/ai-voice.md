# AI and voice in BooKI

## AI strategy

- Interface: `AiProvider.converse(systemPrompt, context, userMessage)`.
- Initial implementation: **OpenAI** (`gpt-4o-mini`).
- Ready for **Kimi** via a second implementation, selected with a conditional property.
- The system prompt is built dynamically from:
  - BooKI's base prompt.
  - The Profile Master's system prompt.
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
- Limitations: variable accuracy, language currently fixed at the reader's chosen locale.

## Future alternatives

- Local Whisper for STT.
- TTS via ElevenLabs, OpenAI TTS, or local Piper.
- Backend-side audio processing for devices without the Web Speech API.

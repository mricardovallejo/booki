# BooKI Frontend

## Technologies

- React 18 + TypeScript
- Vite (dev server + build)
- Tailwind CSS
- react-pdf for PDF rendering
- Web Speech API for voice (STT/TTS)
- PWA via vite-plugin-pwa

## Screens

- **HomePage**: list of the user's PDFs and an upload button.
- **SessionPage**: PDF reader + chat/voice drawer.

## Main components

- `Layout`: top bar and route container.
- `PdfViewer`: displays the PDF and lets you move between pages.
- `ChatDrawer`: side panel with history and message/voice input.
- `VoiceButton`: triggers SpeechRecognition and sends the transcript.

## Voice flow

1. The user taps the microphone button.
2. `useVoice` uses the browser's `SpeechRecognition`.
3. The transcript is sent as a `VOICE`-type message.
4. The backend responds with text.
5. The frontend can use `speechSynthesis.speak()` to read the response aloud (future work).

## Dev proxy

Vite forwards `/api` to `http://localhost:8080`.

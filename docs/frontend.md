# Frontend BooKI

## Tecnologías

- React 18 + TypeScript
- Vite (dev server + build)
- Tailwind CSS
- react-pdf para renderizar PDF
- Web Speech API para voz (STT/TTS)
- PWA via vite-plugin-pwa

## Pantallas

- **HomePage**: lista de PDFs del usuario y botón de subida.
- **SessionPage**: lector de PDF + drawer de chat/voz.

## Componentes principales

- `Layout`: barra superior y contenedor de rutas.
- `PdfViewer`: muestra el PDF y permite avanzar página.
- `ChatDrawer`: panel lateral con historial y entrada de mensaje/voz.
- `VoiceButton`: activa SpeechRecognition y envía el transcript.

## Flujo de voz

1. Usuario pulsa el botón de micrófono.
2. `useVoice` usa `SpeechRecognition` del navegador.
3. El transcript se envía como mensaje tipo `VOICE`.
4. El backend responde con texto.
5. El frontend puede usar `speechSynthesis.speak()` para leer la respuesta (implementación futura).

## Proxy de desarrollo

Vite redirige `/api` a `http://localhost:8080`.

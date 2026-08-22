# IA y voz en BooKI

## Estrategia de IA

- Interfaz `AiProvider.converse(systemPrompt, context, userMessage)`.
- Implementación inicial: **OpenAI** (`gpt-4o-mini`).
- Preparado para **Kimi** con una segunda implementación y condicional por propiedad.
- El system prompt se construye dinámicamente con:
  - Prompt base de BooKI.
  - System prompt del Profile Master.
  - Dificultad de la sesión.
  - Título del documento.
  - Rango de páginas y página actual.
  - Texto extraído del rango de páginas.

## Ejemplos de interacción

- "BooKI, no entendí esta parte."
- "Explícamelo como si tuviera 12 años."
- "Hazme una pregunta."
- "Dame una pista."
- "Resume estas páginas."

## Estrategia de voz

Para el MVP se usa la **Web Speech API del navegador**:

- `SpeechRecognition` para STT.
- `speechSynthesis` para TTS (aún no implementado, pero listo para agregar).
- Ventajas: sin coste, sin dependencias de red extra, funciona en todos los navegadores modernos.
- Limitaciones: precisión variable, idioma fijo a `es-ES`.

## Alternativas futuras

- Whisper local para STT.
- TTS con ElevenLabs, OpenAI TTS o Piper local.
- Procesamiento de audio en backend para dispositivos sin Web Speech API.

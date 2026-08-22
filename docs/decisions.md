# Architecture decisions (ADRs)

## ADR-001: React + PWA instead of Flutter

- **Context**: cross-platform support was required (Linux, Raspberry Pi, Android, Mac tablet).
- **Decision**: React + Vite + PWA.
- **Reasons**: the developer already knows React/Angular, deployment is instant in any browser, no app store submissions are needed, and the Web Speech API simplifies voice.
- **Consequence**: the experience isn't 100% native, but it's stable and accessible enough to validate the MVP.

## ADR-002: Web Speech API for voice

- **Context**: fast voice support was needed without backend complexity.
- **Decision**: browser-side STT with `SpeechRecognition`; TTS pending via `speechSynthesis`.
- **Reasons**: reduces latency, zero cost, no need to send binary audio.
- **Consequence**: depends on the browser; can migrate to a backend implementation later.

## ADR-003: MySQL for dev, H2 for tests

- **Context**: a relational database with structured data was needed.
- **Decision**: MySQL via Docker Compose for development; in-memory H2 for tests.
- **Reasons**: dev/prod parity with MySQL; fast, isolated tests with H2.

## ADR-004: Local storage for PDFs

- **Context**: PDF file uploads in the MVP.
- **Decision**: store the file on disk (`./uploads`) and extract text into the database per page.
- **Reasons**: simple, avoids MinIO/S3 in the MVP, allows serving the PDF directly.
- **Consequence**: doesn't scale to multiple replicas; will migrate to object storage later.

## ADR-005: OpenAI as the initial AI provider

- **Context**: basic Kimi and OpenAI packages are available.
- **Decision**: OpenAI by default, behind an abstract interface for swapping providers.
- **Reasons**: stable API, extensive documentation, an affordable model (`gpt-4o-mini`).
- **Consequence**: can be changed via configuration without touching session logic.

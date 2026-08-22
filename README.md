# BooKI

Lector de PDF con sesiones por rango de páginas y un asistente contextual de aprendizaje y discusión, controlable por texto y voz.

## Estructura del monorepo

```
booki/
├── backend/          # Spring Boot 3.3 + Java 21 + Gradle
├── frontend/         # React + TypeScript + Vite + PWA
├── docs/             # Visión, arquitectura y memoria para agentes
├── docker-compose.yml
├── .env.example
└── README.md
```

> Practical guide to ports, ways to start each server, and how to stop them: [docs/local-dev.md](docs/local-dev.md).

## Requisitos

- Java 21
- Gradle (incluido wrapper en `backend/gradlew`)
- Node.js 20+ y npm (para el frontend)
- Docker y Docker Compose **opcional** (para MySQL; también puedes usar H2)
- API key de OpenAI (o Kimi) para el asistente

## Arranque rápido con H2 (sin Docker)

### 1. Backend

```bash
cd backend
./gradlew bootRunLocal
```

Usa el perfil `local` con H2 en archivo (`~/booki-local-db`). El backend escucha en `http://localhost:8080`.

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

El frontend escucha en `http://localhost:5173`.

## Arranque con MySQL + Docker

```bash
cp .env.example .env
docker compose up -d
cd backend && ./gradlew bootRun
cd frontend && npm run dev
```

## Flujo de prueba

1. Abre `http://localhost:5173`.
2. Regístrate con email/password (falta UI de auth; usa Postman o similar con `POST /api/auth/register`).
3. Sube un PDF desde la pantalla principal.
4. Haz clic en un libro para crear una sesión (elegir rango, dificultad y Profile Master).
5. Abre la sesión y conversa con BooKI por texto o voz.

## Configuración de IA

Edita `.env` o variables de entorno:

```bash
AI_PROVIDER=openai
OPENAI_API_KEY=sk-...
```

Para usar Kimi:

```bash
AI_PROVIDER=kimi
KIMI_API_KEY=...
```

## Perfiles de memoria para agentes

- `docs/vision.md` — visión del producto y principios.
- `docs/architecture.md` — stack y estructura.
- `docs/backend.md` — detalles del backend.
- `docs/frontend.md` — detalles del frontend.
- `docs/ai-voice.md` — estrategia de IA y voz.
- `docs/decisions.md` — decisiones arquitectónicas.
- `docs/agent-memory.md` — resumen compacto.

## Estado del MVP

Estructura base lista para iterar. Pendientes inmediatos:

- [x] UI para crear sesión seleccionando rango de páginas.
- [x] Mejorar UX del lector y del chat.
- [ ] UI de login/registro en el frontend.
- [ ] Integrar TTS con Web Speech API.
- [ ] Tests de integración con H2.

# Arquitectura de BooKI

## Repositorio

Monorepo multiproyecto:

```
booki/
├── backend/          # Spring Boot 3.3 + Java 21 + Gradle
├── frontend/         # React + TypeScript + Vite + PWA
├── docs/             # Memoria técnica y funcional
├── docker-compose.yml
├── .env.example
└── README.md
```

## Stack

| Capa | Tecnología |
|------|------------|
| Backend | Spring Boot 3.3, Spring Security, JWT Bearer, JPA, Flyway, WebClient |
| Base de datos | MySQL (dev/prod), H2 (tests) |
| PDFs | Apache PDFBox para extracción de texto; archivo almacenado en disco |
| Frontend | React 18, TypeScript, Tailwind CSS, Vite, PWA, react-pdf |
| Voz | Web Speech API en el navegador (STT/TTS) |
| IA | Interfaz `AiProvider`; proveedor inicial OpenAI, preparado para Kimi |

## Capas del backend

- `controller` → REST controllers
- `service` → interfaces de servicio
- `service/impl` → implementaciones
- `domain` → entidades JPA
- `repository` → Spring Data JPA
- `security` → JWT util y filtro
- `ai` → proveedores de IA
- `dto` → request/response
- `config` → configuraciones de Spring

## Capas del frontend

- `src/pages` → pantallas (Home, Session)
- `src/components` → componentes reutilizables (Layout, PdfViewer, ChatDrawer, VoiceButton)
- `src/api` → llamadas al backend
- `src/hooks` → hooks personalizados (useVoice)
- `src/types` → TypeScript types

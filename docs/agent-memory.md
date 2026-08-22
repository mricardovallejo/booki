# Memoria compacta para agentes

## Qué estamos construyendo

BooKI MVP: lector de PDF con sesiones por rango de páginas y asistente IA contextual por texto/voz.

## Stack fijado

- Backend: Spring Boot 3.3, Java 21, Gradle, JPA, Flyway, MySQL (dev), H2 (tests), Spring Security + JWT.
- Frontend: React + TypeScript + Vite + Tailwind + PWA + react-pdf.
- Voz: Web Speech API en navegador.
- IA: `AiProvider` interface; OpenAI por defecto.

## Convenciones

- Código en inglés: `Document`, `Session`, `ProfileMaster`, `Message`.
- Package base: `com.booki`.
- Backend: Controller → Service (interface) → ServiceImpl → Repository.
- Frontend: `pages/` → `components/` → `api/` → `hooks/`.

## Qué NO hacer sin preguntar

- No agregar microservicios.
- No cambiar la base de datos sin consensuar.
- No introducir dependencias pesadas nuevas.
- No modificar los principios de producto (lectura nunca bloqueada, no LMS).

## Próximos pasos habituales

1. Levantar MySQL con Docker Compose.
2. Ejecutar backend en dev.
3. Instalar dependencias del frontend con npm y levantar dev server.
4. Probar flujo: registro → subida PDF → crear sesión → conversar.

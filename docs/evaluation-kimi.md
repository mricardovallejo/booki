# Evaluación Kimi — Proyecto BooKI

**Fecha:** 2026-09-02  
**Alcance:** backend (Spring Boot) + frontend (React/TypeScript/Vite).  
**Mandato:** solo lectura; sin cambios de código.  
**Método:** revisión de la documentación del repo (`docs/*.md`, `README.md`, `openapi.yaml`), exploración manual del código y análisis con subagentes especializados.

---

## 1. Resumen ejecutivo

BooKI es un monorepo con un backend Spring Boot 4.1 (Java 21) y un frontend React 18 + TypeScript + Vite + PWA. El producto es un asistente conversacional para leer PDFs, con sesiones por rango de páginas, chat/voz, quiz, resúmenes y "Profile Masters". La arquitectura general está bien pensada: motor de conversación transport-agnostic, proveedores de IA intercambiables, almacenamiento detrás de una interfaz (`StorageAdapter`), y una separación clara de capas en el backend.

**Puntos fuertes destacados:**

- Backend con buena separación de responsabilidades (controller → service interface → impl → repository → domain).
- `ConversationEngine` es genuinamente neutral al transporte y gestiona correctamente los fallos de proveedores de IA sin persistir respuestas falsas.
- La voz se procesa en el backend, manteniendo credenciales server-side y unificando texto/voz en un mismo modelo `Message`.
- El frontend tiene tipado estricto, separación api/hooks/components, y manejo consistente de errores con `getErrorMessage`.
- Flyway, perfiles (`dev`/`local`/`test`), Dockerfile multi-stage, Docker Compose y GitHub Actions están presentes.

**Riesgos críticos que deben abordarse antes de escalar o abrir a más usuarios:**

1. **Seguridad:** JWT en `localStorage`, secret JWT por defecto predecible, `/actuator` público, posible inyección de prompts, y endpoints de autenticación sin rate-limiting.
2. **Calidad/robustez:** falta `@Transactional` en muchos métodos de servicio (riesgo de `LazyInitializationException` y escrituras no atómicas), timeouts ausentes en todos los `WebClient`, cobertura de tests muy baja (~4 archivos de test para ~80 archivos de producción).
3. **Mantenimiento:** sin configuración ESLint (el script `npm run lint` falla), credenciales demo en el frontend, y endpoints con `ResponseEntity<Object>` o `Map<String, Integer>` que pierden el contrato tipado.

En resumen: el proyecto es **funcional y bien arquitectónico para un MVP**, pero necesita endurecimiento de seguridad, transacciones, tests y deuda técnica antes de pasar a producción pública.

---

## 2. Backend

### 2.1 Arquitectura y calidad de código

**Lo que está bien:**

- Estructura de paquetes coherente con `docs/architecture.md` y `docs/backend.md`: `controller`, `service`/`service/impl`, `domain`, `repository`, `dto`, `config`, `security`, `ai`, `conversation`, `voice`, `storage`.
- Uso consistente de inyección por constructor + Lombok `@RequiredArgsConstructor`.
- `ConversationEngine` (`backend/src/main/java/com/booki/conversation/ConversationEngine.java`) centraliza el flujo conversacional y es transport-neutral; `SessionServiceImpl` y `VoiceConversationService` son adaptadores finos.
- `AiProviderRegistry` (`backend/src/main/java/com/booki/ai/AiProviderRegistry.java`) permite la selección de proveedor por sesión.
- `StorageAdapter` (`backend/src/main/java/com/booki/storage/StorageAdapter.java`) es una buena costura para cambiar entre disco local y S3/GCS/R2/MinIO sin tocar el resto del código.
- `CapabilityRegistry` (`backend/src/main/java/com/booki/conversation/capability/CapabilityRegistry.java`) implementa un enrutamiento provider-neutral de capacidades conversacionales, aunque con limitaciones de seguridad (ver §3.5).

**Problemas concretos:**

| # | Archivo(s) | Línea(s) | Problema | Severidad |
|---|------------|----------|----------|-----------|
| 1 | `service/impl/SessionServiceImpl.java` | 105, 110, 146, 151 | Métodos de lectura acceden a asociaciones LAZY (`session.getDocument().getTitle()`, `session.getProfileMaster()`) sin `@Transactional(readOnly = true)`. Riesgo real de `LazyInitializationException` en producción. | Alta |
| 2 | `service/impl/DocumentServiceImpl.java` | 46 | `uploadDocument` no es transaccional: guarda en storage, luego documento, luego páginas. Un fallo intermedio deja objetos huérfanos. | Alta |
| 3 | `service/impl/AuthServiceImpl.java` | 26 | `register` guarda el usuario y luego siembra los Profile Masters; si falla la siembra, el usuario queda sin masters. | Media |
| 4 | `service/impl/ReportServiceImpl.java` | 65, 101, 146 | Los métodos `sendProgressReport`/`sendQuizReport` generan el PDF y luego guardan la fila; no son transaccionales y tampoco envían email (los nombres son engañosos). | Media |
| 5 | `service/impl/SessionProgressCalculator.java` | 26-27 | Carga listas completas de mensajes e intentos solo para contarlas. Debería usar `countBySessionId`. | Media |
| 6 | `service/impl/SessionContextBuilder.java` | 49 | Recarga el `User` desde el repositorio aunque `session.getUser()` ya tiene la FK. | Baja |
| 7 | `controller/SessionController.java` | 49-53 | `updateCurrentPage` acepta `Map<String, Integer>` en lugar de un DTO tipado. | Media |
| 8 | `controller/SessionController.java` | 97 | `generateSummary` devuelve `ResponseEntity<Object>`, perdiendo tipado y precisión en OpenAPI. | Media |
| 9 | Varios controllers | varias | Muchos endpoints de escritura no usan `@Valid` en el body: `ProfileMasterController.update`, `UserController.updateCurrentUser`, `QuizController.submitAnswer`, `TagController.rename`, `SessionController.updateCurrentPage`, etc. | Media |
| 10 | `config/OpenApiConfig.java` | 27 | Título del OpenAPI es `"kobi-backend-api"` en lugar de `"booki-backend-api"` (typo). | Baja |
| 11 | `domain/Session.java` / `domain/ProfileMaster.java` | — | `configJson` existe y siempre se guarda como `"{}"`; es peso muerto. `Session.completedAt` también existe sin lógica. | Baja |
| 12 | `domain/*.java` | — | No hay restricciones `CHECK` a nivel de base de datos para columnas enum (`messages.speaker`, `sessions.difficulty`, etc.). | Baja |

### 2.2 Seguridad

**Lo que está bien:**

- Contraseñas hasheadas con BCrypt (`config/SecurityConfig.java:59-61`).
- JWT stateless, sin sesiones en servidor (`SecurityConfig.java:43`).
- Emails normalizados a minúsculas (`service/impl/AuthServiceImpl.java:53-55`).
- Checks de propiedad consistentes mediante `findByIdAndUserId` en repositorios.
- Las claves de proveedores de IA y voz son server-side únicamente.
- `LocalStorageAdapter` previene path traversal (`storage/LocalStorageAdapter.java:74-80`).

**Riesgos y vulnerabilidades:**

| # | Archivo(s) | Línea(s) | Problema | Severidad |
|---|------------|----------|----------|-----------|
| 1 | `src/main/resources/application.yml` | 45 | `JWT_SECRET` tiene un valor por defecto predecible y público. Si alguien despliega sin cambiarlo, los tokens se pueden firmar/validar por cualquiera. | Crítica |
| 2 | `config/SecurityConfig.java` | 45-50 | `/actuator/**` es público (`anyRequest().permitAll()`), y `management.endpoint.health.show-details: always` expone detalles de DB, storage, disco, SSL, etc. | Alta |
| 3 | `security/JwtUtil.java` | 53-63 | `extractEmail`/`extractUserId` llaman a `parseToken` sin try/catch; un payload malformado puede lanzar excepción no controlada. | Media |
| 4 | `security/JwtAuthenticationFilter.java` | 35-46 | No verifica que el usuario aún exista en la BD; un token de un usuario borrado sigue siendo válido hasta su expiración. | Media |
| 5 | `service/impl/AuthServiceImpl.java` | 29 | `"Email already registered"` permite enumeración de cuentas; debería devolver el mismo mensaje genérico que el login. | Media |
| 6 | `config/SecurityConfig.java` | 64-73 | CORS: `allowedHeaders(List.of("*"))` + `allowCredentials(true)` es permisivo; además se aplica a `/**` incluyendo `/actuator`. | Media |
| 7 | `config/GlobalExceptionHandler.java` | 95-99 | El handler de `RuntimeException` devuelve `ex.getMessage()` al cliente, pudiendo filtrar rutas, nombres de buckets, clases internas, etc. | Alta |
| 8 | `service/impl/SessionContextBuilder.java` / `ai/*` / `conversation/capability/*` | varias | El contenido del PDF, `User.systemPrompt`, `ProfileMaster.systemPrompt` y mensajes del usuario se concatenan directamente en prompts sin delimitadores ni instrucciones defensivas. Permite inyección de prompts. | Alta |
| 9 | `conversation/capability/CapabilityRegistry.java` | 80-95 | El enrutamiento por JSON (`{"capability":"..."}`) depende del modelo; un mensaje del usuario puede inducir al modelo a emitir ese JSON y disparar una capacidad involuntariamente. | Media |
| 10 | `service/impl/DocumentServiceImpl.java` | 47-86 | No valida que el archivo subido sea PDF antes de entregarlo a PDFBox; un archivo de 50 MB no-PDF consume memoria/CPU. | Media |
| 11 | `voice/OpenAiSpeechToTextProvider.java` | 55 | Acepta el MIME type del navegador sin validar contra una lista blanca. | Baja |
| 12 | `ai/OpenAiCompatibleProvider.java` / `ClaudeProvider.java` / `voice/*` | varias | No hay timeouts/retries explícitos en los `WebClient`; una llamada colgada puede bloquear el hilo del request indefinidamente. | Alta |
| 13 | `build.gradle` | 76 | `springdoc-openapi` está en el classpath; `/swagger-ui/**` y `/v3/api-docs/**` no están restringidos en `SecurityConfig`. | Media |

### 2.3 Pruebas

- Solo hay 5 archivos de test para todo el backend:
  - `BackendApplicationTests.java` (solo carga contexto).
  - `ConversationEngineTest.java` y `ConversationEngineStreamingTest.java` (bien cubiertos).
  - `CapabilityRegistryTest.java`.
  - `VoiceConversationServiceTest.java`.
- **No hay:** tests de controllers, tests de seguridad, tests de repositorios (`@DataJpaTest`), tests de servicios (`DocumentServiceImpl`, `QuizServiceImpl`, `ReportServiceImpl`, etc.), tests de providers de IA ni tests de storage.
- El `Dockerfile` ejecuta `-x test`, lo cual es aceptable si CI los corre primero, pero riesgoso si alguien buildea directo para producción.

### 2.4 Configuración, build y despliegue

- `application.yml` está bien estructurado con perfiles `dev`/`local`/`test`.
- `build.gradle` carga `.env` del repo-root en `bootRun`/`bootRunLocal`, útil para desarrollo local.
- `Dockerfile` es multi-stage con usuario no-root; buena práctica.
- `.github/workflows/ci.yml` ejecuta `./gradlew test` y `npm ci && npm run build`.
- `.github/workflows/deploy.yml` despliega backend en Cloud Run y frontend en Firebase Hosting.
- `docker-compose.yml` levanta PostgreSQL 16 y MinIO con credenciales déviles documentadas (`booki`/`bookibooki`), solo para local.

**Problemas:**

- `spring.profiles.active: dev` por defecto (`application.yml:5`) podría activar credenciales de desarrollo en producción si no se sobreescribe.
- `Dockerfile:18` usa `-x test`.
- `deploy.yml:55` usa `--allow-unauthenticated`; esto es correcto para la API pública pero debe ir acompañado de autenticación JWT.
- `max-instances: 2` en Cloud Run limita costos, pero `min-instances: 0` implica cold starts.

---

## 3. Frontend

### 3.1 Arquitectura y calidad de código

**Lo que está bien:**

- Vite + React 18 + TypeScript con `strict`, `noUnusedLocals`, `noUnusedParameters`.
- Separación clara: `pages` → `components` → `api` → `hooks`, como documenta `docs/frontend.md`.
- `src/api/client.ts` centraliza Axios, añade el Bearer token y redirige a `/login` en 401.
- `src/lib/errors.ts` normaliza el manejo de errores de Axios.
- `src/config/endpoints.ts` es la única fuente de verdad para rutas del backend.
- `ProtectedRoute` y `AuthContext` gestionan la autenticación de forma centralizada.
- `tsc --noEmit` pasa.

**Problemas concretos:**

| # | Archivo(s) | Línea(s) | Problema | Severidad |
|---|------------|----------|----------|-----------|
| 1 | `package.json` | 10 | El script `lint` llama a ESLint, pero **no existe ninguna configuración de ESLint** en el repo. `npm run lint` falla inmediatamente. | Alta |
| 2 | `src/pages/LoginPage.tsx` | 11-12, 45-50, 101-102 | Credenciales demo (`demo@booki.app` / `password`) embebidas en el código fuente. Si la cuenta existe en producción, cualquiera puede entrar. | Alta |
| 3 | `src/pages/HomePage.tsx` | — | Componente muy grande que mezcla subida, búsqueda, ordenamiento, tags, modales y eliminación. Difícil de mantener. | Media |
| 4 | `src/components/DocumentCard.tsx` | 24-67 | Anida `<span role="button">` dentro de un `<button>`, generando HTML inválido y comportamiento de teclado poco fiable. | Media |
| 5 | `src/components/Layout.tsx` | 46-97 | Menú de usuario no se cierra con click fuera ni Escape; falta `aria-expanded`. | Media |
| 6 | `src/components/NotificationsBell.tsx` / `ContextInfoButton.tsx` | — | Popovers sin cierre fuera/Escape. | Baja |
| 7 | `src/pages/LoginPage.tsx` | 18 | `location.state as { from?: Location }` usa el tipo `Location` del DOM en lugar del de `react-router-dom`; funciona por casualidad. | Baja |
| 8 | `src/App.tsx` | 16 | Usa `"/"` hardcodeado mientras existe `ROUTES.home`; inconsistente. | Baja |
| 9 | `index.html` | 11 | Carga Google Fonts sin `&display=swap`, bloqueando first paint. | Baja |
| 10 | `src/index.css` | 5-14 | Variables CSS duplicadas en `tailwind.config.js`; doble fuente de verdad. | Baja |
| 11 | `src/pages/MastersPage.tsx` | — | Página también muy grande; podría dividirse en subcomponentes. | Baja |

### 3.2 Seguridad

**Lo que está bien:**

- No se usa `dangerouslySetInnerHTML` ni `eval` en el código fuente.
- `react-markdown` no permite HTML raw por defecto.
- El token no se loguea en desarrollo (`client.ts:26-54`).
- Vite solo expone variables `VITE_*` al bundle; secretos en otras variables no se filtran.

**Riesgos y vulnerabilidades:**

| # | Archivo(s) | Línea(s) | Problema | Severidad |
|---|------------|----------|----------|-----------|
| 1 | `src/context/AuthContext.tsx` | 15, 28, 42, 55 | JWT almacenado en `localStorage`. Cualquier XSS en el origen puede exfiltrarlo. | Alta |
| 2 | `src/api/client.ts` | 11-24 | Lee `localStorage` en cada request en lugar de usar una referencia en memoria. | Media |
| 3 | `src/context/AuthContext.tsx` | 27-39 | Valida JSON.parse pero no la forma del objeto guardado. | Baja |
| 4 | `src/components/ChatPanel.tsx` | 198-209 | `react-markdown` + `remark-gfm` genera links sin `rel="noopener noreferrer"` ni `target="_blank"`, permitiendo tabnabbing. | Media |
| 5 | `src/config/endpoints.ts` / `vite.config.ts` | — | `API_BASE` usa `VITE_API_BASE_URL` en producción o `/api` (proxy) en desarrollo. No hay validación de HTTPS en producción. | Media |
| 6 | `src/pages/LoginPage.tsx` | 11-12, 45-50 | Credenciales demo en el bundle de producción. | Alta |
| 7 | `src/components/CreateSessionModal.tsx` | 48-72, 139, 160 | Valida `startPage` y `endPage` por separado; permite `startPage > endPage`. También hace `as SessionLanguage`/`as AiProvider` sin validar contra whitelist. | Media |
| 8 | `src/pages/ProfilePage.tsx` / `src/pages/MastersPage.tsx` | 59-66 / 137-145 | No hay límites de longitud en `systemPrompt`, `bio`, etc., antes de enviar al backend. | Baja |
| 9 | `src/pages/HomePage.tsx` / `src/api/documents.ts` | 26-37 / 7-13 | La subida no valida tamaño ni MIME real; confía en `accept="application/pdf"` que es trivial de saltar. | Media |
| 10 | `src/components/PdfViewer.tsx` | 63-65 | Envía el Bearer token en `httpHeaders` del PDF; si `API_BASE` fuese HTTP, el token viajaría sin cifrar. | Media |
| 11 | `firebase.json` | 1-7 | No configura headers de seguridad (CSP, X-Frame-Options, X-Content-Type-Options, HSTS). | Alta |
| 12 | `vite.config.ts` | 24-25 | `registerType: 'autoUpdate'` y `devOptions: { enabled: true }`. En producción autoUpdate puede empujar código sin confirmación del usuario; verificar que `devOptions` no afecte builds de producción. | Baja |
| 13 | `package.json` | — | Dependencias como `axios ^1.7.4`, `react-pdf ^9.1.0`, `vite ^5.4.1` y `vite-plugin-pwa ^0.20.1` tienen más de un año. Revisar con `npm audit`. | Media |

### 3.3 Datos y hooks

- No hay librería de caching/SWR. Varios componentes llaman `useSession(sessionId)` independientemente, generando requests duplicados (`ChatPanel.tsx:88`, `PdfViewer.tsx:20`).
- `useSessionContext.ts` no devuelve `error` ni `loading`; fallos quedan en silencio.
- `useDocuments.ts` `remove()` no gestiona error ni loading.
- `api/voice.ts:34-38` envía `wantsAudioReply` como campo de formulario, pero ese campo **no está documentado en `docs/openapi.yaml`** para `POST /sessions/{id}/voice`.
- `types/index.ts` tiene discrepancias menores con OpenAPI: `Message` no incluye `sessionId`, `Tag` no incluye `createdAt`.

### 3.4 Voz

- `useVoiceRecorder.ts` limpia correctamente el `MediaStream`.
- `useVoice.ts` sigue el idioma de la sesión.
- Problemas:
  - `useVoiceRecorder.ts:61-79` `stop()` no maneja el evento `onerror` del `MediaRecorder`.
  - `useVoice.ts:30-59` `onerror` resuelve `null` sin feedback al usuario.
  - `ChatPanel.tsx:121-144` no maneja explícitamente denegación de permisos de micrófono.
  - `ChatPanel.tsx:115-119` crea un nuevo `Audio` cada turno sin pausar el anterior; pueden solaparse.

---

## 4. Errores y riesgos críticos resumidos

1. **JWT inseguro por defecto** (`application.yml:45`). Si se despliega sin `JWT_SECRET`, la app es trivialmente vulnerable.
2. **Almacenamiento de token en `localStorage`**. Riesgo de XSS → robo de sesión.
3. **Actuator público con detalles**. Exposición de información interna sin autenticación.
4. **Sin transacciones** en servicios que hacen múltiples escrituras. Riesgo de datos inconsistentes.
5. **Sin timeouts en WebClient**. Llamadas a proveedores de IA/voz pueden colgar.
6. **Inyección de prompts**. El contenido del usuario/PDF se concatena directamente en prompts.
7. **Credenciales demo en el frontend**. Cuenta fácilmente explotable si existe en producción.
8. **Falta ESLint**. El script de lint falla, y no hay gate de calidad de código en CI.
9. **Cobertura de tests muy baja**. 5 archivos de test para todo el backend; ningún test de controllers/security/storage/providers.
10. **Headers de seguridad ausentes en Firebase Hosting**.

---

## 5. Recomendaciones priorizadas

### Inmediatas (antes de cualquier despliegue público)

1. **Exigir `JWT_SECRET` seguro:** eliminar el default en `application.yml` y fallar al arrancar si no está configurado (o generar uno aleatorio y advertir). Mínimo 256 bits.
2. **Mover token a cookie `HttpOnly` + `Secure` + `SameSite=Strict`:** el backend debe setearla y el frontend leer el estado de autenticación vía `/users/me` o un endpoint similar, eliminando `localStorage`.
3. **Proteger `/actuator`:** requerir autenticación o restringir por red; reducir `show-details` a `when-authorized`/`never`.
4. **Eliminar credenciales demo del frontend** o protegerlas bajo `import.meta.env.DEV`.
5. **Añadir configuración ESLint** y hacer que CI falle si `npm run lint` falla.
6. **Añadir `@Transactional`** a servicios con múltiples pasos y a lecturas que recorren asociaciones LAZY.
7. **Añadir timeouts** a todos los `WebClient` de proveedores IA/voz.
8. **Sanear el handler de `RuntimeException`** en `GlobalExceptionHandler.java` para no devolver `ex.getMessage()` al cliente.

### Corto plazo (1-2 sprints)

9. **Mitigar inyección de prompts:** delimitar bloques de contexto/documento/usuario en `SessionContextBuilder`, añadir instrucciones defensivas, y validar/escapar contenido de usuario antes de incluirlo.
10. **Rate-limiting** en endpoints de autenticación (Bucket4j / Spring Cloud Gateway / reverse proxy).
11. **Validar `@Valid`** en todos los endpoints de escritura y añadir `@Size`/`@Pattern` a DTOs de texto libre.
12. **Validar uploads:** rechazar archivos cuyo content-type no sea `application/pdf` y añadir límite de páginas/tamaño de texto extraído.
13. **Mejorar CORS:** lista blanca explícita de headers y validar que no se use `*` con credenciales.
14. **Añadir headers de seguridad** en `firebase.json`: CSP, X-Frame-Options, X-Content-Type-Options, HSTS.
15. **Ampliar cobertura de tests:** tests de controllers (`@WebMvcTest`), tests de repositorios (`@DataJpaTest`), tests de servicios con Mockito, y tests de seguridad (JWT, CORS, ownership).
16. **Agregar `HttpOnly` cookie auth** en backend y adaptar el interceptor de Axios para no enviar `Authorization` manualmente.

### Medio plazo

17. Implementar revocación de tokens o refresh tokens.
18. Añadir retry/backoff en llamadas a proveedores de IA.
19. Considerar reemplazar el enrutamiento por JSON del modelo por un enfoque más determinista (solo `capabilityHint` explícito del cliente).
20. Introducir SWR/React Query para deduplicar requests y mejorar UX.
21. Revisar y actualizar dependencias (`npm audit`, `npm outdated`).

---

## 6. Métricas cualitativas

| Área | Backend | Frontend | Notas |
|------|---------|----------|-------|
| Arquitectura | 8/10 | 7/10 | Backend muy bien estructurado; frontend claro pero con componentes grandes. |
| Calidad de código | 6/10 | 6/10 | Código limpio, pero falta transacciones, tipado laxo en algunos endpoints, y ESLint. |
| Seguridad | 5/10 | 5/10 | Autenticación básica funcional, pero múltiples riesgos críticos por hardening. |
| Tests | 3/10 | 2/10 | Muy pocos tests; sin ESLint y sin tests de UI en el frontend. |
| Buenas prácticas | 6/10 | 6/10 | Docker, CI, Flyway, perfiles; pero faltan gates de calidad y headers de seguridad. |
| Documentación | 9/10 | 8/10 | Documentación extensa y útil; OpenAPI y ADRs en buen estado (salvo typo del título). |

**Veredicto general:** BooKI es un MVP sólido y bien concebido, con arquitectura extensible y documentación clara. Para pasar a producción pública o escalar, el trabajo prioritario es **endurecimiento de seguridad, transacciones, tests y eliminación de deuda técnica** (ESLint, credenciales demo, tipado). Las recomendaciones están ordenadas por impacto y esfuerzo.

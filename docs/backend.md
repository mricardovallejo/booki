# Backend BooKI

## Perfiles

- `dev` (por defecto): MySQL en localhost:3306.
- `local`: H2 en archivo (`~/booki-local-db`) con `AUTO_SERVER=TRUE`. Ideal para desarrollo sin Docker.
- `test`: H2 en memoria, Flyway deshabilitado.

### Arranque local con H2

```bash
cd backend
./gradlew bootRunLocal
```

Esto levanta el backend en `http://localhost:8080` sin necesidad de MySQL ni Docker.

## Entidades principales

- `User`: email + password_hash.
- `Document`: metadatos del PDF subido por el usuario.
- `DocumentPage`: texto extraído por página.
- `ProfileMaster`: personalidad experta con system prompt.
- `Session`: rango de páginas, página actual y configuración.
- `Message`: historial de conversación (USER / BOOKI).

## API REST

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/auth/register` | Registro con email/password |
| POST | `/api/auth/login` | Login, devuelve JWT |
| GET | `/api/documents` | Listar PDFs del usuario |
| POST | `/api/documents` | Subir PDF (multipart) |
| GET | `/api/documents/{id}` | Metadatos del PDF |
| GET | `/api/documents/{id}/file` | Descargar/visualizar PDF |
| GET | `/api/profile-masters` | Listar masters activos |
| POST | `/api/sessions` | Crear sesión |
| GET | `/api/sessions/{id}` | Cargar sesión |
| PATCH | `/api/sessions/{id}/current-page` | Actualizar página actual |
| GET | `/api/sessions/{id}/messages` | Historial de mensajes |
| POST | `/api/sessions/{id}/messages` | Enviar mensaje a BooKI |

## Seguridad

- JWT Bearer token en header `Authorization`.
- Contraseñas hasheadas con BCrypt.
- CORS configurado para `http://localhost:5173`.

## Configuración de IA

Variables en `.env`:

```
AI_PROVIDER=openai
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
KIMI_API_KEY=...
```

La clase `AiProvider` es la interfaz; `OpenAiProvider` es la implementación inicial.

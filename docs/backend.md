# BooKI Backend

## Profiles

- `dev` (default): MySQL on localhost:3306.
- `local`: file-based H2 (`~/booki-local-db`) with `AUTO_SERVER=TRUE`. Ideal for development without Docker.
- `test`: in-memory H2, Flyway disabled.

### Running locally with H2

```bash
cd backend
./gradlew bootRunLocal
```

This starts the backend on `http://localhost:8080` without needing MySQL or Docker.

## Main entities

- `User`: email + password_hash.
- `Document`: metadata for the PDF uploaded by the user.
- `DocumentPage`: text extracted per page.
- `ProfileMaster`: expert persona with a system prompt.
- `Session`: page range, current page, and settings.
- `Message`: conversation history (USER / BOOKI).

## REST API

| Method | Route | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Register with email/password |
| POST | `/api/auth/login` | Login, returns a JWT |
| GET | `/api/documents` | List the user's PDFs |
| POST | `/api/documents` | Upload a PDF (multipart) |
| GET | `/api/documents/{id}` | PDF metadata |
| GET | `/api/documents/{id}/file` | Download/view the PDF |
| GET | `/api/profile-masters` | List active masters |
| POST | `/api/sessions` | Create a session |
| GET | `/api/sessions/{id}` | Load a session |
| PATCH | `/api/sessions/{id}/current-page` | Update the current page |
| GET | `/api/sessions/{id}/messages` | Message history |
| POST | `/api/sessions/{id}/messages` | Send a message to BooKI |

## Security

- JWT Bearer token in the `Authorization` header.
- Passwords hashed with BCrypt.
- CORS configured for `http://localhost:5173`.

## AI configuration

Variables in `.env`:

```
AI_PROVIDER=openai
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
KIMI_API_KEY=...
```

The `AiProvider` class is the interface; `OpenAiProvider` is the initial implementation.

# Decisiones arquitectónicas (ADRs)

## ADR-001: React + PWA en lugar de Flutter

- **Contexto**: se requería multiplataforma (Linux, Raspberry, Android, Mac tablet).
- **Decisión**: React + Vite + PWA.
- **Razones**: el usuario conoce React/Angular, el despliegue es inmediato en cualquier navegador, no requiere tiendas de apps, y Web Speech API simplifica la voz.
- **Consecuencia**: la experiencia no es 100% nativa, pero es lo suficientemente estable y accesible para validar el MVP.

## ADR-002: Web Speech API para voz

- **Contexto**: se necesitaba voz rápida sin complejidad de backend.
- **Decisión**: STT en el navegador con `SpeechRecognition`; TTS pendiente con `speechSynthesis`.
- **Razones**: reduce latencia, coste cero, no requiere enviar audio binario.
- **Consecuencia**: depende del navegador; en el futuro se puede migrar a backend.

## ADR-003: MySQL para dev, H2 para tests

- **Contexto**: base de datos relacional con datos estructurados.
- **Decisión**: MySQL en Docker Compose para desarrollo; H2 en memoria para tests.
- **Razones**: dev/prod similares con MySQL; tests rápidos y aislados con H2.

## ADR-004: Almacenamiento local de PDFs

- **Contexto**: subida de archivos PDF en MVP.
- **Decisión**: guardar el archivo en disco (`./uploads`) y extraer texto a base de datos por página.
- **Razones**: simple, evita MinIO/S3 en MVP, permite servir el PDF directamente.
- **Consecuencia**: no es escalable a múltiples réplicas; se migrará a almacenamiento objeto más adelante.

## ADR-005: OpenAI como proveedor inicial de IA

- **Contexto**: se dispone de paquetes básicos de Kimi y OpenAI.
- **Decisión**: OpenAI por defecto, con interfaz abstracta para cambiar.
- **Razones**: API estable, documentación amplia, modelo económico (`gpt-4o-mini`).
- **Consecuencia**: se puede cambiar por configuración sin tocar la lógica de sesión.

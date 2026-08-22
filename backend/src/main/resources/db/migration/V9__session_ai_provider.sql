-- Nullable: null means "use the app's configured default provider for this profile" (see application.yml).
ALTER TABLE sessions ADD COLUMN ai_provider VARCHAR(20);

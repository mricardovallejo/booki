-- Profile Masters move from global/shared to per-user: each user gets their
-- own editable copy, seeded from the original 4 defaults at registration.
-- The original V1 seed rows stay as templates (user_id IS NULL) — they are
-- never returned directly to clients, only copied from at registration time.
ALTER TABLE profile_masters ADD COLUMN user_id BIGINT;
ALTER TABLE profile_masters ADD FOREIGN KEY (user_id) REFERENCES users(id);

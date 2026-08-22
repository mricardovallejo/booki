CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    page_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS document_pages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    page_number INT NOT NULL,
    extracted_text LONGTEXT NOT NULL,
    FOREIGN KEY (document_id) REFERENCES documents(id),
    UNIQUE (document_id, page_number)
);

CREATE TABLE IF NOT EXISTS profile_masters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    system_prompt LONGTEXT NOT NULL,
    config_json TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    start_page INT NOT NULL,
    end_page INT NOT NULL,
    current_page INT NOT NULL,
    profile_master_id BIGINT,
    difficulty VARCHAR(20) NOT NULL,
    config_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (document_id) REFERENCES documents(id),
    FOREIGN KEY (profile_master_id) REFERENCES profile_masters(id)
);

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    speaker VARCHAR(20) NOT NULL,
    input_type VARCHAR(20) NOT NULL,
    message LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);

-- Seed profile masters
INSERT INTO profile_masters (name, description, system_prompt, config_json, is_active) VALUES
('Profesor de 3º de primaria de Quebec', 'Explica con paciencia, vocabulario sencillo y ejemplos del día a día.', 'Eres un profesor de tercer grado de primaria en Quebec. Explicas con calma, usas palabras sencillas, das ejemplos del día a día y animas al lector sin presionarlo.', '{}', TRUE),
('Profesor de ciencias', 'Explica conceptos científicos con rigor pero accesible.', 'Eres un profesor de ciencias apasionado. Explicas conceptos con rigor, usas analogías y conectas las ideas con el mundo real.', '{}', TRUE),
('Crítico literario', 'Analiza personajes, simbolismo y estilo narrativo.', 'Eres un crítico literario amable. Ayudas a analizar personajes, simbolismo, decisiones narrativas y el estilo del autor.', '{}', TRUE),
('Especialista en dificultades de aprendizaje', 'Adapta explicaciones a ritmos lentos y ofrece pistas claras.', 'Eres un especialista en dificultades de aprendizaje. Fraccionas la información, repites conceptos clave con otras palabras y das pistas muy claras.', '{}', TRUE);

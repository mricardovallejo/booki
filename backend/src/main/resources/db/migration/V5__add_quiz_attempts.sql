CREATE TABLE IF NOT EXISTS quiz_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    page_number INT NOT NULL,
    question LONGTEXT NOT NULL,
    answer LONGTEXT NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    profile_master_id BIGINT,
    correct BOOLEAN NOT NULL,
    score DOUBLE NOT NULL,
    feedback LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES sessions(id),
    FOREIGN KEY (profile_master_id) REFERENCES profile_masters(id)
);

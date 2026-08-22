CREATE TABLE IF NOT EXISTS tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS tag_documents (
    tag_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    PRIMARY KEY (tag_id, document_id),
    FOREIGN KEY (tag_id) REFERENCES tags(id),
    FOREIGN KEY (document_id) REFERENCES documents(id)
);

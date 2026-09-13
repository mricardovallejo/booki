package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, name = "file_path")
    private String filePath;

    @Column(nullable = false, name = "page_count")
    private Integer pageCount;

    /** The provider's file id for this PDF, set on first use with that provider. Null until then. */
    @Column(name = "claude_file_id")
    private String claudeFileId;

    @Column(name = "openai_file_id")
    private String openaiFileId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    private Instant createdAt;
}

package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "document_pages")
@Getter
@Setter
@NoArgsConstructor
public class DocumentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "document_id")
    private Document document;

    @Column(nullable = false, name = "page_number")
    private Integer pageNumber;

    @Column(nullable = false, columnDefinition = "TEXT", name = "extracted_text")
    private String extractedText;
}

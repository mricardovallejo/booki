package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "profile_masters")
@Getter
@Setter
@NoArgsConstructor
public class ProfileMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null only for the original seed templates copied into each new user's own set at registration. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT", name = "system_prompt")
    private String systemPrompt;

    @Column(nullable = false, columnDefinition = "TEXT", name = "config_json")
    private String configJson;

    @Column(nullable = false, name = "is_active")
    private Boolean isActive = true;
}

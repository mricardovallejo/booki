package com.booki.repository;

import com.booki.domain.AiProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiProfileRepository extends JpaRepository<AiProfile, Long> {

    List<AiProfile> findByUserIdOrderByIdAsc(Long userId);

    Optional<AiProfile> findByIdAndUserId(Long id, Long userId);

    Optional<AiProfile> findFirstByUserIdAndDefaultProfileTrueOrderByIdAsc(Long userId);

    long countByUserId(Long userId);
}

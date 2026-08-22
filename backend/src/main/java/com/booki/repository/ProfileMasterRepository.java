package com.booki.repository;

import com.booki.domain.ProfileMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileMasterRepository extends JpaRepository<ProfileMaster, Long> {
    List<ProfileMaster> findByUserIdOrderByIdAsc(Long userId);
    List<ProfileMaster> findByUserIdIsNull();
    Optional<ProfileMaster> findByIdAndUserId(Long id, Long userId);
}

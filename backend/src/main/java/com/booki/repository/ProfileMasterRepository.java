package com.booki.repository;

import com.booki.domain.ProfileMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileMasterRepository extends JpaRepository<ProfileMaster, Long> {
    List<ProfileMaster> findByIsActiveTrue();
}

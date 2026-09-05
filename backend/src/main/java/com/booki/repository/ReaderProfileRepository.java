package com.booki.repository;

import com.booki.domain.ReaderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReaderProfileRepository extends JpaRepository<ReaderProfile, Long> {

    /** The built-in read-only default (user NULL) + the given user's own; built-in first (lowest id). */
    @Query("select r from ReaderProfile r left join r.user u where u is null or u.id = :userId order by r.id asc")
    List<ReaderProfile> visibleTo(@Param("userId") Long userId);

    Optional<ReaderProfile> findByIdAndUserId(Long id, Long userId);

    Optional<ReaderProfile> findFirstByUserIsNull();
}

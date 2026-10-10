package com.booki.repository;

import com.booki.domain.AppProfileSlot;
import com.booki.domain.AppProfileSlotKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppProfileSlotRepository extends JpaRepository<AppProfileSlot, Long> {

    List<AppProfileSlot> findByUserId(Long userId);

    Optional<AppProfileSlot> findByUserIdAndSlot(Long userId, AppProfileSlotKey slot);
}

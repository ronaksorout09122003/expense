package com.expense.app.repository;

import com.expense.app.entity.UserSetting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingRepository extends JpaRepository<UserSetting, UUID> {

    Optional<UserSetting> findByUserId(UUID userId);
}

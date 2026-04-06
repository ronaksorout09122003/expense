package com.expense.app.repository;

import com.expense.app.entity.Household;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdRepository extends JpaRepository<Household, UUID> {

    Optional<Household> findByInviteCodeIgnoreCaseAndActiveTrue(String inviteCode);

    Optional<Household> findByIdAndActiveTrue(UUID id);
}

package com.expense.app.repository;

import com.expense.app.entity.HouseholdMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {

    List<HouseholdMember> findByUserIdAndActiveTrue(UUID userId);

    List<HouseholdMember> findByHouseholdIdAndActiveTrueOrderByCreatedAtAsc(UUID householdId);

    Optional<HouseholdMember> findByHouseholdIdAndUserIdAndActiveTrue(UUID householdId, UUID userId);

    Optional<HouseholdMember> findByHouseholdIdAndUserId(UUID householdId, UUID userId);

    boolean existsByHouseholdIdAndUserIdAndActiveTrue(UUID householdId, UUID userId);
}

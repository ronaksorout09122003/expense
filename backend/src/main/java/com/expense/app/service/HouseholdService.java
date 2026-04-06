package com.expense.app.service;

import com.expense.app.dto.CommonDtos;
import com.expense.app.dto.HouseholdDtos;
import com.expense.app.entity.AppUser;
import com.expense.app.entity.Household;
import com.expense.app.entity.HouseholdMember;
import com.expense.app.entity.TransactionEntity;
import com.expense.app.enums.HouseholdMemberRole;
import com.expense.app.exception.ApiException;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.AppUserRepository;
import com.expense.app.repository.HouseholdMemberRepository;
import com.expense.app.repository.HouseholdRepository;
import com.expense.app.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HouseholdService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final AppUserRepository appUserRepository;
    private final TransactionRepository transactionRepository;
    private final FinanceMathService financeMathService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public HouseholdDtos.CurrentHouseholdResponse current(UUID userId) {
        HouseholdMember membership = activeMembership(userId);
        if (membership == null) {
            return new HouseholdDtos.CurrentHouseholdResponse(false, null);
        }
        return new HouseholdDtos.CurrentHouseholdResponse(true, toHouseholdResponse(membership.getHousehold()));
    }

    @Transactional
    public HouseholdDtos.HouseholdResponse create(UUID userId, HouseholdDtos.CreateHouseholdRequest request) {
        require(activeMembership(userId) == null, "You already belong to a household");
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Household household = new Household();
        household.setOwnerUser(user);
        household.setName(request.name().trim());
        household.setInviteCode(generateInviteCode());
        household = householdRepository.save(household);

        HouseholdMember member = new HouseholdMember();
        member.setHousehold(household);
        member.setUser(user);
        member.setMemberRole(HouseholdMemberRole.OWNER);
        householdMemberRepository.save(member);
        auditService.log(user, "household", household.getId(), "CREATE", null, request);
        return toHouseholdResponse(household);
    }

    @Transactional
    public HouseholdDtos.HouseholdResponse join(UUID userId, HouseholdDtos.JoinHouseholdRequest request) {
        require(activeMembership(userId) == null, "Leave your current household before joining another");
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Household household = householdRepository.findByInviteCodeIgnoreCaseAndActiveTrue(request.inviteCode().trim())
            .orElseThrow(() -> new NotFoundException("Invite code not found"));
        HouseholdMember member = householdMemberRepository.findByHouseholdIdAndUserId(household.getId(), userId).orElseGet(HouseholdMember::new);
        member.setHousehold(household);
        member.setUser(user);
        if (member.getMemberRole() == null) {
            member.setMemberRole(HouseholdMemberRole.MEMBER);
        }
        member.setActive(true);
        householdMemberRepository.save(member);
        auditService.log(user, "household", household.getId(), "JOIN", null, request);
        return toHouseholdResponse(household);
    }

    @Transactional
    public CommonDtos.MessageResponse leave(UUID userId) {
        HouseholdMember membership = activeMembership(userId);
        if (membership == null) {
            return new CommonDtos.MessageResponse("No household to leave");
        }
        Household household = membership.getHousehold();
        List<HouseholdMember> activeMembers = householdMemberRepository.findByHouseholdIdAndActiveTrueOrderByCreatedAtAsc(household.getId());
        if (membership.getMemberRole() == HouseholdMemberRole.OWNER && activeMembers.size() > 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Remove or transfer the other members before the owner leaves");
        }
        membership.setActive(false);
        householdMemberRepository.save(membership);
        if (activeMembers.size() == 1) {
            household.setActive(false);
            householdRepository.save(household);
        }
        auditService.log(membership.getUser(), "household", household.getId(), "LEAVE", null, null);
        return new CommonDtos.MessageResponse("Household updated");
    }

    @Transactional(readOnly = true)
    public Household requireMembership(UUID userId, UUID householdId) {
        Household household = householdRepository.findByIdAndActiveTrue(householdId)
            .orElseThrow(() -> new NotFoundException("Household not found"));
        require(householdMemberRepository.existsByHouseholdIdAndUserIdAndActiveTrue(householdId, userId), "You are not a member of this household");
        return household;
    }

    private HouseholdMember activeMembership(UUID userId) {
        return householdMemberRepository.findByUserIdAndActiveTrue(userId).stream().findFirst().orElse(null);
    }

    private HouseholdDtos.HouseholdResponse toHouseholdResponse(Household household) {
        List<HouseholdDtos.HouseholdMemberResponse> members = householdMemberRepository.findByHouseholdIdAndActiveTrueOrderByCreatedAtAsc(household.getId()).stream()
            .map(member -> new HouseholdDtos.HouseholdMemberResponse(
                member.getUser().getId(),
                member.getUser().getFullName(),
                member.getUser().getEmail(),
                member.getMemberRole()
            ))
            .toList();
        List<TransactionEntity> sharedTransactions = transactionRepository.findByHouseholdIdAndSharedTrueAndDeletedAtIsNullOrderByTransactionAtDesc(household.getId());
        List<HouseholdDtos.SharedTransactionResponse> recentSharedTransactions = sharedTransactions.stream()
            .limit(12)
            .map(this::toSharedTransactionResponse)
            .toList();
        BigDecimal totalSharedSpend = sharedTransactions.stream()
            .map(TransactionEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HouseholdDtos.HouseholdResponse(
            household.getId(),
            household.getName(),
            household.getInviteCode(),
            members,
            recentSharedTransactions,
            financeMathService.normalize(totalSharedSpend)
        );
    }

    private HouseholdDtos.SharedTransactionResponse toSharedTransactionResponse(TransactionEntity transaction) {
        BigDecimal estimatedShare = financeMathService.normalize(transaction.getAmount()
            .divide(BigDecimal.valueOf(Math.max(transaction.getSharedParticipantCount(), 1)), 2, RoundingMode.HALF_UP));
        return new HouseholdDtos.SharedTransactionResponse(
            transaction.getId(),
            transaction.getUser().getFullName(),
            transaction.getTransactionType().name(),
            financeMathService.normalize(transaction.getAmount()),
            estimatedShare,
            transaction.getNote(),
            transaction.getTransactionAt(),
            Math.max(transaction.getSharedParticipantCount(), 1)
        );
    }

    private String generateInviteCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(8);
        for (int index = 0; index < 8; index++) {
            builder.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    private void require(boolean valid, String message) {
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }
}

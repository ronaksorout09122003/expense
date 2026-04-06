package com.expense.app.service;

import com.expense.app.dto.CounterpartyDtos;
import com.expense.app.entity.AppUser;
import com.expense.app.entity.Counterparty;
import com.expense.app.exception.ApiException;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.AppUserRepository;
import com.expense.app.repository.CounterpartyRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CounterpartyService {

    private final CounterpartyRepository counterpartyRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public List<CounterpartyDtos.CounterpartyResponse> list(UUID userId) {
        return counterpartyRepository.findByUserIdAndDeletedAtIsNullOrderByNameAsc(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    public CounterpartyDtos.CounterpartyResponse get(UUID userId, UUID id) {
        return counterpartyRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("Counterparty not found"));
    }

    @Transactional
    public CounterpartyDtos.CounterpartyResponse create(UUID userId, CounterpartyDtos.CounterpartyRequest request) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        ensureUnique(userId, request.name(), null);
        Counterparty counterparty = new Counterparty();
        counterparty.setUser(user);
        apply(counterparty, request, true);
        counterparty = counterpartyRepository.save(counterparty);
        auditService.log(user, "counterparty", counterparty.getId(), "CREATE", null, request);
        return toResponse(counterparty);
    }

    @Transactional
    public CounterpartyDtos.CounterpartyResponse update(UUID userId, UUID id, CounterpartyDtos.CounterpartyRequest request) {
        Counterparty counterparty = counterpartyRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Counterparty not found"));
        ensureUnique(userId, request.name(), id);
        CounterpartyDtos.CounterpartyResponse oldValue = toResponse(counterparty);
        apply(counterparty, request, counterparty.isActive());
        counterparty = counterpartyRepository.save(counterparty);
        auditService.log(counterparty.getUser(), "counterparty", counterparty.getId(), "UPDATE", oldValue, request);
        return toResponse(counterparty);
    }

    @Transactional
    public void deactivate(UUID userId, UUID id) {
        Counterparty counterparty = counterpartyRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
            .orElseThrow(() -> new NotFoundException("Counterparty not found"));
        counterparty.setActive(false);
        counterpartyRepository.save(counterparty);
        auditService.log(counterparty.getUser(), "counterparty", id, "DEACTIVATE", null, Map.of("active", false));
    }

    private void apply(Counterparty counterparty, CounterpartyDtos.CounterpartyRequest request, boolean defaultActive) {
        counterparty.setName(request.name().trim());
        counterparty.setCounterpartyType(request.counterpartyType());
        counterparty.setPhone(request.phone());
        counterparty.setNotes(request.notes());
        counterparty.setActive(request.active() != null ? request.active() : defaultActive);
    }

    private void ensureUnique(UUID userId, String name, UUID existingId) {
        boolean duplicate = counterpartyRepository.findByUserIdAndDeletedAtIsNullOrderByNameAsc(userId).stream()
            .anyMatch(counterparty -> counterparty.getName().trim().equalsIgnoreCase(name.trim())
                && (existingId == null || !counterparty.getId().equals(existingId)));
        if (duplicate) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A merchant or person with this name already exists");
        }
    }

    private CounterpartyDtos.CounterpartyResponse toResponse(Counterparty counterparty) {
        return new CounterpartyDtos.CounterpartyResponse(
            counterparty.getId(),
            counterparty.getName(),
            counterparty.getCounterpartyType(),
            counterparty.getPhone(),
            counterparty.getNotes(),
            counterparty.isActive()
        );
    }
}

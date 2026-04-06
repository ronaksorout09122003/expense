package com.expense.app.controller;

import com.expense.app.dto.CommonDtos;
import com.expense.app.dto.CounterpartyDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.CounterpartyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/counterparties")
@RequiredArgsConstructor
public class CounterpartyController {

    private final CounterpartyService counterpartyService;

    @GetMapping
    public List<CounterpartyDtos.CounterpartyResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return counterpartyService.list(principal.getUserId());
    }

    @GetMapping("/{id}")
    public CounterpartyDtos.CounterpartyResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return counterpartyService.get(principal.getUserId(), id);
    }

    @PostMapping
    public CounterpartyDtos.CounterpartyResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CounterpartyDtos.CounterpartyRequest request
    ) {
        return counterpartyService.create(principal.getUserId(), request);
    }

    @PutMapping("/{id}")
    public CounterpartyDtos.CounterpartyResponse update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody CounterpartyDtos.CounterpartyRequest request
    ) {
        return counterpartyService.update(principal.getUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public CommonDtos.MessageResponse deactivate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        counterpartyService.deactivate(principal.getUserId(), id);
        return new CommonDtos.MessageResponse("Counterparty deactivated");
    }
}

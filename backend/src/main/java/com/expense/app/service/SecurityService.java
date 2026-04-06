package com.expense.app.service;

import com.expense.app.dto.CommonDtos;
import com.expense.app.dto.SecurityDtos;
import com.expense.app.entity.AppUser;
import com.expense.app.entity.UserSetting;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.AppUserRepository;
import com.expense.app.repository.UserSettingRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final AppUserRepository appUserRepository;
    private final UserSettingRepository userSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public SecurityDtos.SecurityOverviewResponse overview(UUID userId) {
        AppUser user = loadUser(userId);
        UserSetting setting = loadSetting(userId);
        return new SecurityDtos.SecurityOverviewResponse(
            user.getPinHash() != null && !user.getPinHash().isBlank(),
            setting.isBiometricEnabled(),
            setting.isReminderEnabled(),
            setting.getSessionTimeoutMinutes()
        );
    }

    @Transactional
    public CommonDtos.MessageResponse setPin(UUID userId, SecurityDtos.PinRequest request) {
        AppUser user = loadUser(userId);
        user.setPinHash(passwordEncoder.encode(request.pin()));
        appUserRepository.save(user);
        auditService.log(user, "security", user.getId(), "SET_PIN", null, Map.of("hasPin", true));
        return new CommonDtos.MessageResponse("PIN updated");
    }

    @Transactional
    public CommonDtos.MessageResponse clearPin(UUID userId) {
        AppUser user = loadUser(userId);
        user.setPinHash(null);
        appUserRepository.save(user);
        auditService.log(user, "security", user.getId(), "CLEAR_PIN", null, Map.of("hasPin", false));
        return new CommonDtos.MessageResponse("PIN removed");
    }

    @Transactional(readOnly = true)
    public SecurityDtos.PinVerificationResponse verifyPin(UUID userId, SecurityDtos.PinRequest request) {
        AppUser user = loadUser(userId);
        boolean valid = user.getPinHash() != null && passwordEncoder.matches(request.pin(), user.getPinHash());
        return new SecurityDtos.PinVerificationResponse(valid, valid ? "PIN verified" : "Invalid PIN");
    }

    private AppUser loadUser(UUID userId) {
        return appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserSetting loadSetting(UUID userId) {
        return userSettingRepository.findByUserId(userId).orElseThrow(() -> new NotFoundException("Settings not found"));
    }
}

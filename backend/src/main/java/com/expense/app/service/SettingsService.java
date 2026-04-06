package com.expense.app.service;

import com.expense.app.dto.UserDtos;
import com.expense.app.entity.Account;
import com.expense.app.entity.UserSetting;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.AccountRepository;
import com.expense.app.repository.UserSettingRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserSettingRepository userSettingRepository;
    private final AccountRepository accountRepository;

    public UserDtos.SettingsResponse getSettings(UUID userId) {
        return toResponse(getSetting(userId));
    }

    @Transactional
    public UserDtos.SettingsResponse updateSettings(UUID userId, UserDtos.UpdateSettingsRequest request) {
        UserSetting setting = getSetting(userId);
        Account defaultAccount = null;
        if (request.defaultAccountId() != null) {
            defaultAccount = accountRepository.findByIdAndUserIdAndDeletedAtIsNull(request.defaultAccountId(), userId)
                .orElseThrow(() -> new NotFoundException("Default account not found"));
        }

        setting.setDefaultAccount(defaultAccount);
        setting.setDefaultCurrency(request.defaultCurrency().trim().toUpperCase());
        setting.setDateFormat(request.dateFormat().trim());
        setting.setBiometricEnabled(request.biometricEnabled());
        setting.setReminderEnabled(request.reminderEnabled());
        setting.setSessionTimeoutMinutes(Math.max(5, request.sessionTimeoutMinutes()));
        return toResponse(userSettingRepository.save(setting));
    }

    private UserSetting getSetting(UUID userId) {
        return userSettingRepository.findByUserId(userId)
            .orElseThrow(() -> new NotFoundException("User settings not found"));
    }

    private UserDtos.SettingsResponse toResponse(UserSetting setting) {
        return new UserDtos.SettingsResponse(
            setting.getId(),
            setting.getDefaultAccount() != null ? setting.getDefaultAccount().getId() : null,
            setting.getDefaultCurrency(),
            setting.getDateFormat(),
            setting.isBiometricEnabled(),
            setting.isReminderEnabled(),
            setting.getSessionTimeoutMinutes()
        );
    }
}

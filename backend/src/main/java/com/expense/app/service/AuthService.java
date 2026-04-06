package com.expense.app.service;

import com.expense.app.dto.AuthDtos;
import com.expense.app.dto.UserDtos;
import com.expense.app.entity.AppUser;
import com.expense.app.exception.ApiException;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.AppUserRepository;
import com.expense.app.security.JwtService;
import com.expense.app.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserBootstrapService userBootstrapService;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (appUserRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "A user with this email already exists");
        }

        AppUser user = new AppUser();
        user.setFullName(request.fullName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setMobile(request.mobile());
        user.setCurrencyCode("INR");
        user.setTimezone("Asia/Kolkata");
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = appUserRepository.save(user);
        userBootstrapService.initializeDefaults(user);

        return new AuthDtos.AuthResponse(
            jwtService.generateToken(new UserPrincipal(user)),
            toProfile(user),
            "Account created successfully"
        );
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password())
            );
        } catch (BadCredentialsException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
            .orElseThrow(() -> new NotFoundException("User not found"));

        return new AuthDtos.AuthResponse(
            jwtService.generateToken(new UserPrincipal(user)),
            toProfile(user),
            "Login successful"
        );
    }

    public AuthDtos.AuthResponse refresh(UserPrincipal principal) {
        AppUser user = appUserRepository.findById(principal.getUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
        return new AuthDtos.AuthResponse(
            jwtService.generateToken(new UserPrincipal(user)),
            toProfile(user),
            "Session refreshed"
        );
    }

    public UserDtos.ProfileResponse me(UserPrincipal principal) {
        AppUser user = appUserRepository.findById(principal.getUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
        return toProfile(user);
    }

    private UserDtos.ProfileResponse toProfile(AppUser user) {
        return new UserDtos.ProfileResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getMobile(),
            user.getCurrencyCode(),
            user.getTimezone()
        );
    }
}

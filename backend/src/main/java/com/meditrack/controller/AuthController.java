package com.meditrack.controller;

import com.meditrack.dto.EmailRequest;
import com.meditrack.dto.LoginRequest;
import com.meditrack.dto.ResetPasswordRequest;
import com.meditrack.dto.VerifyEmailRequest;
import com.meditrack.dto.RefreshTokenRequest;
import com.meditrack.dto.RegisterRequest;
import com.meditrack.dto.AuthResponse;
import com.meditrack.dto.MessageResponse;
import com.meditrack.exception.TooManyRequestsException;
import com.meditrack.security.LoginRateLimiter;
import com.meditrack.security.UserPrincipal;
import com.meditrack.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String rateLimitKey = "login:" + clientIp(httpRequest);
        if (!loginRateLimiter.isAllowed(rateLimitKey)) {
            throw new TooManyRequestsException("Too many login attempts. Please wait a minute and try again");
        }
        AuthResponse response = authService.login(request, httpRequest);
        loginRateLimiter.reset(rateLimitKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String rateLimitKey = "register:" + clientIp(httpRequest);
        if (!loginRateLimiter.isAllowed(rateLimitKey)) {
            throw new TooManyRequestsException("Too many registration attempts. Please wait a minute and try again");
        }
        authService.register(request);
        return ResponseEntity.ok(new MessageResponse(
                "Registration successful. We sent a verification link to your email. "
                        + "Verify it, then wait for an administrator to approve your account."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(new MessageResponse(authService.verifyEmail(request.getToken())));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        enforceMailRateLimit("resend", request.getEmail(), httpRequest);
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok(new MessageResponse(
                "If that address belongs to an unverified account, a new verification link has been sent."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        enforceMailRateLimit("forgot", request.getEmail(), httpRequest);
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(new MessageResponse(
                "If that address belongs to an account, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Password updated. You can now log in with your new password."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(Authentication authentication) {
        Integer userId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
        authService.logout(userId);
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    /** Limits mail-triggering endpoints per IP and per target address, so they can't be used to spam an inbox. */
    private void enforceMailRateLimit(String action, String email, HttpServletRequest request) {
        if (!loginRateLimiter.isAllowed(action + ":ip:" + clientIp(request))
                || !loginRateLimiter.isAllowed(action + ":mail:" + email.trim().toLowerCase())) {
            throw new TooManyRequestsException("Too many requests. Please wait a minute and try again");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0] : request.getRemoteAddr();
    }
}

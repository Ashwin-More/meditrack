package com.meditrack.controller;

import com.meditrack.dto.OtpIdentifierRequest;
import com.meditrack.dto.LoginRequest;
import com.meditrack.dto.ResetPasswordRequest;
import com.meditrack.dto.VerifyOtpRequest;
import com.meditrack.dto.RefreshTokenRequest;
import com.meditrack.dto.RegisterRequest;
import com.meditrack.dto.AuthResponse;
import com.meditrack.dto.MessageResponse;
import com.meditrack.dto.OtpMessageResponse;
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
    public ResponseEntity<OtpMessageResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String rateLimitKey = "register:" + clientIp(httpRequest);
        if (!loginRateLimiter.isAllowed(rateLimitKey)) {
            throw new TooManyRequestsException("Too many registration attempts. Please wait a minute and try again");
        }
        authService.register(request);
        return ResponseEntity.ok(new OtpMessageResponse(
                "Registration successful. We sent a 6-digit verification code to your email. "
                        + "Enter it to verify your account, then wait for an administrator to approve it."));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<MessageResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(new MessageResponse(authService.verifyOtp(request.getIdentifier(), request.getOtp())));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<OtpMessageResponse> resendOtp(@Valid @RequestBody OtpIdentifierRequest request, HttpServletRequest httpRequest) {
        enforceMailRateLimit("resend", request.getIdentifier(), httpRequest);
        authService.resendOtp(request.getIdentifier());
        // Same wording whether or not the account exists / is pending (no account enumeration).
        return ResponseEntity.ok(new OtpMessageResponse("A new OTP has been sent to your email."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<OtpMessageResponse> forgotPassword(@Valid @RequestBody OtpIdentifierRequest request, HttpServletRequest httpRequest) {
        enforceMailRateLimit("forgot", request.getIdentifier(), httpRequest);
        authService.forgotPassword(request.getIdentifier());
        return ResponseEntity.ok(new OtpMessageResponse(
                "If that identifier belongs to an account, a password reset code has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getIdentifier(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Password updated. You can now log in with your new password."));
    }

    /** Passwordless login, step 1: request a one-time code sent to whichever channel the identifier matched. */
    @PostMapping("/login-otp/request")
    public ResponseEntity<OtpMessageResponse> requestLoginOtp(@Valid @RequestBody OtpIdentifierRequest request, HttpServletRequest httpRequest) {
        enforceMailRateLimit("login-otp", request.getIdentifier(), httpRequest);
        authService.requestLoginOtp(request.getIdentifier(), request.getPortal());
        return ResponseEntity.ok(new OtpMessageResponse(
                "If that account is active, a sign-in code has been sent."));
    }

    /** Passwordless login, step 2: verify the code and receive tokens exactly like a normal login. */
    @PostMapping("/login-otp/verify")
    public ResponseEntity<AuthResponse> verifyLoginOtp(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest httpRequest) {
        String rateLimitKey = "login-otp-verify:" + clientIp(httpRequest);
        if (!loginRateLimiter.isAllowed(rateLimitKey)) {
            throw new TooManyRequestsException("Too many attempts. Please wait a minute and try again");
        }
        AuthResponse response = authService.verifyLoginOtp(request.getIdentifier(), request.getOtp(), request.getPortal(), httpRequest);
        loginRateLimiter.reset(rateLimitKey);
        return ResponseEntity.ok(response);
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

    /** Limits OTP-triggering endpoints per IP and per target identifier, so they can't be used to spam someone. */
    private void enforceMailRateLimit(String action, String identifier, HttpServletRequest request) {
        if (!loginRateLimiter.isAllowed(action + ":ip:" + clientIp(request))
                || !loginRateLimiter.isAllowed(action + ":target:" + identifier.trim().toLowerCase())) {
            throw new TooManyRequestsException("Too many requests. Please wait a minute and try again");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0] : request.getRemoteAddr();
    }
}

package com.meditrack.service;

import com.meditrack.dto.LoginRequest;
import com.meditrack.dto.RegisterRequest;
import com.meditrack.dto.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);
    /** Creates the PENDING account and emails the verification OTP (the code itself is never returned). */
    void register(RegisterRequest request);

    /** identifier = username, email, or phone. Confirms the OTP and marks the account's email/phone verified. */
    String verifyOtp(String identifier, String otp);
    void resendOtp(String identifier);

    void forgotPassword(String identifier);
    void resetPassword(String identifier, String otp, String newPassword);

    /** Passwordless login, step 1: send a one-time code to whichever channel the identifier matched. */
    void requestLoginOtp(String identifier, String portal);
    /** Passwordless login, step 2: verify the code and issue tokens exactly like a normal login. */
    AuthResponse verifyLoginOtp(String identifier, String otp, String portal, HttpServletRequest httpRequest);

    AuthResponse refresh(String refreshToken);
    void logout(Integer userId);
}

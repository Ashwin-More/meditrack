package com.meditrack.service;

import com.meditrack.dto.LoginRequest;
import com.meditrack.dto.RegisterRequest;
import com.meditrack.dto.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);
    void register(RegisterRequest request);
    String verifyEmail(String token);
    void resendVerification(String email);
    void forgotPassword(String email);
    void resetPassword(String token, String newPassword);
    AuthResponse refresh(String refreshToken);
    void logout(Integer userId);
}

package com.meditrack.service;

import com.meditrack.entity.User;

public interface EmailService {
    void sendVerificationEmail(User user, String rawToken);
    void sendPasswordResetEmail(User user, String rawToken);
}

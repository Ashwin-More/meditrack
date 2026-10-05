package com.meditrack.service;

import com.meditrack.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends one-time OTP codes by SMS, for phone-number login/verification.
 *
 * app.sms.enabled=false (default): nothing is sent over the wire; the code is
 *   printed in the backend console — same "works with zero external accounts"
 *   idea as app.mail.enabled=false, so the project runs with no SMS provider.
 * app.sms.enabled=true: plug in a real provider (Twilio, MSG91, SNS, etc.) here —
 *   the call site (AuthServiceImpl) never needs to change, only this class.
 */
@Service
@Slf4j
public class SmsServiceImpl implements SmsService {

    @Value("${app.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${app.sms.from:MEDITRACK}")
    private String from;

    @Override
    public boolean sendVerificationOtp(User user, String otp) {
        return deliver(user, "verification", otp);
    }

    @Override
    public boolean sendPasswordResetOtp(User user, String otp) {
        return deliver(user, "password reset", otp);
    }

    @Override
    public boolean sendLoginOtp(User user, String otp) {
        return deliver(user, "sign-in", otp);
    }

    private boolean deliver(User user, String purpose, String otp) {
        String message = "MediTrack " + purpose + " code: " + otp + ". Valid for a few minutes. Do not share this code.";
        // The code is deliberately NOT logged. Returning false makes the caller fall back to email.
        if (!smsEnabled) {
            log.info("SMS {} code not sent to {}: app.sms.enabled=false (falling back to email).", purpose, user.getPhone());
            return false;
        }
        // No SMS provider is wired up in this project by default. Set app.sms.enabled=true
        // and implement the actual provider call (Twilio / MSG91 / AWS SNS / etc.) here,
        // sending `message` and returning true only when the provider accepted it.
        log.warn("app.sms.enabled=true but no SMS provider is implemented - falling back to email for {}", user.getPhone());
        return false;
    }
}

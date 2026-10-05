package com.meditrack.service;

import com.meditrack.entity.User;

public interface SmsService {
    /** @return true only if the code was really handed to a mail/SMS provider. */
    boolean sendVerificationOtp(User user, String otp);
    /** @return true only if the code was really handed to a mail/SMS provider. */
    boolean sendPasswordResetOtp(User user, String otp);
    /** @return true only if the code was really handed to a mail/SMS provider. */
    boolean sendLoginOtp(User user, String otp);
}

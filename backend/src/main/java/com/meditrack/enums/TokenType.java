package com.meditrack.enums;

public enum TokenType {
    EMAIL_VERIFY,
    PASSWORD_RESET,
    /** Passwordless login: a one-time code sent to the user's email or phone. */
    LOGIN_OTP
}

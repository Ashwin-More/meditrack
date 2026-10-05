package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Generic "we sent a code" message. The OTP itself is never included - it only travels by email/SMS. */
@Getter
@AllArgsConstructor
public class OtpMessageResponse {
    private String message;
}

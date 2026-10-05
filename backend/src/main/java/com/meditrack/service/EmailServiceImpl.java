package com.meditrack.service;

import com.meditrack.entity.User;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends one-time OTP codes by email (registration / verification, password
 * reset, passwordless login). Replaces the old clickable-link emails: the
 * user now types the code into MediTrack themselves, which also means the
 * code can be requested for a phone number that has no inbox to click a link in.
 *
 * app.mail.enabled=false (default): nothing is sent; the code is printed in the
 *   backend console so the project works on a laptop with no SMTP account.
 * app.mail.enabled=true: real SMTP send using the spring.mail.* settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@meditrack.local}")
    private String from;

    /** Display name shown as the sender in the recipient's inbox. */
    private static final String FROM_NAME = "MediTrack";

    @Value("${app.tokens.email-verify-minutes:10}")
    private long verifyMinutes;

    @Value("${app.tokens.password-reset-minutes:30}")
    private long resetMinutes;

    @Value("${app.tokens.login-otp-minutes:5}")
    private long loginMinutes;

    /**
     * Google shows App Passwords as "abcd efgh ijkl mnop"; SMTP login only works without the spaces.
     * Strip them here so a pasted password works either way, and log (never the secret itself) what
     * the mail setup looks like so a misconfiguration is obvious in the console.
     */
    @PostConstruct
    void checkMailConfiguration() {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender instanceof JavaMailSenderImpl impl) {
            if (impl.getPassword() != null) impl.setPassword(impl.getPassword().replaceAll("\\s+", ""));
            if (impl.getUsername() != null) impl.setUsername(impl.getUsername().trim());
            if (mailEnabled) {
                boolean missing = impl.getUsername() == null || impl.getUsername().isBlank()
                        || impl.getPassword() == null || impl.getPassword().isBlank();
                if (missing) {
                    log.error("MAIL_ENABLED=true but MAIL_USERNAME / MAIL_PASSWORD are empty - no email can be sent.");
                } else {
                    log.info("Email OTP delivery ON: {}:{} as {} (password length {}), from {}",
                            impl.getHost(), impl.getPort(), impl.getUsername(), impl.getPassword().length(), from);
                }
            }
        }
        if (!mailEnabled) {
            log.warn("Email OTP delivery is OFF (MAIL_ENABLED is not true) - no OTP can be delivered. "
                    + "Set MAIL_ENABLED=true (and MAIL_USERNAME / MAIL_PASSWORD) in the .env file next to docker-compose.yml.");
        }
    }

    @Override
    public boolean sendVerificationOtp(User user, String otp) {
        return deliver(user, "MediTrack – Email Verification Code", "email verification code", otp, verifyMinutes);
    }

    @Override
    public boolean sendPasswordResetOtp(User user, String otp) {
        return deliver(user, "MediTrack – Password Reset Code", "password reset code", otp, resetMinutes);
    }

    @Override
    public boolean sendLoginOtp(User user, String otp) {
        return deliver(user, "MediTrack – Sign-in Code", "sign-in code", otp, loginMinutes);
    }

    private boolean deliver(User user, String subject, String codeLabel, String otp, long validMinutes) {
        // Seeded demo accounts use @meditrack.local addresses that can't receive real mail.
        boolean undeliverableDemoAddress = user.getEmail() == null || user.getEmail().toLowerCase().endsWith(".local");
        if (!mailEnabled || undeliverableDemoAddress) {
            // The code is deliberately NOT logged - it must only ever reach the user by email.
            log.warn("OTP email NOT sent to {} (subject '{}'): mail is disabled (MAIL_ENABLED) or the address is a *.local demo address.",
                    user.getEmail(), subject);
            return false;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.error("app.mail.enabled=true but no JavaMailSender is configured (check spring.mail.*)");
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, FROM_NAME); // visible sender name = "MediTrack"
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(buildText(codeLabel, otp, validMinutes), buildHtml(codeLabel, otp, validMinutes));
            sender.send(message);
            log.info("Sent '{}' email to {}", subject, user.getEmail());
            return true;
        } catch (Exception ex) {
            // Never fail the calling request because SMTP is down; the user can request a new code.
            String hint = String.valueOf(ex.getMessage()).contains("535") || String.valueOf(ex.getMessage()).toLowerCase().contains("authentication")
                    ? " -> Gmail rejected the login. Use a 16-character App Password (Google Account > Security > 2-Step Verification > App passwords) for MAIL_USERNAME."
                    : "";
            log.error("Failed to send '{}' email to {}: {}{}", subject, user.getEmail(), ex.getMessage(), hint);
            return false;
        }
    }

    private static String minutesText(long m) {
        return m + (m == 1 ? " minute" : " minutes");
    }

    private String buildText(String codeLabel, String otp, long validMinutes) {
        return "MediTrack\nHealthcare Management\n\nHello,\n\nYour " + codeLabel + " is:\n\n" + otp
                + "\n\nEnter this code on the MediTrack screen to continue. This OTP is valid for " + minutesText(validMinutes) + ".\n\n"
                + "For your security, do not share this code with anyone.\n\n"
                + "If you did not request this code, please ignore this email.\n\n"
                + "Regards,\nMediTrack Team\nSecure Healthcare Management";
    }

    private String buildHtml(String codeLabel, String otp, long validMinutes) {
        return "<div style=\"background:#F4F6FA;padding:32px;font-family:Segoe UI,Arial,sans-serif;\">"
                + "<div style=\"max-width:480px;margin:auto;background:#FFFFFF;border:1px solid #E6EAF2;border-radius:14px;padding:32px;color:#18213D;\">"
                + "<div style=\"font-size:22px;font-weight:700;\"><span style=\"color:#18213D;\">Medi</span><span style=\"color:#2457D6;\">Track</span></div>"
                + "<div style=\"font-size:13px;color:#667085;margin-bottom:24px;\">Healthcare Management</div>"
                + "<p style=\"margin:0 0 12px 0;\">Hello,</p>"
                + "<p style=\"margin:0 0 12px 0;line-height:1.5;\">Your " + HtmlUtils.htmlEscape(codeLabel) + " is:</p>"
                + "<div style=\"margin:20px 0;text-align:center;background:#F7F9FD;border:1px solid #E6EAF2;border-radius:10px;padding:18px;\">"
                + "<strong style=\"font-size:34px;letter-spacing:10px;color:#2457D6;\">" + HtmlUtils.htmlEscape(otp) + "</strong></div>"
                + "<p style=\"margin:0 0 12px 0;line-height:1.5;\">Enter this code on the MediTrack screen to continue. This OTP is valid for " + minutesText(validMinutes) + ".</p>"
                + "<p style=\"margin:0 0 12px 0;line-height:1.5;\">For your security, do not share this code with anyone.</p>"
                + "<p style=\"margin:0 0 24px 0;line-height:1.5;color:#667085;\">If you did not request this code, please ignore this email.</p>"
                + "<p style=\"margin:0;line-height:1.5;\">Regards,<br/><strong>MediTrack Team</strong><br/>"
                + "<span style=\"color:#667085;\">Secure Healthcare Management</span></p>"
                + "</div></div>";
    }
}

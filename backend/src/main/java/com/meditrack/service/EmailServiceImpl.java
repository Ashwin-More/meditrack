package com.meditrack.service;

import com.meditrack.entity.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends verification / password-reset emails.
 *
 * app.mail.enabled=false (default): nothing is sent; the link is printed in the backend
 *   console so the project works on a laptop with no SMTP account.
 * app.mail.enabled=true: real SMTP send using the spring.mail.* settings (see backend/README.md).
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

    @Value("${app.frontend-url:http://localhost:5500}")
    private String frontendUrl;

    @Override
    public void sendVerificationEmail(User user, String rawToken) {
        String link = frontendUrl + "/#/verify-email?token=" + rawToken;
        deliver(user, "Verify your MediTrack email", "Verify your email",
                "Welcome to MediTrack. Please confirm this email address to activate your account.",
                "Verify email", link, "This link expires in 24 hours.");
    }

    @Override
    public void sendPasswordResetEmail(User user, String rawToken) {
        String link = frontendUrl + "/#/reset-password?token=" + rawToken;
        deliver(user, "Reset your MediTrack password", "Reset your password",
                "We received a request to reset your MediTrack password. If this was not you, ignore this email.",
                "Choose a new password", link, "This link expires in 30 minutes and can be used once.");
    }

    private void deliver(User user, String subject, String heading, String intro,
                         String buttonText, String link, String footer) {
        if (!mailEnabled) {
            log.info("\n========== MAIL (dev mode, app.mail.enabled=false) ==========\n"
                    + "To:      {}\nSubject: {}\nLink:    {}\n"
                    + "==============================================================", user.getEmail(), subject, link);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.error("app.mail.enabled=true but no JavaMailSender is configured (check spring.mail.*)");
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(buildHtml(user.getFirstName(), heading, intro, buttonText, link, footer), true);
            sender.send(message);
            log.info("Sent '{}' email to {}", subject, user.getEmail());
        } catch (Exception ex) {
            // Never fail the calling request because SMTP is down; the user can request a new link.
            log.error("Failed to send '{}' email to {}: {}", subject, user.getEmail(), ex.getMessage());
        }
    }

    private String buildHtml(String firstName, String heading, String intro, String buttonText, String link, String footer) {
        return "<div style=\"background:#0B1120;padding:32px;font-family:Segoe UI,Arial,sans-serif;\">"
                + "<div style=\"max-width:480px;margin:auto;background:#172033;border:1px solid #273449;border-radius:14px;padding:32px;color:#F8FAFC;\">"
                + "<div style=\"font-size:20px;font-weight:700;margin-bottom:18px;\">"
                + "<span style=\"color:#A78BFA;\">Medi</span><span style=\"color:#22D3EE;\">Track</span></div>"
                + "<h2 style=\"margin:0 0 12px 0;\">" + HtmlUtils.htmlEscape(heading) + "</h2>"
                + "<p style=\"color:#A7B0C0;line-height:1.5;\">Hi " + HtmlUtils.htmlEscape(firstName) + ",<br/>" + HtmlUtils.htmlEscape(intro) + "</p>"
                + "<p style=\"margin:26px 0;\"><a href=\"" + link + "\" style=\"background:#7C3AED;color:#fff;text-decoration:none;"
                + "padding:12px 22px;border-radius:8px;font-weight:600;display:inline-block;\">" + HtmlUtils.htmlEscape(buttonText) + "</a></p>"
                + "<p style=\"color:#A7B0C0;font-size:12px;\">" + HtmlUtils.htmlEscape(footer) + "<br/>If the button does not work, copy this link:<br/>"
                + "<span style=\"word-break:break-all;color:#22D3EE;\">" + HtmlUtils.htmlEscape(link) + "</span></p>"
                + "</div></div>";
    }
}

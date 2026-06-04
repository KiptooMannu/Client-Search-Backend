package com.kazikonnect.backend.features.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${app.email.from:${spring.mail.username:}}")
    private String fromEmail;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.email.tmp-dir:/tmp}")
    private String emailTempDir;

    private final JavaMailSender mailSender;

    // =========================
    // PASSWORD RESET EMAIL
    // =========================
    public void sendPasswordResetEmail(String email, String otp) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null");
        }

        String subject = "Kazi Konnect — Password Reset Request";

        String htmlBody = """
                <p>Hi there,</p>
                <p>Please reset your password using the One-Time Password (OTP) below:</p>
                <h2 style="background-color: #f3f4f6; color: #2E3192; padding: 12px 24px; text-align: center; border-radius: 8px; font-size: 24px; letter-spacing: 4px; display: inline-block; min-width: 150px; margin: 10px 0;">%s</h2>
                <p>This code expires in 15 minutes.</p>
                <p>If you didn't request a password reset, you can safely ignore this email.</p>
                <p>Best regards,<br>The Kazi Konnect Team</p>
                """.formatted(otp);

        sendEmail(email, subject, htmlBody);
    }

    // =========================
    // EMAIL VERIFICATION EMAIL
    // =========================
    public void sendEmailVerificationEmail(String email, String fullName, String otp) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null");
        }

        log.info("Sending email verification for {} with OTP {}", email, otp);

        String subject = "Verify Your Email - Kazi Konnect";

        String htmlBody = """
                <p>Hi %s,</p>
                <p>Welcome to Kazi Konnect! 🎉</p>
                <p>Please verify your email address using the One-Time Password (OTP) below:</p>
                <h2 style="background-color: #f3f4f6; color: #2E3192; padding: 12px 24px; text-align: center; border-radius: 8px; font-size: 24px; letter-spacing: 4px; display: inline-block; min-width: 150px; margin: 10px 0;">%s</h2>
                <p>This code expires in 24 hours.</p>
                <p>If you didn't create this account, you can safely ignore this email.</p>
                <p>Best regards,<br>The Kazi Konnect Team</p>
                """.formatted(fullName, otp);

        sendEmail(email, subject, htmlBody);
    }

    // =========================
    // WELCOME EMAIL
    // =========================
    public void sendWelcomeEmail(String email, String fullName) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null");
        }

        String subject = "Welcome to Kazi Konnect";

        String htmlBody = """
                <p>Hi %s,</p>
                <p>Welcome to Kazi Konnect 🎉</p>
                <p>Your account is ready.</p>
                """.formatted(fullName);

        sendEmail(email, subject, htmlBody);
    }

    // =========================
    // CORE EMAIL SENDER
    // =========================
    private void sendEmail(String recipient, String subject, String htmlBody) {

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Recipient is required");
        }

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }

        if (htmlBody == null || htmlBody.isBlank()) {
            throw new IllegalArgumentException("Email body is required");
        }

        // SMTP not configured → fallback
        if (mailUsername.isBlank() || mailPassword.isBlank()) {
            log.warn("SMTP not configured. Writing email to file.");
            writeEmailToFile(recipient, subject, htmlBody, "local");
            return;
        }

        String sender = (fromEmail != null && !fromEmail.isBlank())
                ? fromEmail
                : mailUsername;

        if (sender == null || sender.isBlank()) {
            throw new IllegalStateException("Sender email is not configured");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            // These now produce NO warnings because we validated above
            helper.setFrom(sender);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);

            log.info("Email sent successfully to {}", recipient);

        } catch (MailException | MessagingException e) {
            log.error("SMTP email sending failed", e);
            try {
                writeEmailToFile(recipient, subject, htmlBody, "smtp-failed");
            } catch (Exception writeEx) {
                log.error("Failed to write fallback email file after SMTP failure", writeEx);
            }
        }
    }

    // =========================
    // FALLBACK FILE LOGGING
    // =========================
    private void writeEmailToFile(String recipient,
                                   String subject,
                                   String htmlBody,
                                   String suffix) {
        try {
            String tmpDir = (emailTempDir == null || emailTempDir.isBlank())
                    ? "/tmp"
                    : emailTempDir;
            log.info("Writing fallback email file to {}", tmpDir);
            Path dir = Paths.get(tmpDir, "tmp-emails");
            Files.createDirectories(dir);

            String fileName = System.currentTimeMillis()
                    + "-" + recipient.replaceAll("[^a-zA-Z0-9@._-]", "_")
                    + "-" + suffix + ".html";

            Path file = dir.resolve(fileName);

            String content = """
                    <h3>To: %s</h3>
                    <h4>Subject: %s</h4>
                    %s
                    """.formatted(recipient, subject, htmlBody);

            Files.writeString(file, content, StandardCharsets.UTF_8);

            log.info("Email written to file: {}", file.toAbsolutePath());

        } catch (IOException e) {
            log.error("Failed to write email file", e);
        }
    }
}
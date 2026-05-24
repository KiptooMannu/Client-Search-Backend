package com.kazikonnect.backend.features.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Email service for sending reset links and notifications.
 * Uses Resend API when configured.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${resend.api.key:}")
    private String resendApiKey;

    @Value("${app.email.from:kkgg7241@gmail.com}")
    private String fromEmail;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Send password reset email with token link
     */
    public void sendPasswordResetEmail(String email, String resetToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
        String subject = "Kazi Konnect — Password Reset Request";
        String htmlBody = String.format(
            "<p>Hi there,</p>" +
            "<p>We received a request to reset your password. Click the button below to choose a new password:</p>" +
            "<p><a href=\"%s\" style=\"display:inline-block;padding:12px 24px;background:#4f46e5;color:#ffffff;border-radius:8px;text-decoration:none;\">Reset Password</a></p>" +
            "<p>If the button does not work, copy and paste this link into your browser:</p>" +
            "<p><a href=\"%s\">%s</a></p>" +
            "<p>This link expires in 1 hour. If you did not request a password reset, please ignore this email.</p>" +
            "<p>Thanks,<br/>Kazi Konnect Team</p>",
            resetLink, resetLink, resetLink
        );

        sendEmail(email, subject, htmlBody);
    }

    /**
     * Send welcome email on registration
     */
    public void sendWelcomeEmail(String email, String fullName) {
        String subject = "Welcome to Kazi Konnect";
        String htmlBody = String.format(
            "<p>Hi %s,</p>" +
            "<p>Thank you for joining Kazi Konnect. Your account has been created successfully.</p>" +
            "<p>You can now log in and start using the platform.</p>" +
            "<p>Welcome aboard!<br/>Kazi Konnect Team</p>",
            fullName
        );

        sendEmail(email, subject, htmlBody);
    }

    private void sendEmail(String recipient, String subject, String htmlBody) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.error("Resend API key not configured. Please set RESEND_API_KEY in your environment.");
            throw new RuntimeException("Email provider is not configured. Please contact support.");
        }

        log.info("Sending email through Resend from {} to {}", fromEmail, recipient);

        try {
            String json = String.format(
                "{\"from\":\"%s\",\"to\":\"%s\",\"subject\":\"%s\",\"html\":\"%s\"}",
                escapeJson(fromEmail),
                escapeJson(recipient),
                escapeJson(subject),
                escapeJson(htmlBody)
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + resendApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("Resend response status={}, body={}", response.statusCode(), response.body());
            if (response.statusCode() >= 300) {
                log.error("Resend failed with status {} and body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to send email. Please try again later.");
            }
            log.info("Resend email sent to {} with status {}", recipient, response.statusCode());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to send email through Resend", e);
            throw new RuntimeException("Failed to send email. Please try again later.");
        }
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}

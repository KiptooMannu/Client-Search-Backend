package com.kazikonnect.backend.features.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.ParameterizedTypeReference;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class MpesaService {

    @Value("${mpesa.env:sandbox}")
    private String mpesaEnv;

    @Value("${mpesa.consumer-key:}")
    private String consumerKey;

    @Value("${mpesa.consumer-secret:}")
    private String consumerSecret;

    @Value("${mpesa.shortcode:174379}")
    private String shortcode;

    @Value("${mpesa.passkey:}")
    private String passkey;

    @Value("${mpesa.callback-url:http://localhost:8080/api/payments/mpesa/callback}")
    private String callbackUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public StkPushResponse initiateStkPush(String phoneNumber, double amount, String accountReference, String transactionDesc) {
        if (consumerKey == null || consumerKey.isBlank() || consumerSecret == null || consumerSecret.isBlank()) {
            throw new RuntimeException("M-PESA credentials are not configured.");
        }

        String accessToken = fetchAccessToken();

        String url = getBaseUrl() + "/mpesa/stkpush/v1/processrequest";

        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = Base64.getEncoder().encodeToString((shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));

        Map<String, Object> payload = new HashMap<>();
        payload.put("BusinessShortCode", shortcode);
        payload.put("Password", password);
        payload.put("Timestamp", timestamp);
        payload.put("TransactionType", "CustomerPayBillOnline");
        payload.put("Amount", amount);
        payload.put("PartyA", normalizePhoneNumber(phoneNumber));
        payload.put("PartyB", shortcode);
        payload.put("PhoneNumber", normalizePhoneNumber(phoneNumber));
        payload.put("CallBackURL", callbackUrl);
        payload.put("AccountReference", accountReference);
        payload.put("TransactionDesc", transactionDesc);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new RuntimeException("Empty response received from M-PESA STK push.");
        }

        return new StkPushResponse(
            String.valueOf(body.getOrDefault("ResponseDescription", body.getOrDefault("errorMessage", "Unknown response"))),
            String.valueOf(body.getOrDefault("CheckoutRequestID", "")),
            String.valueOf(body.getOrDefault("CustomerMessage", body.getOrDefault("ResponseDescription", "")))
        );
    }

    public String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            throw new IllegalArgumentException("Phone number is required.");
        }
        String clean = phoneNumber.replaceAll("[^0-9]", "");
        if (clean.startsWith("0") && clean.length() == 10) {
            return "254" + clean.substring(1);
        }
        if (clean.startsWith("7") && clean.length() == 9) {
            return "254" + clean;
        }
        if (clean.startsWith("254") && clean.length() == 12) {
            return clean;
        }
        if (clean.startsWith("+254") && clean.length() == 13) {
            return clean.substring(1);
        }
        return clean;
    }

    @NonNull
    private String fetchAccessToken() {
        String url = getBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials";
        HttpHeaders headers = new HttpHeaders();

        String key = Objects.requireNonNull(consumerKey, "consumerKey must not be null");
        String secret = Objects.requireNonNull(consumerSecret, "consumerSecret must not be null");
        headers.setBasicAuth(key, secret);

        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("access_token")) {
            throw new RuntimeException("Failed to retrieve M-PESA access token.");
        }

        return Objects.requireNonNull(
                String.valueOf(body.get("access_token")),
                "access_token value must not be null"
        );
    }

    private String getBaseUrl() {
        return mpesaEnv.equalsIgnoreCase("production")
            ? "https://api.safaricom.co.ke"
            : "https://sandbox.safaricom.co.ke";
    }
}
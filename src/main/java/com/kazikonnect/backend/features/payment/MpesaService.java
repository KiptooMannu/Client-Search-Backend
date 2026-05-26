package com.kazikonnect.backend.features.payment;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
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
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;

@Service
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

    @Value("${mpesa.passkey}")
    private String passkey;

    @Value("${mpesa.callback-url}")
    private String callbackUrl;

    @Value("${mpesa.callback-allowed-ips:}")
    private String callbackAllowedIps;

    private final Logger LOGGER = LoggerFactory.getLogger(MpesaService.class);
    private final RestTemplate restTemplate;

    public MpesaService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(15_000);
        requestFactory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public StkPushResponse initiateStkPush(String phoneNumber, double amount, String accountReference,
            String transactionDesc) {
        if (consumerKey == null || consumerKey.isBlank() || consumerSecret == null || consumerSecret.isBlank()) {
            throw new RuntimeException("M-PESA credentials are not configured.");
        }

        String accessToken = fetchAccessToken();

        String url = getBaseUrl() + "/mpesa/stkpush/v1/processrequest";

        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = Base64.getEncoder()
                .encodeToString((shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));

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

        LOGGER.info("Sending MPESA STK push request for accountReference={} phoneNumber={} amount={}", accountReference, phoneNumber, amount);
        LOGGER.debug("MPESA STK push payload: {}", payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> response;
        try {
            response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            throw new RuntimeException("M-PESA STK push failed: " + e.getStatusCode() + " " + responseBody, e);
        } catch (RestClientException e) {
            throw new RuntimeException("Network error while sending M-PESA STK push: " + e.getMessage(), e);
        }

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new RuntimeException("Empty response received from M-PESA STK push.");
        }

        LOGGER.info("MPESA STK push response received: {}", body);
        return new StkPushResponse(
                String.valueOf(body.getOrDefault("ResponseDescription",
                        body.getOrDefault("errorMessage", "Unknown response"))),
                String.valueOf(body.getOrDefault("CheckoutRequestID", "")),
                String.valueOf(body.getOrDefault("CustomerMessage", body.getOrDefault("ResponseDescription", ""))));
    }

    @PostConstruct
    private void validateConfiguration() {
        if (consumerKey == null || consumerKey.isBlank()) {
            throw new IllegalStateException("MPESA_CONSUMER_KEY must be configured.");
        }
        if (consumerSecret == null || consumerSecret.isBlank()) {
            throw new IllegalStateException("MPESA_CONSUMER_SECRET must be configured.");
        }
        if (passkey == null || passkey.isBlank()) {
            throw new IllegalStateException("MPESA_PASSKEY must be configured.");
        }
        if (shortcode == null || shortcode.isBlank()) {
            throw new IllegalStateException("MPESA_SHORTCODE must be configured.");
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new IllegalStateException("MPESA_CALLBACK_URL must be configured.");
        }
        if (!callbackUrl.startsWith("https://")) {
            throw new IllegalStateException("MPESA callback URL must use HTTPS.");
        }
        LOGGER.info("MPESA service configured for env={}, shortcode={}, callbackUrl={}", mpesaEnv, shortcode, callbackUrl);
    }

    public boolean isAcceptedCallbackSource(String remoteIp) {
        if (callbackAllowedIps == null || callbackAllowedIps.isBlank()) {
            LOGGER.warn("MPESA callback source validation is disabled because MPESA_CALLBACK_ALLOWED_IPS is not configured.");
            return true;
        }
        List<String> permitted = List.of(callbackAllowedIps.split(","))
                .stream()
                .map(String::trim)
                .filter(ip -> !ip.isBlank())
                .collect(Collectors.toList());
        return permitted.contains(remoteIp);
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

        ResponseEntity<Map<String, Object>> response;
        try {
            response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(null, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            throw new RuntimeException("Failed to retrieve M-PESA access token: " + e.getStatusCode() + " " + responseBody, e);
        } catch (RestClientException e) {
            throw new RuntimeException("Network error while retrieving M-PESA access token: " + e.getMessage(), e);
        }

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("access_token")) {
            throw new RuntimeException("Failed to retrieve M-PESA access token: empty or invalid response.");
        }

        return Objects.requireNonNull(
                String.valueOf(body.get("access_token")),
                "access_token value must not be null");
    }

    private String getBaseUrl() {
        return mpesaEnv.equalsIgnoreCase("production")
                ? "https://api.safaricom.co.ke"
                : "https://sandbox.safaricom.co.ke";
    }
}
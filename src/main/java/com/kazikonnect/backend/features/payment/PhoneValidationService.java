package com.kazikonnect.backend.features.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Phone Validation Service
 * Validates Kenyan phone numbers and checks M-Pesa eligibility
 */
@Service
public class PhoneValidationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhoneValidationService.class);

    // Kenyan phone number patterns
    private static final Pattern KENYAN_MOBILE_PATTERN = Pattern.compile(
        "^(\\+?254|0)?[17][0-9]{8}$"
    );

    // M-Pesa supported operators (Safaricom = 7, Airtel = 6, Kenya Power = 5)
    private static final Pattern SAFARICOM_PATTERN = Pattern.compile("^254[17][0-9]{8}$");
    private static final Pattern AIRTEL_PATTERN = Pattern.compile("^254(6|8)[0-9]{8}$");

    @Value("${validation.allow-all-carriers:false}")
    private boolean allowAllCarriers;

    /**
     * Check if phone number is valid Kenyan number
     */
    public boolean isValidKenyanNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }

        String normalized = normalizePhoneNumber(phoneNumber);
        boolean isValid = KENYAN_MOBILE_PATTERN.matcher(normalized).matches();

        if (!isValid) {
            LOGGER.warn("Invalid Kenyan phone number format: {}", phoneNumber);
        }

        return isValid;
    }

    /**
     * Check if phone is on M-Pesa supported carrier
     * Primary: Safaricom (7x, 1x)
     * Secondary: Airtel (6x, 8x)
     */
    public boolean isMpesaEnabledCarrier(String phoneNumber) {
        if (phoneNumber == null) return false;

        String normalized = normalizePhoneNumber(phoneNumber);

        // Safaricom (primary M-Pesa provider)
        if (SAFARICOM_PATTERN.matcher(normalized).matches()) {
            return true;
        }

        // Airtel Money compatible with some M-Pesa flows
        if (AIRTEL_PATTERN.matcher(normalized).matches() && allowAllCarriers) {
            LOGGER.info("Airtel number allowed (experimental): {}", normalized);
            return true;
        }

        LOGGER.warn("Phone number not on M-Pesa enabled carrier: {}", normalized);
        return false;
    }

    /**
     * Check if phone appears to have M-Pesa active
     * (Note: This is a best-effort check based on carrier and format validation.
     *  Actual M-Pesa activation status would require API call to Safaricom)
     */
    public boolean isMpesaActive(String phoneNumber) {
        if (!isValidKenyanNumber(phoneNumber)) {
            return false;
        }

        if (!isMpesaEnabledCarrier(phoneNumber)) {
            return false;
        }

        // In production, could call M-Pesa API to verify actual M-Pesa account status
        // For now, return true if carrier is enabled
        return true;
    }

    /**
     * Normalize Kenyan phone to international format (254...)
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";

        // Remove all non-digits
        String clean = phoneNumber.replaceAll("[^0-9]", "");

        // Convert various formats to 254 format
        if (clean.startsWith("0") && clean.length() == 10) {
            return "254" + clean.substring(1);
        }
        if (clean.length() == 9 && (clean.startsWith("7") || clean.startsWith("1"))) {
            return "254" + clean;
        }
        if (clean.startsWith("254") && clean.length() == 12) {
            return clean;
        }
        if (clean.startsWith("+254") && clean.length() == 13) {
            return clean.substring(1);
        }

        // Return as-is if already normalized or can't determine
        return clean;
    }

    /**
     * Validate STK push attempts (rate limiting)
     * Prevents spam/abuse of STK push functionality
     */
    public boolean isValidStkPushAttempt(String phoneNumber, int attemptsInLast5Minutes) {
        // Allow max 3 STK push attempts per phone in 5 minutes
        int maxAttemptsInWindow = 3;

        if (attemptsInLast5Minutes >= maxAttemptsInWindow) {
            LOGGER.warn("STK push rate limit exceeded for phone: {}, attempts: {}", 
                phoneNumber, attemptsInLast5Minutes);
            return false;
        }

        return true;
    }

    /**
     * Validate B2C payout phone number
     * More strict validation for worker payouts
     */
    public void validateB2cPhoneNumber(String phoneNumber) throws IllegalArgumentException {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number is required for B2C payout");
        }

        if (!isValidKenyanNumber(phoneNumber)) {
            throw new IllegalArgumentException("Invalid phone number format for B2C payout: " + phoneNumber);
        }

        if (!isMpesaEnabledCarrier(phoneNumber)) {
            throw new IllegalArgumentException("Phone number is not on M-Pesa enabled carrier: " + phoneNumber);
        }
    }

    /**
     * Get operator name from phone number
     */
    public String getOperator(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);

        if (SAFARICOM_PATTERN.matcher(normalized).matches()) {
            return "Safaricom";
        }
        if (AIRTEL_PATTERN.matcher(normalized).matches()) {
            return "Airtel";
        }

        return "Unknown";
    }
}

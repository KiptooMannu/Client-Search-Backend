package com.kazikonnect.backend.features.payment;

/**
 * B2C Payout Response
 * Response from M-Pesa B2C API call
 */
public record B2cPayoutResponse(
    String conversationId,
    String originatorConversationId,
    String responseDescription,
    String responseCode
) {
    public boolean isSuccess() {
        return responseCode != null && responseCode.equals("0");
    }
}

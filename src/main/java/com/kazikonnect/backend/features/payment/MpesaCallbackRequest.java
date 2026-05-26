package com.kazikonnect.backend.features.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MpesaCallbackRequest(
        @JsonProperty("Body") Body body
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record Body(
            @JsonProperty("stkCallback") StkCallback stkCallback
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record StkCallback(
            @JsonProperty("MerchantRequestID") String merchantRequestId,
            @JsonProperty("CheckoutRequestID") String checkoutRequestId,
            @JsonProperty("ResultCode") Integer resultCode,
            @JsonProperty("ResultDesc") String resultDesc,
            @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record CallbackMetadata(
            @JsonProperty("Item") List<Item> items
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record Item(
            @JsonProperty("Name") String name,
            @JsonProperty("Value") Object value
    ) {}
}

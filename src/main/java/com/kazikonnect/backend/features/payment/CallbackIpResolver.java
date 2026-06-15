package com.kazikonnect.backend.features.payment;

/**
 * Resolves the client IP from proxy headers (Render, nginx, etc.).
 */
public final class CallbackIpResolver {

    private CallbackIpResolver() {
    }

    public static String resolve(String xForwardedFor, String remoteAddr) {
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String first = xForwardedFor.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        return remoteAddr != null ? remoteAddr : "";
    }
}

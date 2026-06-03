package com.kazikonnect.backend.features.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserRole {
    WORKER,
    CLIENT,
    ADMIN;

    @JsonCreator
    public static UserRole fromValue(String value) {
        if (value == null) {
            return null;
        }
        return UserRole.valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}

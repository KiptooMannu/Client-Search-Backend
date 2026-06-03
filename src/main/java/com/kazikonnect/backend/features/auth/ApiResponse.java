package com.kazikonnect.backend.features.auth;

public record ApiResponse<T>(boolean success, String message, T data) {}

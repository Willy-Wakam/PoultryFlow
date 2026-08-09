package com.poultryflow.shared.api.error;

/**
 * Client-safe location and reason for one request validation failure.
 */
public record ValidationViolation(String field, String message) {
}

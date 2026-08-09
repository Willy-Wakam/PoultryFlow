package com.poultryflow.shared.api;

/**
 * Stable names shared by PoultryFlow API documentation and future endpoint implementations.
 */
public final class ApiContract {

    public static final String BUSINESS_API_BASE_PATH = "/api/v1";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_KEY_PARAMETER = "IdempotencyKey";
    public static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";
    public static final String VALIDATION_VIOLATION_SCHEMA = "ValidationViolation";
    public static final String PROBLEM_RESPONSE = "ProblemResponse";
    public static final String VALIDATION_FAILED_CODE = "VALIDATION_FAILED";
    public static final String HTTP_ERROR_CODE = "HTTP_ERROR";

    public static final String IDEMPOTENCY_KEY_PARAMETER_REF =
            "#/components/parameters/" + IDEMPOTENCY_KEY_PARAMETER;
    public static final String PROBLEM_DETAIL_SCHEMA_REF =
            "#/components/schemas/" + PROBLEM_DETAIL_SCHEMA;
    public static final String PROBLEM_RESPONSE_REF =
            "#/components/responses/" + PROBLEM_RESPONSE;

    private ApiContract() {
    }
}

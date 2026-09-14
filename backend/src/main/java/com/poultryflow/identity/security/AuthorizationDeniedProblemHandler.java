package com.poultryflow.identity.security;

import com.poultryflow.shared.api.ApiContract;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class AuthorizationDeniedProblemHandler implements AccessDeniedHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AuthorizationDeniedProblemHandler.class);
    private static final URI PROBLEM_TYPE =
            URI.create("urn:poultryflow:problem:authorization-denied");
    private static final int MAX_LOG_VALUE_LENGTH = 160;

    private final ObjectMapper objectMapper;

    public AuthorizationDeniedProblemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        Principal principal = request.getUserPrincipal();
        LOGGER.warn(
                "event=authorization_denied principal={} method={} path={}",
                safeLogValue(principal == null ? "unknown" : principal.getName()),
                safeLogValue(request.getMethod()),
                safeLogValue(request.getRequestURI()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have permission to perform this PoultryFlow operation.");
        problem.setType(PROBLEM_TYPE);
        problem.setTitle("Authorization denied");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ApiContract.AUTHORIZATION_DENIED_CODE);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private String safeLogValue(String value) {
        String sanitized = value
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_');
        return sanitized.length() <= MAX_LOG_VALUE_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_LOG_VALUE_LENGTH);
    }
}

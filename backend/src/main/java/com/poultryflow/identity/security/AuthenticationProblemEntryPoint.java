package com.poultryflow.identity.security;

import com.poultryflow.shared.api.ApiContract;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class AuthenticationProblemEntryPoint implements AuthenticationEntryPoint {

    private static final URI PROBLEM_TYPE =
            URI.create("urn:poultryflow:problem:authentication-required");

    private final ObjectMapper objectMapper;

    public AuthenticationProblemEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException)
            throws IOException, ServletException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "A valid PoultryFlow access token is required.");
        problem.setType(PROBLEM_TYPE);
        problem.setTitle("Authentication required");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ApiContract.AUTHENTICATION_REQUIRED_CODE);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

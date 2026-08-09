package com.poultryflow.shared.api.error;

import com.poultryflow.shared.api.ApiContract;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final URI VALIDATION_PROBLEM_TYPE =
            URI.create("urn:poultryflow:problem:validation-failed");

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request fields are invalid.");
        problem.setType(VALIDATION_PROBLEM_TYPE);
        problem.setTitle("Validation failed");
        problem.setInstance(requestUri(request));
        problem.setProperty("code", ApiContract.VALIDATION_FAILED_CODE);
        problem.setProperty("violations", violations(exception));

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        return handleExceptionInternal(
                exception,
                problem,
                responseHeaders,
                HttpStatus.BAD_REQUEST,
                request);
    }

    private List<ValidationViolation> violations(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getAllErrors().stream()
                .map(this::toViolation)
                .sorted(Comparator.comparing(ValidationViolation::field)
                        .thenComparing(ValidationViolation::message))
                .toList();
    }

    private ValidationViolation toViolation(ObjectError error) {
        String field = error instanceof FieldError fieldError
                ? fieldError.getField()
                : error.getObjectName();
        String message = Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value");
        return new ValidationViolation(field, message);
    }

    private URI requestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletRequest servletRequest = servletWebRequest.getRequest();
            return URI.create(servletRequest.getRequestURI());
        }
        return URI.create("/");
    }
}

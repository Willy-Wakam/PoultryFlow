package com.poultryflow.farm.api;

import com.poultryflow.farm.AmbiguousFarmProfileException;
import com.poultryflow.farm.FarmProfileNotConfiguredException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FarmProfileController.class)
public class FarmProfileExceptionHandler {

    @ExceptionHandler(FarmProfileNotConfiguredException.class)
    ResponseEntity<ProblemDetail> profileNotConfigured(HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Farm profile not configured",
                "The farm profile has not been configured.",
                "FARM_PROFILE_NOT_CONFIGURED",
                "farm-profile-not-configured",
                request);
    }

    @ExceptionHandler(AmbiguousFarmProfileException.class)
    ResponseEntity<ProblemDetail> ambiguousProfile(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Current farm is ambiguous",
                "The current farm cannot be resolved safely.",
                "FARM_PROFILE_AMBIGUOUS",
                "farm-profile-ambiguous",
                request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> concurrentModification(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "The farm profile changed while this request was being processed. Refresh and retry.",
                "CONCURRENT_MODIFICATION",
                "concurrent-modification",
                request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            String code,
            String type,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:poultryflow:problem:" + type));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}

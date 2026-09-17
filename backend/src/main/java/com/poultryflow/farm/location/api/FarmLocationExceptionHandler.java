package com.poultryflow.farm.location.api;

import com.poultryflow.farm.AmbiguousFarmProfileException;
import com.poultryflow.farm.FarmProfileNotConfiguredException;
import com.poultryflow.farm.location.ConcurrentFarmLocationModificationException;
import com.poultryflow.farm.location.DuplicateFarmLocationNameException;
import com.poultryflow.farm.location.FarmLocationNotFoundException;
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

@RestControllerAdvice(assignableTypes = FarmLocationController.class)
public class FarmLocationExceptionHandler {

    @ExceptionHandler(FarmProfileNotConfiguredException.class)
    ResponseEntity<ProblemDetail> farmNotConfigured(HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Farm profile not configured",
                "The farm profile must be configured before locations can be managed.",
                "FARM_PROFILE_NOT_CONFIGURED",
                "farm-profile-not-configured",
                request);
    }

    @ExceptionHandler(FarmLocationNotFoundException.class)
    ResponseEntity<ProblemDetail> locationNotFound(HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Farm location not found",
                "The requested farm location was not found.",
                "FARM_LOCATION_NOT_FOUND",
                "farm-location-not-found",
                request);
    }

    @ExceptionHandler(DuplicateFarmLocationNameException.class)
    ResponseEntity<ProblemDetail> duplicateName(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Farm location name already exists",
                "A farm location with that name already exists.",
                "FARM_LOCATION_NAME_ALREADY_EXISTS",
                "farm-location-name-already-exists",
                request);
    }

    @ExceptionHandler(AmbiguousFarmProfileException.class)
    ResponseEntity<ProblemDetail> ambiguousFarm(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Current farm is ambiguous",
                "The current farm cannot be resolved safely.",
                "FARM_PROFILE_AMBIGUOUS",
                "farm-profile-ambiguous",
                request);
    }

    @ExceptionHandler({
        ConcurrentFarmLocationModificationException.class,
        OptimisticLockingFailureException.class,
        OptimisticLockException.class
    })
    ResponseEntity<ProblemDetail> concurrentModification(HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "The farm location changed while this request was being processed. Refresh and retry.",
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
